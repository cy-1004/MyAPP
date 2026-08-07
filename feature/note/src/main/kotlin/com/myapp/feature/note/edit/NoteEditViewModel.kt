package com.myapp.feature.note.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.note.data.NoteDraft
import com.myapp.feature.note.data.NoteRepository
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
enum class NoteEditResult { Saved, Deleted }

@HiltViewModel
class NoteEditViewModel @Inject constructor(
    private val repository: NoteRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** id 为 0 表示新建。 */
    private val noteId: Long = savedStateHandle.toRoute<Route.NoteDetail>().id

    private val _draft = MutableStateFlow(NoteDraft(id = noteId))
    val draft: StateFlow<NoteDraft> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(noteId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _results = Channel<NoteEditResult>(Channel.BUFFERED)
    val results: Flow<NoteEditResult> = _results.receiveAsFlow()

    /** 图片导入失败的提示事件。 */
    private val _imageErrors = Channel<Int>(Channel.BUFFERED)
    val imageErrors: Flow<Int> = _imageErrors.receiveAsFlow()

    init {
        if (noteId != 0L) {
            viewModelScope.launch {
                _draft.value = repository.loadDraft(noteId)
                _loaded.value = true
            }
        }
    }

    fun updateContent(value: String) = update { it.copy(content = value) }

    fun addTag(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        // 重复标签不重复添加
        if (_draft.value.tags.any { it.equals(trimmed, ignoreCase = true) }) return
        update { it.copy(tags = it.tags + trimmed) }
    }

    fun removeTag(value: String) = update { it.copy(tags = it.tags - value) }

    fun removeImage(relativePath: String) = update {
        it.copy(images = it.images - relativePath)
    }

    fun togglePinned() = update { it.copy(pinned = !it.pinned) }

    /**
     * Photo Picker 回调：立刻把 Uri 列表传进 IO 协程复制到 filesDir/notes/<uuid>/。
     *
     * Uri 只在 Activity 生命周期内有效，必须立刻处理，不能存到 StateFlow 等 UI 重组--
     * 重组发生时 Uri 可能已经失效，openInputStream 抛 SecurityException。
     */
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val uuid = _draft.value.uuid
        viewModelScope.launch {
            val imported = repository.importImages(uuid, uris)
            val failed = uris.size - imported.size
            if (imported.isNotEmpty()) {
                update { it.copy(images = it.images + imported) }
            }
            if (failed > 0) {
                _imageErrors.send(failed)
            }
        }
    }

    fun save() {
        val current = _draft.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(current)
            _results.send(NoteEditResult.Saved)
        }
    }

    fun delete() {
        val id = _draft.value.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _results.send(NoteEditResult.Deleted)
        }
    }

    private fun update(block: (NoteDraft) -> NoteDraft) {
        _draft.value = block(_draft.value)
    }
}
