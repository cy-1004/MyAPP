package com.myapp.feature.period.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.core.common.time.AppTime
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
) : ViewModel() {

    val state: StateFlow<Result<PeriodState>> = repository.observeState()
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
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
}
