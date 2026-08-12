package com.myapp.feature.feed.articles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.data.RssArticleUi
import com.myapp.feature.feed.data.RssFilter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * RSS 文章列表（PRD 3.9）：全部/未读/收藏筛选 + 下拉刷新。「资讯」子 tab 的内容。
 *
 * 没有周期性后台拉取——只在首次打开这个页面和下拉刷新时触发 [RssArticleListViewModel.refresh]
 * （见 RssRepository 顶部注释的裁剪说明）。点开文章即标记已读。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssArticleListScreen(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RssArticleListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refreshOnFirstOpen() }
    LaunchedEffect(Unit) {
        viewModel.savedAsNoteEvents.collect { event ->
            snackbarHostState.showSnackbar("已把「${event.title}」存为笔记", duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            FilterRow(current = state.filter, onSelect = viewModel::setFilter)

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.loaded && state.articles.isEmpty()) {
                    EmptyState(
                        text = emptyText(state.filter),
                        actionLabel = "添加订阅源",
                        onAction = { onNavigate(Route.RssSources) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = Spacing.xl,
                            end = Spacing.xl,
                            top = Spacing.sm,
                            bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(items = state.articles, key = { it.id }) { article ->
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
}

@Composable
private fun FilterRow(current: RssFilter, onSelect: (RssFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.sm),
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
    }
}

private fun emptyText(filter: RssFilter): String = when (filter) {
    RssFilter.All -> "还没有资讯，点右上角订阅源管理添加一个 RSS 地址"
    RssFilter.Unread -> "没有未读资讯"
    RssFilter.Favorite -> "还没有收藏的资讯"
    is RssFilter.Group -> "这个分组下还没有资讯"
}

@Composable
private fun RssArticleRow(
    article: RssArticleUi,
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
