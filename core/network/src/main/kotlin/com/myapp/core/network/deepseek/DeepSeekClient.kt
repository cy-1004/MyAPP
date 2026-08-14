package com.myapp.core.network.deepseek

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.security.SecretStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** 调用失败的分类（PRD 3.14 要求错误文案能区分几种原因，而不是笼统的「失败了」）。 */
enum class DeepSeekFailure(val message: String) {
    /** 设置页还没填 key。 */
    NO_KEY("还没填 DeepSeek API Key，去「设置 → AI 分析」填一个"),

    NO_NETWORK("连不上网络，检查一下网络连接再试"),

    /** key 不对或已作废。**注意文案里不能带 key 本身**。 */
    UNAUTHORIZED("API Key 鉴权失败，可能是填错了或已在控制台作废"),

    RATE_LIMITED("请求太频繁或余额不足，等一会儿再试"),

    BAD_RESPONSE("模型返回异常，稍后再试一次"),
}

/** 成功时带回正文；[searched] 表示这次是否真的触发了联网搜索。 */
sealed interface DeepSeekResult {
    data class Success(val text: String, val searched: Boolean) : DeepSeekResult
    data class Failure(val reason: DeepSeekFailure) : DeepSeekResult
}

/**
 * DeepSeek 调用入口（PRD 3.14）。
 *
 * 只负责「把 prompt 发出去、把文字拿回来」，不含任何经期业务语义——
 * 业务编排在 `:feature:period`。key 从 [SecretStore] 现取现用，不缓存成字段：
 * 用户在设置页清空 key 之后，下一次调用就该立刻失效。
 *
 * **不重试**：用户正盯着屏幕等，静默重试只会把 30s 变成 60s（PRD 明确要求）。
 */
@Singleton
class DeepSeekClient @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json,
    private val secrets: SecretStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * 单独派生一个客户端：联网搜索会让响应显著变慢，共享的那个 30s readTimeout
     * 是按 RSS 调的，这里需要的是**整次调用** 30s 上限（callTimeout 覆盖连接+读+写，
     * readTimeout 只管两个数据包之间的间隔，流式慢吐时根本不会触发）。
     * `newBuilder()` 复用连接池与线程池，不是新开一个 OkHttp。
     */
    private val client = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * 是否已经配置了 key。做成 Flow 是因为它会在**另一个页面**（设置页）被改变，
     * 一次性快照会让「填完 key 返回」的界面停在「还没填」上。
     */
    val hasApiKey: Flow<Boolean> = secrets.revision.map {
        !secrets[SecretStore.KEY_DEEPSEEK_API_KEY].isNullOrBlank()
    }

    suspend fun complete(
        instructions: String,
        input: String,
        webSearch: Boolean,
    ): DeepSeekResult = withContext(io) {
        val apiKey = secrets[SecretStore.KEY_DEEPSEEK_API_KEY]?.takeIf { it.isNotBlank() }
            ?: return@withContext DeepSeekResult.Failure(DeepSeekFailure.NO_KEY)

        val payload = ResponsesRequest(
            model = MODEL,
            instructions = instructions,
            input = input,
            tools = if (webSearch) listOf(ToolSpec(WEB_SEARCH_TOOL)) else emptyList(),
            maxOutputTokens = MAX_OUTPUT_TOKENS,
        )
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // 不把 body 原样带进 UI：它可能回显请求内容，而请求里有用户的私密记录
                    return@withContext DeepSeekResult.Failure(
                        when (response.code) {
                            401, 403 -> DeepSeekFailure.UNAUTHORIZED
                            402, 429 -> DeepSeekFailure.RATE_LIMITED
                            else -> DeepSeekFailure.BAD_RESPONSE
                        },
                    )
                }
                parse(body)
            }
        } catch (_: IOException) {
            // OkHttp 把超时也归到 IOException。对用户来说「没连上」和「太慢了」
            // 是同一件事：现在用不了，等会儿再点
            DeepSeekResult.Failure(DeepSeekFailure.NO_NETWORK)
        } catch (_: Exception) {
            DeepSeekResult.Failure(DeepSeekFailure.BAD_RESPONSE)
        }
    }

    private fun parse(body: String): DeepSeekResult {
        val reply = try {
            json.decodeFromString<ResponsesReply>(body)
        } catch (_: Exception) {
            return DeepSeekResult.Failure(DeepSeekFailure.BAD_RESPONSE)
        }
        if (reply.error != null) return DeepSeekResult.Failure(DeepSeekFailure.BAD_RESPONSE)

        // output 是个混合数组：联网搜索的调用记录、推理过程、最终消息都在里面。
        // 只取 output_text，其余（含搜索关键词）不展示也不落盘
        val text = reply.output
            .flatMap { it.content }
            .filter { it.type == OUTPUT_TEXT }
            .joinToString("\n") { it.text }
            .trim()
        if (text.isEmpty()) return DeepSeekResult.Failure(DeepSeekFailure.BAD_RESPONSE)

        val searched = reply.output.any { it.type.startsWith(WEB_SEARCH_TOOL) }
        return DeepSeekResult.Success(text, searched)
    }

    companion object {
        const val ENDPOINT = "https://api.deepseek.com/responses"

        /**
         * 模型 id（2026-08-14 由用户核对官方文档确认）。
         * 官方把权重升级到 V4-Flash-0731 时调用方式不变，这个字符串不用跟着改。
         */
        const val MODEL = "deepseek-v4-flash"

        /** 服务端内置工具。不带日期后缀，跟随官方的默认版本。 */
        const val WEB_SEARCH_TOOL = "web_search"

        private const val OUTPUT_TEXT = "output_text"
        private const val CALL_TIMEOUT_SECONDS = 30L

        /** 一段解读用不了太多字，给上限是防止模型跑飞把费用拉高。 */
        private const val MAX_OUTPUT_TOKENS = 1200

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
