package com.myapp.feature.feed.articles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
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

/**
 * 列表页的「非分页」状态。文章本身走 [RssArticleListViewModel.articles] 那条
 * `PagingData` 流，不放在这里--PagingData 不是可以随便塞进 StateFlow 比较的普通值。
 */
data class RssArticleListState(
    val filter: RssFilter = RssFilter.All,
    /** 筛选器要用的订阅源清单（PRD 3.9「按订阅源」筛选）。 */
    val sources: List<RssSourceUi> = emptyList(),
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

    /**
     * 文章分页流（PRD 4.5）。
     *
     * 改版前是「limit 递增」：滚到底就把 limit 加 50 重查一次，
     * 结果是滚得越远、内存里攒的文章越多，一路涨到把符合条件的全部装进去
     * （库里有 5000+ 篇）。Paging 的 `maxSize` 会把滚远了的页丢掉，占用有上限。
     *
     * `cachedIn(viewModelScope)` 不能省：没有它，每次配置变更/重订阅都会
     * 从第一页重新加载，滚动位置和已加载的页全丢。
     */
    val articles: Flow<PagingData<RssArticleListItem>> = filter
        .flatMapLatest { repository.pagedArticles(it) }
        .cachedIn(viewModelScope)

    val state: StateFlow<RssArticleListState> = combine(
        repository.observeSources(),
        filter,
        refreshing,
    ) { sources, currentFilter, isRefreshing ->
        RssArticleListState(
            filter = currentFilter,
            sources = sources,
            refreshing = isRefreshing,
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

    /**
     * 换筛选条件后让列表回到顶部的一次性事件。
     *
     * 用 Channel 而不是在 UI 层 `LaunchedEffect(filter)`：那个 key 没变也会在
     * **composable 重新进入组合**时重跑一次，于是从文章详情页返回也会被弹回顶部
     * （改版时真踩到了）。事件只在 [setFilter] 真的改了值时发，语义精确。
     * CONFLATED：连点几个筛选只需要滚一次。
     */
    private val _scrollToTop = Channel<Unit>(Channel.CONFLATED)
    val scrollToTop: Flow<Unit> = _scrollToTop.receiveAsFlow()

    /**
     * 换筛选条件。不用再手动把 limit 收回首屏了--
     * `flatMapLatest` 会为新条件建一条全新的分页流，天然从第一页开始。
     * 但 `LazyListState` 的索引不会跟着重置，所以要显式发一次回顶事件。
     */
    fun setFilter(value: RssFilter) {
        if (filter.value == value) return
        filter.value = value
        _scrollToTop.trySend(Unit)
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
