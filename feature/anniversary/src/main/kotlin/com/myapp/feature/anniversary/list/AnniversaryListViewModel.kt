package com.myapp.feature.anniversary.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.core.common.time.AppTime
import com.myapp.core.datastore.AppPreferences
import com.myapp.feature.anniversary.data.Anniversary
import com.myapp.feature.anniversary.data.AnniversaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次性事件：删除后弹可撤销提示。与待办同一套约定。 */
data class UndoDeleteEvent(val id: Long, val title: String)

@HiltViewModel
class AnniversaryListViewModel @Inject constructor(
    private val repository: AnniversaryRepository,
    private val preferences: AppPreferences,
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

    /**
     * 撒花触发器（PRD 6.1：纪念日当天）。同一天只撒一次--
     * 去重记号存在 DataStore（[AppPreferences.anniversaryConfettiLastDate]），
     * 不能只用内存状态，否则切后台重进程重建 ViewModel 后当天会再撒一次。
     */
    private val _confettiTrigger = MutableStateFlow<Long?>(null)
    val confettiTrigger: StateFlow<Long?> = _confettiTrigger.asStateFlow()

    init {
        viewModelScope.launch {
            items.collect { result ->
                if (result !is Result.Success || result.data.none { it.isToday }) return@collect
                val today = AppTime.today().toString()
                if (preferences.anniversaryConfettiLastDate.first() != today) {
                    preferences.setAnniversaryConfettiLastDate(today)
                    _confettiTrigger.value = System.currentTimeMillis()
                }
            }
        }
    }

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
