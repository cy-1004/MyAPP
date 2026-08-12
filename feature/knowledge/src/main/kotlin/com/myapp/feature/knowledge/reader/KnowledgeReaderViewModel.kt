package com.myapp.feature.knowledge.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.data.KnowledgeContentUi
import com.myapp.feature.knowledge.data.KnowledgeRepository
import com.myapp.feature.knowledge.data.KnowledgeSourceUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class KnowledgeReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KnowledgeRepository,
) : ViewModel() {

    private val sourceId: Long = savedStateHandle.toRoute<Route.KnowledgeReader>().sourceId

    val source: StateFlow<KnowledgeSourceUi?> = repository.observeById(sourceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 断网/加载失败时的降级正文。 */
    val content: StateFlow<KnowledgeContentUi?> = repository.observeContent(sourceId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 排一次提取任务；WebView 本身的重新加载由 Screen 直接调用 `WebView.reload()`。 */
    fun refreshContent() {
        repository.refreshOne(sourceId)
    }

    fun togglePinned() {
        val current = source.value ?: return
        viewModelScope.launch { repository.setPinned(sourceId, !current.pinned) }
    }
}
