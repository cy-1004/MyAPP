package com.myapp.feature.feed.articles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.data.RssArticleListItem
import com.myapp.feature.feed.data.RssFilter
import com.myapp.feature.feed.data.RssSourceUi
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RSS 文章列表的**独立页**（Route.RssArticles，首页卡片「查看更多」跳这里）。
 *
 * 「知识」tab 里的资讯子页不走这个入口，而是直接用 [RssArticleListContent]——
 * 那边的顶栏由宿主统一提供，子页面再挂一个 Scaffold 会让顶部叠两层。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssArticleListScreen(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("资讯", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { onNavigate(Route.RssSources) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "订阅源管理")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        RssArticleListContent(
            onNavigate = onNavigate,
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = innerPadding.calculateTopPadding(),
                bottom = 96.dp,
            ),
            snackbarHostState = snackbarHostState,
        )
    }
}

/**
 * 资讯列表内容（PRD 3.9）：全部/未读/收藏/按订阅源筛选 + 下拉刷新 + 滚到底加载下一屏。
 *
 * 没有周期性后台拉取——只在首次打开和下拉刷新时触发 [RssArticleListViewModel.refresh]
 * （见 RssRepository 顶部注释的裁剪说明）。点开文章即标记已读。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssArticleListContent(
    onNavigate: (Route) -> Unit,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: RssArticleListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshOnFirstOpen() }
    LaunchedEffect(Unit) {
        viewModel.savedAsNoteEvents.collect { event ->
            snackbarHostState.showSnackbar("已把「${event.title}」存为笔记", duration = SnackbarDuration.Short)
        }
    }

    // 预取由 Paging 自己按 prefetchDistance 做，不用再监听滚动位置手动 loadMore
    val articles = viewModel.articles.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    // 换筛选条件后回到顶部。改版前是「limit 收回首屏」顺带把列表挤短、位置被迫拉回，
    // 换 Paging 之后新的分页流虽然从第一页开始，但 LazyListState 的索引不会跟着重置--
    // 切到条目少得多的筛选（某个订阅源）就会落在莫名其妙的位置。
    // 事件由 ViewModel 在真的改了筛选时发（不能用 LaunchedEffect(filter)，原因见那边注释）。
    LaunchedEffect(Unit) {
        viewModel.scrollToTop.collect { listState.scrollToItem(0) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        FilterRow(
            current = state.filter,
            sources = state.sources,
            onSelect = viewModel::setFilter,
            // 筛选条要吃掉顶栏高度，否则会被收起中的顶栏盖住
            topPadding = contentPadding.calculateTopPadding(),
        )

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            // 空态要等首次加载真的结束再显示，否则首帧会闪一下「还没有文章」。
            // loadState.refresh 是 NotLoading 才算加载完（初始值是 Loading）
            val loaded = articles.loadState.refresh !is LoadState.Loading
            if (loaded && articles.itemCount == 0) {
                EmptyState(
                    text = emptyText(state.filter),
                    actionLabel = "添加订阅源",
                    onAction = { onNavigate(Route.RssSources) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                        end = contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                        top = Spacing.sm,
                        bottom = contentPadding.calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(
                        count = articles.itemCount,
                        key = articles.itemKey { it.id },
                    ) { index ->
                        // enablePlaceholders = false，所以理论上不会是 null；
                        // 判空只是为了不让边界情况把整页崩掉
                        val article = articles[index] ?: return@items
                        RssArticleRow(
                            article = article,
                            onClick = {
                                viewModel.setRead(article.id, true)
                                onNavigate(Route.RssArticleDetail(article.id))
                            },
                            onToggleFavorite = { viewModel.setFavorite(article.id, !article.isFavorite) },
                            onSaveAsNote = { viewModel.saveAsNote(article) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 筛选条：固定的「全部/未读/收藏」+ 每个订阅源一枚 chip（PRD 3.9 按订阅源筛选）。
 *
 * 订阅源可能有十几个，一行放不下，所以整条横向滚动而不是换行——
 * 换行会让筛选条在源多的时候吃掉半屏，与「顶部占比太大」的问题冲突。
 */
@Composable
private fun FilterRow(
    current: RssFilter,
    sources: List<RssSourceUi>,
    onSelect: (RssFilter) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(selected = current == RssFilter.All, onClick = { onSelect(RssFilter.All) }, label = { Text("全部") })
        FilterChip(
            selected = current == RssFilter.Unread,
            onClick = { onSelect(RssFilter.Unread) },
            label = { Text("未读") },
        )
        FilterChip(
            selected = current == RssFilter.Favorite,
            onClick = { onSelect(RssFilter.Favorite) },
            label = { Text("收藏") },
        )
        sources.forEach { source ->
            FilterChip(
                selected = current == RssFilter.Source(source.id),
                onClick = { onSelect(RssFilter.Source(source.id)) },
                label = {
                    Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

private fun emptyText(filter: RssFilter): String = when (filter) {
    RssFilter.All -> "还没有资讯，点右上角订阅源管理添加一个 RSS 地址"
    RssFilter.Unread -> "没有未读资讯"
    RssFilter.Favorite -> "还没有收藏的资讯"
    is RssFilter.Group -> "这个分组下还没有资讯"
    is RssFilter.Source -> "这个订阅源下还没有资讯"
}

@Composable
private fun RssArticleRow(
    article: RssArticleListItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveAsNote: () -> Unit,
) {
    AppCard(onClick = onClick) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (article.coverImageUrl != null) {
                AsyncImage(
                    model = article.coverImageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (article.isRead) FontWeight.Normal else FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${article.sourceTitle} · ${formatTime(article.publishedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textTertiary,
                )
                if (article.summary.isNotBlank()) {
                    Text(
                        text = article.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSaveAsNote) {
                Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = "存为笔记")
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (article.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (article.isFavorite) "取消收藏" else "收藏",
                    tint = if (article.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.appColors.textTertiary,
                )
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
