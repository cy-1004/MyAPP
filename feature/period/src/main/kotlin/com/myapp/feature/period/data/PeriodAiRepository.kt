package com.myapp.feature.period.data

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.datastore.AppPreferences
import com.myapp.core.network.deepseek.DeepSeekClient
import com.myapp.core.network.deepseek.DeepSeekResult
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** 已缓存的上一次分析。[updatedAt] 为 0 表示从没分析过。 */
data class PeriodAiCache(
    val text: String,
    val updatedAt: Long,
)

sealed interface PeriodAiOutcome {
    /** [fromCache] 为真表示数据没变，直接给的旧结论，没有真的发请求。 */
    data class Success(val text: String, val updatedAt: Long, val fromCache: Boolean) : PeriodAiOutcome
    data class Failure(val message: String) : PeriodAiOutcome
}

/**
 * 经期 AI 分析的业务编排（PRD 3.14）。
 *
 * 三条铁律都落在这里：
 *  1. **没开启就不发**——总开关兼知情同意，false 时连 prompt 都不组。
 *  2. **数据没变就不发**——指纹命中直接给缓存。
 *  3. **只缓存结果**——请求正文与原始响应都不落盘，DataStore 里只有最终那段文字。
 *
 * 峰谷时段的拦截**不在这里**：那需要用户二次确认，属于 UI 决策。
 * 仓库层只认「叫我发我就发」，否则会出现「ViewModel 以为发了、仓库偷偷没发」这种最难查的状态。
 */
@Singleton
class PeriodAiRepository @Inject constructor(
    private val client: DeepSeekClient,
    private val preferences: AppPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    val enabled: Flow<Boolean> = preferences.aiEnabled

    /** 缓存结果。峰价被挡时也要能看到它（PRD 明确要求），所以它独立于任何可用性判断。 */
    val cache: Flow<PeriodAiCache> =
        combine(preferences.periodAiResult, preferences.periodAiUpdatedAt) { text, at ->
            PeriodAiCache(text, at)
        }

    /** 设置页填没填 key。没填时经期页直接把入口指向设置页，而不是让用户点了才报错。 */
    val hasApiKey: Flow<Boolean> = client.hasApiKey

    /**
     * 跑一次分析。[force] 为真时忽略指纹强制重发——数据几十天不变时，
     * 用户仍然有权要一份新的解读（比如联网搜索的结果会随时间变化）。
     */
    suspend fun analyze(
        state: PeriodState,
        dayLogs: Map<LocalDate, PeriodDayLog>,
        today: LocalDate,
        force: Boolean,
    ): PeriodAiOutcome = withContext(io) {
        if (!preferences.aiEnabled.first()) {
            return@withContext PeriodAiOutcome.Failure("AI 分析还没开启，去「设置 → AI 分析」开启后再试")
        }
        if (state.records.isEmpty()) {
            return@withContext PeriodAiOutcome.Failure("还没有经期记录，先记一次开始再来分析")
        }

        val fingerprint = PeriodAiPrompt.fingerprint(state, dayLogs)
        if (!force) {
            val cachedText = preferences.periodAiResult.first()
            if (cachedText.isNotBlank() && preferences.periodAiFingerprint.first() == fingerprint) {
                return@withContext PeriodAiOutcome.Success(
                    text = cachedText,
                    updatedAt = preferences.periodAiUpdatedAt.first(),
                    fromCache = true,
                )
            }
        }

        val result = client.complete(
            instructions = PeriodAiPrompt.INSTRUCTIONS,
            input = PeriodAiPrompt.buildInput(state, dayLogs, today),
            webSearch = preferences.aiWebSearchEnabled.first(),
        )
        when (result) {
            is DeepSeekResult.Failure -> PeriodAiOutcome.Failure(result.reason.message)
            is DeepSeekResult.Success -> {
                val now = AppTime.now()
                preferences.savePeriodAiResult(result.text, fingerprint, now)
                PeriodAiOutcome.Success(result.text, now, fromCache = false)
            }
        }
    }
}
