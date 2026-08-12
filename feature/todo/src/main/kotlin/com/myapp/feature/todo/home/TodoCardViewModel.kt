package com.myapp.feature.todo.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.todo.data.Todo
import com.myapp.feature.todo.data.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TodoCardViewModel @Inject constructor(
    private val repository: TodoRepository,
) : ViewModel() {

    val state: StateFlow<Result<List<Todo>>> = repository.observeToday()
        .asResult()
        .stateIn(
            scope = viewModelScope,
            // 5 秒超时：切后台短暂返回时不重订阅数据库，避免闪烁
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

    /**
     * 用户点了哪条待办 -- 非 null 时 UI 弹 [CompleteTodoDialog]。
     *
     * 用 StateFlow 而不是 Channel：对话框是持久 UI 状态，配置变更（旋转屏幕）后
     * 应该恢复显示，而不是丢掉。确认或取消后清空。
     */
    private val _pendingTodo = MutableStateFlow<Todo?>(null)
    val pendingTodo: StateFlow<Todo?> = _pendingTodo.asStateFlow()

    /**
     * 完成后的撤销事件 -- 走 Channel 而不是 StateFlow。
     * 用 StateFlow 承载「弹一次 Snackbar」这类一次性事件，旋转屏幕或返回页面时会重放，
     * 出现莫名其妙的二次弹窗（与 [com.myapp.feature.todo.list.TodoListViewModel.undoEvents] 同套路）。
     */
    private val _undoEvents = Channel<UndoCompleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoCompleteEvent> = _undoEvents.receiveAsFlow()

    fun requestComplete(todo: Todo) {
        _pendingTodo.value = todo
    }

    fun cancelCompleteDialog() {
        _pendingTodo.value = null
    }

    fun confirmComplete(note: String) {
        val todo = _pendingTodo.value ?: return
        _pendingTodo.value = null
        viewModelScope.launch {
            repository.complete(todo.id, note)
            _undoEvents.send(UndoCompleteEvent(id = todo.id, title = todo.title))
        }
    }

    fun undoComplete(id: Long) {
        viewModelScope.launch { repository.setDone(id, done = false) }
    }
}

data class UndoCompleteEvent(val id: Long, val title: String)
