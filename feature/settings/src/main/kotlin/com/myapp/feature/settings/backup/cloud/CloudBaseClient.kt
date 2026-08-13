package com.myapp.feature.settings.backup.cloud

import com.myapp.core.common.di.IoDispatcher
import com.myapp.feature.settings.backup.SecretStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** 云备份相关的可读错误。[needsReauth] 为 true 时 UI 应退回未登录态。 */
class CloudBackupException(
    message: String,
    val needsReauth: Boolean = false,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * 腾讯云开发 HTTP 客户端（PRD 3.13）。
 *
 * **鉴权策略**：access_token 只有 24 小时有效期，而备份是每天一次的无人值守任务，
 * 每次醒来 token 基本都过期了。CloudBase 的 refresh 机制未在文档中明确，
 * 这里采用更简单也更可靠的做法——**用本机 Keystore 里存的账号密码重新登录**。
 * 反正密码本来就要存（否则无人值守任务没法自愈），多存一个 refresh_token 并无收益。
 *
 * 只有在密码本身失效（改过密码）时才抛 [CloudBackupException] 且 `needsReauth = true`，
 * UI 据此退回未登录态——不能静默失败让用户以为还在正常备份。
 */
@Singleton
class CloudBaseClient @Inject constructor(
    baseClient: OkHttpClient,
    private val json: Json,
    private val secrets: SecretStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * 备份 payload 是整库密文，可能有几 MB。
     * :core:network 的共享 OkHttpClient 没设 writeTimeout（默认 10 秒），
     * 弱网下上传必超时，所以这里派生一个放宽超时的实例（连接池仍然共用）。
     */
    private val client: OkHttpClient = baseClient.newBuilder()
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val tokenMutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAtMillis: Long = 0

    /** 用账号密码换 token 并存下凭证。设置页「登录」调用。 */
    suspend fun signIn(username: String, password: String): Unit = withContext(io) {
        val token = requestToken(username, password)
        secrets[SecretStore.KEY_USERNAME] = username
        secrets[SecretStore.KEY_PASSWORD] = password
        tokenMutex.withLock {
            cachedToken = token.accessToken
            tokenExpiresAtMillis = nowMillis() + token.expiresIn * 1000
        }
    }

    fun signOut() {
        secrets.clear()
        cachedToken = null
        tokenExpiresAtMillis = 0
    }

    val isSignedIn: Boolean
        get() = secrets[SecretStore.KEY_USERNAME] != null && secrets[SecretStore.KEY_PASSWORD] != null

    /** 上传一份备份，返回云端生成的行 id。 */
    suspend fun upload(record: BackupInsert): Long = withContext(io) {
        val body = json.encodeToString(BackupInsert.serializer(), record)
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(CloudBaseApi.BASE_URL + CloudBaseApi.BACKUP_TABLE_PATH)
            .addHeader("Authorization", "Bearer ${validToken()}")
            .addHeader("Content-Type", "application/json")
            // 让 PostgREST 回写入的行，才能拿到自增 id
            .addHeader("Prefer", "return=representation")
            .post(body)
            .build()
        val rows = client.newCall(request).execute().use { response ->
            decodeOrThrow(response, "上传备份失败") {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(BackupRecord.serializer()), it)
            }
        }
        rows.firstOrNull()?.id ?: 0L
    }

    /** 备份历史（不含 payload），按时间倒序。 */
    suspend fun listBackups(limit: Int = 20): List<BackupRecord> = withContext(io) {
        val url = CloudBaseApi.BASE_URL + CloudBaseApi.BACKUP_TABLE_PATH +
            "?select=${CloudBaseApi.METADATA_COLUMNS}&order=created_at.desc&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${validToken()}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            decodeOrThrow(response, "读取备份列表失败") {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(BackupRecord.serializer()), it)
            }
        }
    }

    /** 取某一条备份的完整内容（含 payload）。 */
    suspend fun download(id: Long): BackupRecord = withContext(io) {
        val url = CloudBaseApi.BASE_URL + CloudBaseApi.BACKUP_TABLE_PATH + "?select=*&id=eq.$id"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${validToken()}")
            .get()
            .build()
        val rows = client.newCall(request).execute().use { response ->
            decodeOrThrow(response, "下载备份失败") {
                json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(BackupRecord.serializer()), it)
            }
        }
        rows.firstOrNull() ?: throw CloudBackupException("云端找不到这份备份（可能已被删除）")
    }

    suspend fun delete(id: Long): Unit = withContext(io) {
        val request = Request.Builder()
            .url(CloudBaseApi.BASE_URL + CloudBaseApi.BACKUP_TABLE_PATH + "?id=eq.$id")
            .addHeader("Authorization", "Bearer ${validToken()}")
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw toException(response, "删除备份失败")
        }
    }

    // ---------- 内部 ----------

    /** 拿一个没过期的 token；过期或没有就用存下的账号密码重登。 */
    private suspend fun validToken(): String = tokenMutex.withLock {
        // 提前 60 秒判过期，避免请求正好卡在失效边界上
        val current = cachedToken
        if (current != null && nowMillis() < tokenExpiresAtMillis - 60_000) return@withLock current

        val username = secrets[SecretStore.KEY_USERNAME]
        val password = secrets[SecretStore.KEY_PASSWORD]
        if (username == null || password == null) {
            throw CloudBackupException("尚未登录云备份账号", needsReauth = true)
        }
        val token = requestToken(username, password)
        cachedToken = token.accessToken
        tokenExpiresAtMillis = nowMillis() + token.expiresIn * 1000
        token.accessToken
    }

    private fun requestToken(username: String, password: String): AuthToken {
        val body = json.encodeToString(SignInRequest.serializer(), SignInRequest(username, password))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(CloudBaseApi.BASE_URL + CloudBaseApi.SIGN_IN_PATH)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    json.decodeFromString(CloudBaseError.serializer(), text)
                }.getOrNull()
                val readable = when {
                    detail?.code == "INVALID_USERNAME_OR_PASSWORD" ->
                        "账号或密码不正确（注意：手机号不能用于登录，请用昵称或邮箱）"
                    detail?.message?.isNotBlank() == true -> detail.message
                    else -> "登录失败（HTTP ${response.code}）"
                }
                throw CloudBackupException(readable, needsReauth = true)
            }
            json.decodeFromString(AuthToken.serializer(), text)
        }
    }

    private fun <T> decodeOrThrow(response: Response, what: String, decode: (String) -> T): T {
        val text = response.body?.string().orEmpty()
        if (!response.isSuccessful) throw toException(response, what, text)
        return try {
            decode(text)
        } catch (e: Exception) {
            throw CloudBackupException("$what：返回内容无法解析", cause = e)
        }
    }

    private fun toException(
        response: Response,
        what: String,
        body: String = response.body?.string().orEmpty(),
    ): CloudBackupException {
        val detail = runCatching { json.decodeFromString(CloudBaseError.serializer(), body) }.getOrNull()
        // PGRST205 = schema 缓存里找不到表，几乎总是「建表 SQL 还没在控制台跑过」
        if (detail?.code == "DATABASE_PGRST205" || response.code == 404) {
            return CloudBackupException(
                "云端还没有 app_backup 表，请先在云开发控制台执行 docs/云数据备份.md 里的建表 SQL",
            )
        }
        if (response.code == 401 || response.code == 403) {
            return CloudBackupException("云备份登录已失效，请重新登录", needsReauth = true)
        }
        val readable = detail?.message?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
        return CloudBackupException("$what：$readable")
    }

    private fun nowMillis() = System.currentTimeMillis()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
