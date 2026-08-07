package com.myapp.feature.question.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.question.data.QuestionDraft
import com.myapp.feature.question.data.QuestionRepository
import com.myapp.feature.question.data.QuestionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 编辑完成后要做什么--由页面决定（保存后返回、删除后返回）。 */
enum class QuestionEditResult { Saved, Deleted }

/** 「转为笔记」事件：携带新笔记 id，页面据此弹 Snackbar + 「查看」按钮。 */
data class ConvertToNoteResult(val noteId: Long)

@HiltViewModel
class QuestionEditViewModel @Inject constructor(
    private val repository: QuestionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** id 为 0 表示新建。 */
    private val questionId: Long = savedStateHandle.toRoute<Route.QuestionDetail>().id

    private val _draft = MutableStateFlow(QuestionDraft(id = questionId))
    val draft: StateFlow<QuestionDraft> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(questionId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _results = Channel<QuestionEditResult>(Channel.BUFFERED)
    val results: Flow<QuestionEditResult> = _results.receiveAsFlow()

    private val _convertEvents = Channel<ConvertToNoteResult>(Channel.BUFFERED)
    val convertEvents: Flow<ConvertToNoteResult> = _convertEvents.receiveAsFlow()

    init {
        if (questionId != 0L) {
            viewModelScope.launch {
                val loaded = repository.loadDraft(questionId)
                if (loaded != null) {
                    _draft.value = loaded
                }
                _loaded.value = true
            }
        }
    }

    fun updateContent(value: String) = update { it.copy(content = value) }

    fun updateContext(value: String) = update { it.copy(context = value) }

    fun updateAnswer(value: String) = update { it.copy(answer = value) }

    fun updateStatus(value: QuestionStatus) = update { it.copy(status = value) }

    fun addTag(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        if (_draft.value.tags.any { it.equals(trimmed, ignoreCase = true) }) return
        update { it.copy(tags = it.tags + trimmed) }
    }

    fun removeTag(value: String) = update { it.copy(tags = it.tags - value) }

    fun save() {
        val current = _draft.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(current)
            _results.send(QuestionEditResult.Saved)
        }
    }

    fun delete() {
        val id = _draft.value.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _results.send(QuestionEditResult.Deleted)
        }
    }

    /**
     * 把已解决疑问转为笔记（PRD 3.5）。仅 [QuestionStatus.RESOLVED] 状态可调用。
     * 成功后 emit [ConvertToNoteResult]--Question 状态保持 RESOLVED，不自动归档。
     */
    fun convertToNote() {
        val id = _draft.value.id
        if (id == 0L) return
        if (_draft.value.status != QuestionStatus.RESOLVED) return
        viewModelScope.launch {
            val noteId = repository.convertToNote(id) ?: return@launch
            _convertEvents.send(ConvertToNoteResult(noteId))
        }
    }

    private fun update(block: (QuestionDraft) -> QuestionDraft) {
        _draft.value = block(_draft.value)
    }
}
