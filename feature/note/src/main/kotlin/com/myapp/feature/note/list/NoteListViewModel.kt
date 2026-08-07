package com.myapp.feature.note.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.note.data.Note
import com.myapp.feature.note.data.NoteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次性事件：删除后弹出可撤销的提示。 */
data class UndoDeleteEvent(val id: Long, val title: String)

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val repository: NoteRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tag = MutableStateFlow<String?>(null)
    val tag: StateFlow<String?> = _tag.asStateFlow()

    private val _undoEvents = Channel<UndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoDeleteEvent> = _undoEvents.receiveAsFlow()

    /**
     * 列表数据流：搜索 + 标签筛选合并后切换 DAO 查询。
     *
     * `flatMapLatest`：切换筛选条件时立刻取消上一个数据库订阅，
     * 不会两个查询的结果打架（与 TodoListViewModel 同一模式）。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<Result<List<Note>>> = combine(_query, _tag) { q, t -> q to t }
        .flatMapLatest { (q, t) -> repository.observe(q.takeIf { it.isNotBlank() }, t) }
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

    /** 用户已用过的全部标签，给筛选 chip 用。 */
    val tags: StateFlow<List<String>> = repository.observeAllTags()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun updateQuery(value: String) {
        _query.value = value
    }

    fun selectTag(value: String?) {
        _tag.value = value
    }

    fun delete(note: Note) {
        viewModelScope.launch {
            repository.delete(note.id)
            _undoEvents.send(UndoDeleteEvent(id = note.id, title = firstLineTitle(note)))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    private fun firstLineTitle(note: Note): String =
        com.myapp.feature.note.ui.firstLine(note.content)
}
