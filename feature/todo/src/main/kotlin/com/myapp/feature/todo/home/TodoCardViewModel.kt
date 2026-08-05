package com.myapp.feature.todo.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.todo.data.Todo
import com.myapp.feature.todo.data.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun toggle(todo: Todo) {
        viewModelScope.launch {
            repository.setDone(todo.id, !todo.done)
        }
    }
}
