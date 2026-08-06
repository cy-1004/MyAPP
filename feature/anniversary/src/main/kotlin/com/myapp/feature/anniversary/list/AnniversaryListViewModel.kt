package com.myapp.feature.anniversary.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.anniversary.data.Anniversary
import com.myapp.feature.anniversary.data.AnniversaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次性事件：删除后弹可撤销提示。与待办同一套约定。 */
data class UndoDeleteEvent(val id: Long, val title: String)

@HiltViewModel
class AnniversaryListViewModel @Inject constructor(
    private val repository: AnniversaryRepository,
) : ViewModel() {

    val items: StateFlow<Result<List<Anniversary>>> = repository.observeAll()
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

    // 一次性事件走 Channel：StateFlow 会在返回本页时重放，导致二次弹窗
    private val _undoEvents = Channel<UndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoDeleteEvent> = _undoEvents.receiveAsFlow()

    fun delete(item: Anniversary) {
        viewModelScope.launch {
            repository.delete(item.id)
            _undoEvents.send(UndoDeleteEvent(id = item.id, title = item.title))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    fun pin(item: Anniversary) {
        viewModelScope.launch { repository.setPinned(item.id) }
    }
}
