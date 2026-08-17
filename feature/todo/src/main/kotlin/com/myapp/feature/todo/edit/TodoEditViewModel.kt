package com.myapp.feature.todo.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.common.contract.ReminderScheduler
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.todo.data.TodoDraft
import com.myapp.feature.todo.data.TodoRepository
import com.myapp.feature.todo.data.todoReminderKey
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 编辑完成后要做什么——由页面决定（保存后返回、删除后返回）。 */
enum class EditResult { Saved, Deleted }

@HiltViewModel
class TodoEditViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val reminderScheduler: ReminderScheduler,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** id 为 0 表示新建——用同一个页面承担新建与编辑，避免两套几乎一样的表单。 */
    private val todoId: Long = savedStateHandle.toRoute<Route.TodoDetail>().id

    private val _draft = MutableStateFlow(TodoDraft(id = todoId))
    val draft: StateFlow<TodoDraft> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(todoId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _results = Channel<EditResult>(Channel.BUFFERED)
    val results: Flow<EditResult> = _results.receiveAsFlow()

    init {
        if (todoId != 0L) {
            viewModelScope.launch {
                _draft.value = repository.loadDraft(todoId)
                _loaded.value = true
            }
        }
    }

    fun updateTitle(value: String) = update { it.copy(title = value) }
    fun updateNote(value: String) = update { it.copy(note = value) }
    fun updateDueAt(value: Long?) = update { it.copy(dueAt = value) }
    fun updatePriority(value: Int) = update { it.copy(priority = value) }
    fun updateRepeatRule(value: String) = update { it.copy(repeatRule = value) }

    fun save() {
        val current = _draft.value
        if (!current.canSave) return
        viewModelScope.launch {
            val id = repository.save(current)
            val dueAt = current.dueAt
            val key = todoReminderKey(id)
            if (dueAt != null) {
                reminderScheduler.schedule(key, dueAt, title = current.title, body = "待办到期", snoozable = true)
            } else {
                // 编辑时可能被改成「无截止时间」，把之前注册的闹钟一并取消
                reminderScheduler.cancel(key)
            }
            _results.send(EditResult.Saved)
        }
    }

    fun delete() {
        val id = _draft.value.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _results.send(EditResult.Deleted)
        }
    }

    private fun update(block: (TodoDraft) -> TodoDraft) {
        _draft.value = block(_draft.value)
    }
}
