package com.myapp.feature.knowledge.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.data.KnowledgeFetchStatus
import com.myapp.feature.knowledge.data.KnowledgeSearchHit
import com.myapp.feature.knowledge.data.KnowledgeSourceUi

/**
 * 知识源管理列表（PRD 3.7）：新建/编辑/启停/删除/排序 + 正文全文搜索。
 *
 * 与 `CategoryListScreen` 同一套交互语言：左滑删除 + Snackbar 撤销、
 * 上移/下移箭头调顺序（量级小，不做拖拽）。搜索命中跳转到对应知识源的阅读页。
 */
@Composable
fun KnowledgeListScreen(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${event.title}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(event.id)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("知识库", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { onNavigate(Route.KnowledgeExtractSettings) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "知识库设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Route.KnowledgeSourceDetail()) },
                modifier = Modifier.padding(bottom = LocalBottomBarHeight.current),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加知识源")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setQuery,
                label = { Text("搜索已缓存的正文") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )

            if (state.isSearching) {
                SearchResultList(
                    results = state.searchResults,
                    onClick = { hit -> onNavigate(Route.KnowledgeReader(hit.sourceId)) },
                )
            } else {
                SourceGroupList(
                    groups = state.groups,
                    loaded = state.loaded,
                    onNavigate = onNavigate,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun SearchResultList(results: List<KnowledgeSearchHit>, onClick: (KnowledgeSearchHit) -> Unit) {
    if (results.isEmpty()) {
        EmptyState(text = "没有搜到匹配的正文", modifier = Modifier.fillMaxSize())
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(items = results, key = { it.sourceId }) { hit ->
            AppCard(onClick = { onClick(hit) }) {
                Column {
                    Text(
                        text = hit.sourceTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = hit.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceGroupList(
    groups: List<KnowledgeGroup>,
    loaded: Boolean,
    onNavigate: (Route) -> Unit,
    viewModel: KnowledgeListViewModel,
) {
    if (loaded && groups.isEmpty()) {
        EmptyState(
            text = "还没有知识源，点 + 添加一个飞书公开链接",
            actionLabel = "添加",
            onAction = { onNavigate(Route.KnowledgeSourceDetail()) },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.xl,
            end = Spacing.xl,
            top = Spacing.sm,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.name}") { GroupHeader(group.name) }
            items(items = group.rows, key = { it.source.id }) { row ->
                KnowledgeSourceRowItem(
                    row = row,
                    onClick = { onNavigate(Route.KnowledgeReader(row.source.id)) },
                    onEdit = { onNavigate(Route.KnowledgeSourceDetail(row.source.id)) },
                    onDelete = { viewModel.delete(row) },
                    onToggleEnabled = { on -> viewModel.setEnabled(row.source.id, on) },
                    onTogglePinned = { pinned -> viewModel.setPinned(row.source.id, pinned) },
                    onToggleInPool = { inPool -> viewModel.setInPool(row.source.id, inPool) },
                    onMove = { delta -> viewModel.move(row.source.id, delta) },
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.appColors.textSecondary,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
    )
}

/**
 * `confirmValueChange` 一律返回 false 让卡片滑回原位、由数据流驱动列表变化
 * （与 `CategoryListScreen` 同一套约定）；回调用 [rememberUpdatedState] 包一层。
 */
@Composable
private fun KnowledgeSourceRowItem(
    row: KnowledgeSourceRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePinned: (Boolean) -> Unit,
    onToggleInPool: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val currentDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentDelete()
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.appColors.danger, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        AppCard(onClick = onClick) {
            // 标题/URL 单独一行占满卡片宽度——之前跟五个操作图标挤在同一行时，
            // 图标的固定宽度加起来会超过卡片可用宽度，导致标题这个 weight(1f) 的
            // 列被挤压到 0 宽度，整行文字直接消失不可见（真机验证时发现）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FetchStatusIcon(row.source.fetchStatus)

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.source.title.ifBlank { row.source.url },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                }

                IconButton(onClick = { onTogglePinned(!row.source.pinned) }) {
                    Icon(
                        imageVector = if (row.source.pinned) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (row.source.pinned) "取消置顶" else "置顶",
                        tint = if (row.source.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.appColors.textTertiary,
                    )
                }

                // 知识池（M7 每日知识点候选）与置顶是两个独立开关：
                // 置顶管首页快捷入口，知识池管会不会被随机推送复习。
                IconButton(onClick = { onToggleInPool(!row.source.inPool) }) {
                    Icon(
                        imageVector = if (row.source.inPool) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                        contentDescription = if (row.source.inPool) "移出知识池" else "加入知识池",
                        tint = if (row.source.inPool) MaterialTheme.colorScheme.primary else MaterialTheme.appColors.textTertiary,
                    )
                }

                // 上移/下移：知识源量级小，用确定性的箭头而不是拖拽排序（同分类管理取舍）
                IconButton(onClick = { onMove(-1) }, enabled = !row.isFirst) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = { onMove(1) }, enabled = !row.isLast) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                }

                Switch(checked = row.source.enabled, onCheckedChange = onToggleEnabled)
            }
        }
    }
}

@Composable
private fun FetchStatusIcon(status: KnowledgeFetchStatus) {
    when (status) {
        KnowledgeFetchStatus.LOGIN_REQUIRED -> Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = "需要登录，已失效",
            tint = MaterialTheme.appColors.danger,
            modifier = Modifier.size(20.dp),
        )

        KnowledgeFetchStatus.FAILED -> Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = "提取失败",
            tint = MaterialTheme.appColors.textTertiary,
            modifier = Modifier.size(20.dp),
        )

        KnowledgeFetchStatus.PENDING, KnowledgeFetchStatus.SUCCESS -> Unit
    }
}
