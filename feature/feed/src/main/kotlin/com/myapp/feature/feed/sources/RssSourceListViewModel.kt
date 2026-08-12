package com.myapp.feature.feed.sources

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.feed.data.RssRepository
import com.myapp.feature.feed.data.RssSourceUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 列表行：订阅源本身 + 是否已在整体顺序首/末位，同 KnowledgeSourceRow 约定。 */
data class RssSourceRow(val source: RssSourceUi, val isFirst: Boolean, val isLast: Boolean)

data class RssSourceGroup(val name: String, val rows: List<RssSourceRow>)

data class RssSourceListState(val groups: List<RssSourceGroup> = emptyList(), val loaded: Boolean = false)

data class RssUndoDeleteEvent(val id: Long, val title: String)

/** OPML 导入结果通知（PRD 3.9），Snackbar 展示用。 */
data class RssImportEvent(val added: Int, val skipped: Int)

@HiltViewModel
class RssSourceListViewModel @Inject constructor(
    private val repository: RssRepository,
) : ViewModel() {

    val state: StateFlow<RssSourceListState> = repository.observeSources()
        .map { sources -> RssSourceListState(groups = buildGroups(sources), loaded = true) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RssSourceListState(),
        )

    private val _undoEvents = Channel<RssUndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<RssUndoDeleteEvent> = _undoEvents.receiveAsFlow()

    private val _importEvents = Channel<RssImportEvent>(Channel.BUFFERED)
    val importEvents: Flow<RssImportEvent> = _importEvents.receiveAsFlow()

    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun delete(row: RssSourceRow) {
        viewModelScope.launch {
            repository.delete(row.source.id)
            _undoEvents.send(RssUndoDeleteEvent(row.source.id, row.source.title))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    /** [delta] = -1 上移 / +1 下移。边界处 Repository 会直接忽略。 */
    fun move(id: Long, delta: Int) {
        viewModelScope.launch { repository.move(id, delta) }
    }

    /** 导出 OPML 文本，写文件（拿到 Uri 后 `contentResolver.openOutputStream`）交给调用方。 */
    suspend fun exportOpml(): String = repository.exportOpml()

    fun importOpml(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importOpml(uri)
            _importEvents.send(RssImportEvent(result.added, result.skipped))
        }
    }

    private fun buildGroups(sources: List<RssSourceUi>): List<RssSourceGroup> {
        val rows = sources.mapIndexed { index, source ->
            RssSourceRow(source = source, isFirst = index == 0, isLast = index == sources.lastIndex)
        }
        return rows.groupBy { it.source.groupName.ifBlank { UNGROUPED_LABEL } }
            .map { (name, groupRows) -> RssSourceGroup(name, groupRows) }
    }

    companion object {
        const val UNGROUPED_LABEL = "未分组"
    }
}
