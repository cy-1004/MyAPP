package com.myapp.feature.knowledge.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.knowledge.data.KnowledgeRepository
import com.myapp.feature.knowledge.data.KnowledgeSearchHit
import com.myapp.feature.knowledge.data.KnowledgeSourceUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 列表行：知识源本身 + 是否已在整体顺序首/末位（决定上移/下移按钮是否可点）。 */
data class KnowledgeSourceRow(
    val source: KnowledgeSourceUi,
    val isFirst: Boolean,
    val isLast: Boolean,
)

data class KnowledgeGroup(val name: String, val rows: List<KnowledgeSourceRow>)

data class KnowledgeListState(
    val groups: List<KnowledgeGroup> = emptyList(),
    val loaded: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<KnowledgeSearchHit> = emptyList(),
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()
}

/** 一次性事件：删除后弹可撤销提示（与分类管理同一套约定）。 */
data class KnowledgeUndoDeleteEvent(val id: Long, val title: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnowledgeListViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val state: StateFlow<KnowledgeListState> = combine(repository.observeAll(), searchQuery) { sources, query ->
        sources to query
    }.flatMapLatest { (sources, query) ->
        val groups = buildGroups(sources)
        if (query.isBlank()) {
            flowOf(KnowledgeListState(groups = groups, loaded = true, searchQuery = query))
        } else {
            searchResultsFlow(groups, query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KnowledgeListState(),
    )

    private val _undoEvents = Channel<KnowledgeUndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<KnowledgeUndoDeleteEvent> = _undoEvents.receiveAsFlow()

    fun setQuery(query: String) {
        searchQuery.value = query
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    fun setInPool(id: Long, inPool: Boolean) {
        viewModelScope.launch { repository.setInPool(id, inPool) }
    }

    fun delete(row: KnowledgeSourceRow) {
        viewModelScope.launch {
            repository.delete(row.source.id)
            _undoEvents.send(KnowledgeUndoDeleteEvent(row.source.id, row.source.title))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    /** [delta] = -1 上移 / +1 下移。边界处 Repository 会直接忽略。 */
    fun move(id: Long, delta: Int) {
        viewModelScope.launch { repository.move(id, delta) }
    }

    private fun buildGroups(sources: List<KnowledgeSourceUi>): List<KnowledgeGroup> {
        val rows = sources.mapIndexed { index, source ->
            KnowledgeSourceRow(source = source, isFirst = index == 0, isLast = index == sources.lastIndex)
        }
        return rows.groupBy { it.source.groupName.ifBlank { UNGROUPED_LABEL } }
            .map { (name, groupRows) -> KnowledgeGroup(name, groupRows) }
    }

    /** flow{} 包一层是因为 `repository.search` 是 suspend 函数，不是 Flow。 */
    private fun searchResultsFlow(groups: List<KnowledgeGroup>, query: String) =
        flow {
            emit(
                KnowledgeListState(
                    groups = groups,
                    loaded = true,
                    searchQuery = query,
                    searchResults = repository.search(query),
                ),
            )
        }

    companion object {
        const val UNGROUPED_LABEL = "未分组"
    }
}
