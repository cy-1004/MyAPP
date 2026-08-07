package com.myapp.feature.question.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.question.data.Question
import com.myapp.feature.question.data.QuestionRepository
import com.myapp.feature.question.data.QuestionStatus
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 一次性事件：删除后弹出可撤销的提示。 */
data class UndoDeleteEvent(val id: Long, val title: String)

/** 列表 UI 状态：按状态分三组，待解决优先。 */
data class QuestionListUiState(
    val open: List<Question> = emptyList(),
    val resolved: List<Question> = emptyList(),
    val archived: List<Question> = emptyList(),
) {
    val isEmpty: Boolean get() = open.isEmpty() && resolved.isEmpty() && archived.isEmpty()
}

@HiltViewModel
class QuestionListViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tag = MutableStateFlow<String?>(null)
    val tag: StateFlow<String?> = _tag.asStateFlow()

    private val _undoEvents = Channel<UndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<UndoDeleteEvent> = _undoEvents.receiveAsFlow()

    /**
     * 列表数据流：搜索 + 标签筛选合并后切换 DAO 查询，再在内存按 status 分组。
     *
     * Repository 一次查全部非删除，ViewModel 按 status 切三组：
     *   - OPEN 按 updated_at DESC（最近改过的在前）
     *   - RESOLVED 按 resolved_at DESC（最近解决的在前）
     *   - ARCHIVED 按 updated_at DESC
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<Result<QuestionListUiState>> = combine(_query, _tag) { q, t -> q to t }
        .flatMapLatest { (q, t) ->
            repository.observe(q.takeIf { it.isNotBlank() }, t).map(::group)
        }
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

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

    fun delete(question: Question) {
        viewModelScope.launch {
            repository.delete(question.id)
            _undoEvents.send(UndoDeleteEvent(id = question.id, title = firstLineTitle(question)))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    private fun group(questions: List<Question>): QuestionListUiState {
        val open = questions.filter { it.status == QuestionStatus.OPEN }
            .sortedByDescending { it.updatedAt }
        val resolved = questions.filter { it.status == QuestionStatus.RESOLVED }
            .sortedByDescending { it.resolvedAt ?: it.updatedAt }
        val archived = questions.filter { it.status == QuestionStatus.ARCHIVED }
            .sortedByDescending { it.updatedAt }
        return QuestionListUiState(open = open, resolved = resolved, archived = archived)
    }

    private fun firstLineTitle(question: Question): String =
        question.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "疑问"
}
