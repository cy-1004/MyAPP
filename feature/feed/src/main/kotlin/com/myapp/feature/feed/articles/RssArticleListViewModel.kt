package com.myapp.feature.feed.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.database.dao.DEFAULT_PAGE_SIZE
import com.myapp.feature.feed.data.RssArticleListItem
import com.myapp.feature.feed.data.RssFilter
import com.myapp.feature.feed.data.RssRepository
import com.myapp.feature.feed.data.RssSourceUi
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
    val articles: List<RssArticleListItem> = emptyList(),
    /** 筛选器要用的订阅源清单（PRD 3.9「按订阅源」筛选）。 */
    val sources: List<RssSourceUi> = emptyList(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    /** 当前筛选下的总条数，用来判断还能不能继续往下加载。 */
    val totalCount: Int = 0,
) {
    val canLoadMore: Boolean get() = articles.size < totalCount
}

/** 一次性事件：「已存为笔记」提示。 */
data class RssSavedAsNoteEvent(val title: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RssArticleListViewModel @Inject constructor(
    private val repository: RssRepository,
) : ViewModel() {

    private val filter = MutableStateFlow<RssFilter>(RssFilter.All)
    private val refreshing = MutableStateFlow(false)

    /**
     * 当前要显示多少条。
     *
     * 不引入 Paging 3：本页只是「往下滚就多给一屏」，Paging 的
     * PagingSource/RemoteMediator 那套在这里是纯粹的额外复杂度
     * （PRD 8 把 Paging 列在 P3 打磨期，不是现在要解决的问题）。
     * Room 的 Flow 在数据变化时会重查，limit 变大即触发一次新查询，天然够用。
     */
    private val limit = MutableStateFlow(DEFAULT_PAGE_SIZE)

    val state: StateFlow<RssArticleListState> = combine(
        combine(filter, limit) { f, l -> f to l }
            .flatMapLatest { (f, l) -> repository.observeArticles(f, l) },
        filter.flatMapLatest { repository.observeArticleCount(it) },
        repository.observeSources(),
        filter,
        refreshing,
    ) { articles, total, sources, currentFilter, isRefreshing ->
        RssArticleListState(
            filter = currentFilter,
            articles = articles,
            sources = sources,
            loaded = true,
            refreshing = isRefreshing,
            totalCount = total,
        )
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

    /** 滚到底部时再放一屏。已经全部加载完就不再增长，避免 limit 无意义地涨下去。 */
    fun loadMore() {
        if (!state.value.canLoadMore) return
        limit.value += DEFAULT_PAGE_SIZE
    }

    /** 换筛选条件时把 limit 收回首屏——否则从「全部」切到某个源还会按上千条去查。 */
    fun setFilter(value: RssFilter) {
        if (filter.value == value) return
        filter.value = value
        limit.value = DEFAULT_PAGE_SIZE
    }

    fun setRead(id: Long, isRead: Boolean) {
        viewModelScope.launch { repository.setRead(id, isRead) }
    }

    fun setFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(id, isFavorite) }
    }

    fun saveAsNote(article: RssArticleListItem) {
        viewModelScope.launch {
            if (repository.saveAsNote(article.id) != null) {
                _savedAsNoteEvents.send(RssSavedAsNoteEvent(article.title))
            }
        }
    }
}
