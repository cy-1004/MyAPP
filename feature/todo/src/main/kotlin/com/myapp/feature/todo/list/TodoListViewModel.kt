package com.myapp.feature.todo.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.todo.data.Todo
import com.myapp.feature.todo.data.TodoFilter
import com.myapp.feature.todo.data.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次性事件：删除后弹出可撤销的提示。 */
data class UndoDeleteEvent(val id: Long, val title: String)

@HiltViewModel
class TodoListViewModel @Inject constructor(
    private val repository: TodoRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(TodoFilter.TODAY)
    val filter: StateFlow<TodoFilter> = _filter.asStateFlow()

    /**
     * 一次性事件走 Channel 而不是 StateFlow：
     * 用 StateFlow 承载「弹一次 Snackbar」这类事件，旋转屏幕或返回页面时会重放，
     * 出现莫名其妙的二次弹窗。
     */
    private val _undoEvents = Channel<UndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoDeleteEvent> = _undoEvents.receiveAsFlow()

    /**
     * 撒花触发器（PRD 6.1：待办全部完成时）。值本身没有意义，每次变化就是「再撒一次」--
     * 详见 [com.myapp.core.designsystem.effect.ConfettiOverlay] 的 trigger 参数说明。
     * 只在「勾完这一下让今天的未完成数变成 0」时触发，不在打开页面时发现已经是 0 就触发--
     * 后者应该是「路过一个已经空的列表」，不是「刚刚清空」，撒花庆祝的是后者。
     */
    private val _confettiTrigger = MutableStateFlow<Long?>(null)
    val confettiTrigger: StateFlow<Long?> = _confettiTrigger.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<Result<List<Todo>>> = _filter
        // flatMapLatest：切换筛选时立刻取消上一个数据库订阅，不会两个视图的结果打架
        .flatMapLatest { repository.observe(it) }
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

    fun selectFilter(filter: TodoFilter) {
        _filter.value = filter
    }

    fun toggle(todo: Todo) {
        viewModelScope.launch {
            val turningDone = !todo.done
            repository.setDone(todo.id, turningDone)
            if (turningDone && repository.countTodayUndone() == 0) {
                _confettiTrigger.value = System.currentTimeMillis()
            }
        }
    }

    fun delete(todo: Todo) {
        viewModelScope.launch {
            repository.delete(todo.id)
            _undoEvents.send(UndoDeleteEvent(id = todo.id, title = todo.title))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }
}
