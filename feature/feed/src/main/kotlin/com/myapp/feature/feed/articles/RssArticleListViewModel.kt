package com.myapp.feature.feed.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.feed.data.RssArticleUi
import com.myapp.feature.feed.data.RssFilter
import com.myapp.feature.feed.data.RssRepository
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RssArticleListState(
    val filter: RssFilter = RssFilter.All,
    val articles: List<RssArticleUi> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
)

/** 一次性事件：「已存为笔记」提示。 */
data class RssSavedAsNoteEvent(val title: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RssArticleListViewModel @Inject constructor(
    private val repository: RssRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<RssFilter>(RssFilter.All)
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<RssArticleListState> = combine(
        filter.flatMapLatest { repository.observeArticles(it) },
        filter,
        refreshing,
    ) { articles, currentFilter, isRefreshing ->
        RssArticleListState(filter = currentFilter, articles = articles, loaded = true, refreshing = isRefreshing)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RssArticleListState(),
    )

    private val _savedAsNoteEvents = Channel<RssSavedAsNoteEvent>(Channel.BUFFERED)
    val savedAsNoteEvents: Flow<RssSavedAsNoteEvent> = _savedAsNoteEvents.receiveAsFlow()

    private var hasRefreshedOnce = false

    /** 进入页面时刷新一次（PRD 3.9「后台定时」的裁剪替代——见 RssRepository 顶部说明），只触发一次。 */
    fun refreshOnFirstOpen() {
        if (hasRefreshedOnce) return
        hasRefreshedOnce = true
        refresh()
    }

    fun refresh() {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            repository.refreshAll()
            refreshing.value = false
        }
    }

    fun setFilter(value: RssFilter) {
        filter.value = value
    }

    fun setRead(id: Long, isRead: Boolean) {
        viewModelScope.launch { repository.setRead(id, isRead) }
    }

    fun setFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, isFavorite) }
    }

    fun saveAsNote(article: RssArticleUi) {
        viewModelScope.launch {
            if (repository.saveAsNote(article.id) != null) {
                _savedAsNoteEvents.send(RssSavedAsNoteEvent(article.title))
            }
        }
    }
}
