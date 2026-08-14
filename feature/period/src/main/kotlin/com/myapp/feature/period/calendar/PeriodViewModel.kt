package com.myapp.feature.period.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.core.common.time.AppTime
import com.myapp.feature.period.data.PeriodAiOutcome
import com.myapp.feature.period.data.PeriodAiRepository
import com.myapp.feature.period.data.PeriodDayLog
import com.myapp.feature.period.data.PeriodDayLogRepository
import com.myapp.feature.period.data.PeriodRepository
import com.myapp.feature.period.data.PeriodState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 删除后的可撤销提示。 */
data class UndoDeleteEvent(val id: Long, val startDate: LocalDate)

/**
 * AI 分析区的界面状态（PRD 3.14）。
 *
 * [text] 与 [running] 是并存的：重新分析时旧结果继续留在屏幕上，
 * 不要在等待的十几秒里把用户上次看的内容清空。
 */
data class PeriodAiUiState(
    val enabled: Boolean = false,
    val hasApiKey: Boolean = false,
    val running: Boolean = false,
    val text: String = "",
    val updatedAt: Long = 0L,
    val fromCache: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PeriodViewModel @Inject constructor(
    private val repository: PeriodRepository,
    private val dayLogRepository: PeriodDayLogRepository,
    private val aiRepository: PeriodAiRepository,
) : ViewModel() {

    val state: StateFlow<Result<PeriodState>> = repository.observeState()
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

    /**
     * 每日记录，按日期索引。
     *
     * 与 [state] 分成两条流而不是 combine：日记录变化（记一条今天的情况）不该让整页
     * 连带周期统计一起重算，两者的更新频率也完全不同。
     */
    val dayLogs: StateFlow<Map<LocalDate, PeriodDayLog>> = dayLogRepository.observeByDate()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    private val _undoEvents = Channel<UndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoDeleteEvent> = _undoEvents.receiveAsFlow()

    // ---- AI 分析（PRD 3.14）----

    private val _ai = MutableStateFlow(PeriodAiUiState())
    val ai: StateFlow<PeriodAiUiState> = _ai.asStateFlow()

    init {
        // 开关与缓存都是持久化的，进页面就该反映出来——「峰价时段也要能看到上次结果」
        // 这条要求意味着结果的展示不能依赖任何一次成功的调用
        viewModelScope.launch {
            combine(
                aiRepository.enabled,
                aiRepository.hasApiKey,
                aiRepository.cache,
            ) { enabled, hasKey, cache ->
                Triple(enabled, hasKey, cache)
            }.collect { (enabled, hasKey, cache) ->
                _ai.update {
                    it.copy(
                        enabled = enabled,
                        hasApiKey = hasKey,
                        text = cache.text,
                        updatedAt = cache.updatedAt,
                    )
                }
            }
        }
    }

    /**
     * 发起分析。[force] 由 UI 在两种情况下传真：峰价时段用户点了「仍然分析」，
     * 或者用户在数据没变的情况下主动要一份新的。
     */
    fun analyze(force: Boolean) {
        val current = state.value
        if (current !is Result.Success) return
        if (_ai.value.running) return

        viewModelScope.launch {
            _ai.update { it.copy(running = true, error = null, fromCache = false) }
            val outcome = aiRepository.analyze(
                state = current.data,
                dayLogs = dayLogs.value,
                today = AppTime.today(),
                force = force,
            )
            _ai.update {
                when (outcome) {
                    is PeriodAiOutcome.Success -> it.copy(
                        running = false,
                        text = outcome.text,
                        updatedAt = outcome.updatedAt,
                        fromCache = outcome.fromCache,
                        error = null,
                    )
                    is PeriodAiOutcome.Failure -> it.copy(running = false, error = outcome.message)
                }
            }
        }
    }

    fun dismissAiError() {
        _ai.update { it.copy(error = null) }
    }

    fun recordStart(date: LocalDate = AppTime.today()) {
        viewModelScope.launch { repository.recordStart(date) }
    }

    fun recordEnd(date: LocalDate = AppTime.today()) {
        viewModelScope.launch { repository.recordEnd(date) }
    }

    fun delete(id: Long, startDate: LocalDate) {
        viewModelScope.launch {
            repository.delete(id)
            _undoEvents.send(UndoDeleteEvent(id = id, startDate = startDate))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    /** 保存某一天的身体情况；标签与文本都空时等价于删除这一天（见 repository）。 */
    fun saveDayLog(log: PeriodDayLog) {
        viewModelScope.launch { dayLogRepository.save(log) }
    }

    fun deleteDayLog(date: LocalDate) {
        viewModelScope.launch { dayLogRepository.delete(date) }
    }
}
