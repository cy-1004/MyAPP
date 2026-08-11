package com.myapp.feature.ledger.category

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.ui.categoryColor
import com.myapp.feature.ledger.ui.categoryIcon

/**
 * 分类管理列表（PRD 3.6 M5 Phase 3）。
 *
 * 两个 section：
 * - 启用中：记账编辑页选择器里能选到的分类。支持上移/下移调顺序（选择器按 sortOrder 排）、
 *   开关停用、左滑删除 + Snackbar 撤销、点击进编辑页。
 * - 已停用：不出现在选择器里，但历史账目照常显示它。可以随时开回来。
 *
 * 「未分类」是保留项：不可删、不可停用（开关禁用），只能改图标/颜色。
 * 自动记账没命中分类时要落到它，它没了 `LedgerRepository.resolveCategoryId` 就会 error。
 */
@Composable
fun CategoryListScreen(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${event.name}」",
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
                title = { Text("分类管理", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Route.CategoryDetail()) },
                modifier = Modifier.padding(bottom = LocalBottomBarHeight.current),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建分类")
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.sm,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "header-active") { SectionHeader("启用中") }

            if (state.loaded && state.active.isEmpty()) {
                item(key = "empty-active") {
                    EmptyState(
                        text = "一个启用的分类都没有，点 + 添加",
                        actionLabel = "添加",
                        onAction = { onNavigate(Route.CategoryDetail()) },
                    )
                }
            }
            items(items = state.active, key = { it.category.id }) { row ->
                CategoryRowItem(
                    row = row,
                    onClick = { onNavigate(Route.CategoryDetail(row.category.id)) },
                    onDelete = { viewModel.delete(row) },
                    onToggleActive = { on -> viewModel.setActive(row.category.id, on) },
                    onMove = { delta -> viewModel.move(row.category.id, delta) },
                )
            }

            if (state.inactive.isNotEmpty()) {
                item(key = "header-inactive") { SectionHeader("已停用") }
                items(items = state.inactive, key = { it.category.id }) { row ->
                    CategoryRowItem(
                        row = row,
                        onClick = { onNavigate(Route.CategoryDetail(row.category.id)) },
                        onDelete = { viewModel.delete(row) },
                        onToggleActive = { on -> viewModel.setActive(row.category.id, on) },
                        onMove = { delta -> viewModel.move(row.category.id, delta) },
                    )
                }
            }

            item(key = "footer-hint") {
                Text(
                    text = "停用的分类不再出现在记一笔的选择器里，已有账目仍然显示它。" +
                        "「未分类」是自动记账的兜底分类，不能删也不能停用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.appColors.textSecondary,
        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
    )
}

/**
 * 一行分类。保留项不挂左滑删除（直接渲染内容），其余走 SwipeToDismissBox。
 *
 * `confirmValueChange` 一律返回 false 让卡片滑回原位、由数据流驱动列表变化
 * （见交接文档第五节坑 #4）；回调用 [rememberUpdatedState] 包一层，
 * 否则永远拿到第一帧的旧引用。
 */
@Composable
private fun CategoryRowItem(
    row: CategoryRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (row.category.isProtected) {
        CategoryCard(row = row, onClick = onClick, onToggleActive = onToggleActive, onMove = onMove)
        return
    }

    val currentDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentDelete()
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
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
        CategoryCard(row = row, onClick = onClick, onToggleActive = onToggleActive, onMove = onMove)
    }
}

@Composable
private fun CategoryCard(
    row: CategoryRow,
    onClick: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val category = row.category
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(icon = category.icon, color = category.color)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitleOf(row),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }

            // 上移/下移：分类量级小，用确定性的箭头而不是拖拽排序
            IconButton(
                onClick = { onMove(-1) },
                enabled = !row.isFirst,
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(
                onClick = { onMove(1) },
                enabled = !row.isLast,
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
            }

            Switch(
                checked = category.isActive,
                onCheckedChange = onToggleActive,
                // 保留项必须常驻启用：自动记账没命中分类时要落到它
                enabled = !category.isProtected,
            )
        }
    }
}

@Composable
private fun CategoryBadge(icon: String, color: String) {
    val tint = categoryColor(color)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIcon(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun subtitleOf(row: CategoryRow): String {
    val usage = if (row.transactionCount > 0) "${row.transactionCount} 笔账目" else "还没有账目"
    return if (row.category.isProtected) "$usage · 保留项" else usage
}
