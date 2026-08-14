package com.myapp.feature.period.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.core.common.time.AppTime
import com.myapp.feature.period.data.PeriodDayLog
import com.myapp.feature.period.data.PeriodDayLogRepository
import com.myapp.feature.period.data.PeriodRepository
import com.myapp.feature.period.data.PeriodState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 删除后的可撤销提示。 */
data class UndoDeleteEvent(val id: Long, val startDate: LocalDate)

@HiltViewModel
class PeriodViewModel @Inject constructor(
    private val repository: PeriodRepository,
    private val dayLogRepository: PeriodDayLogRepository,
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
