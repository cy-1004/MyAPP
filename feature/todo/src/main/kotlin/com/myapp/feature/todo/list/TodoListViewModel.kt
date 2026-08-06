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
        viewModelScope.launch { repository.setDone(todo.id, !todo.done) }
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
