package com.myapp.feature.settings.backup

import android.content.Context
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.security.SecretStore
import com.myapp.core.database.DATABASE_SCHEMA_VERSION
import com.myapp.core.database.backup.BackupDataSource
import com.myapp.core.datastore.AppPreferences
import com.myapp.feature.settings.backup.cloud.BackupInsert
import com.myapp.feature.settings.backup.cloud.BackupRecord
import com.myapp.feature.settings.backup.cloud.CloudBackupException
import com.myapp.feature.settings.backup.cloud.CloudBaseClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 云备份的业务编排（PRD 3.13）：导出 → 压缩加密 → 上传，以及反向的恢复。
 *
 * 这一层不碰 UI，也不认识 WorkManager——手动「立即备份」和每日后台任务
 * 调的是同一个 [backupNow]，只是触发源不同。
 */
@Singleton
class CloudBackupRepository @Inject constructor(
    private val dataSource: BackupDataSource,
    private val codec: BackupCodec,
    private val client: CloudBaseClient,
    private val secrets: SecretStore,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    val isSignedIn: Boolean get() = client.isSignedIn

    val hasPassphrase: Boolean get() = secrets[SecretStore.KEY_PASSPHRASE] != null

    suspend fun signIn(username: String, password: String) = client.signIn(username, password)

    suspend fun signOut() {
        client.signOut()
        preferences.setCloudBackupEnabled(false)
        preferences.setCloudBackupLastError("")
    }

    fun setPassphrase(passphrase: String) {
        secrets[SecretStore.KEY_PASSPHRASE] = passphrase
    }

    /**
     * 执行一次完整备份。
     *
     * 失败时把原因写进偏好设置再抛出——后台任务失败没人看着，
     * 不落盘的话用户永远不知道备份早就断了。
     */
    suspend fun backupNow(): BackupSummary = withContext(io) {
        try {
            val passphrase = secrets[SecretStore.KEY_PASSPHRASE]
                ?: throw CloudBackupException("还没有设置备份密码")

            val snapshot = dataSource.export(now = System.currentTimeMillis())
            // 空库保护：一份 0 行的快照几乎肯定是异常状态（刚恢复失败/数据被清），
            // 传上去会把有效的历史备份挤到列表后面，甚至被保留策略清掉
            if (snapshot.rowCount == 0) {
                throw CloudBackupException("本机没有任何数据，已跳过这次备份")
            }

            val encoded = codec.encode(snapshot, passphrase)
            client.upload(
                BackupInsert(
                    appVersion = appVersionName(),
                    dbSchemaVersion = DATABASE_SCHEMA_VERSION,
                    sizeBytes = encoded.sizeBytes,
                    checksum = encoded.checksum,
                    payload = encoded.payloadBase64,
                ),
            )

            val at = System.currentTimeMillis()
            preferences.setCloudBackupLastSuccessAt(at)
            preferences.setCloudBackupLastError("")
            pruneOldBackups()
            BackupSummary(at = at, rowCount = snapshot.rowCount, sizeBytes = encoded.sizeBytes)
        } catch (e: Exception) {
            preferences.setCloudBackupLastError(e.message ?: "备份失败")
            throw e
        }
    }

    suspend fun listBackups(): List<BackupRecord> = client.listBackups()

    /** 下载指定备份并**整库覆盖**本机数据。 */
    suspend fun restore(id: Long): Int = withContext(io) {
        val passphrase = secrets[SecretStore.KEY_PASSPHRASE]
            ?: throw CloudBackupException("还没有设置备份密码，无法解密备份")
        val record = client.download(id)
        val payload = record.payload
            ?: throw CloudBackupException("这份备份没有内容")
        val snapshot = codec.decode(payload, passphrase, expectedChecksum = record.checksum)
        dataSource.restore(snapshot)
        snapshot.rowCount
    }

    suspend fun delete(id: Long) = client.delete(id)

    /**
     * 只保留最近 [KEEP_BACKUPS] 份。
     *
     * 每天一份、每份都是全量，不清理的话云端会无限增长。
     * 清理失败不影响本次备份结果，所以整体吞掉异常——备份已经成功了，
     * 因为删旧的失败而报错会让用户以为备份没成。
     */
    private suspend fun pruneOldBackups() {
        runCatching {
            client.listBackups(limit = KEEP_BACKUPS + PRUNE_BATCH)
                .drop(KEEP_BACKUPS)
                .forEach { client.delete(it.id) }
        }
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    private companion object {
        /** 保留份数。每天一份 = 大约两周的回退空间，够用且不至于占太多。 */
        const val KEEP_BACKUPS = 14

        /** 一次最多多查这么多条来找待删的，避免列表接口拉太多行。 */
        const val PRUNE_BATCH = 20
    }
}

/** 一次成功备份的结果，用于 UI 反馈。 */
data class BackupSummary(
    val at: Long,
    val rowCount: Int,
    val sizeBytes: Long,
)
