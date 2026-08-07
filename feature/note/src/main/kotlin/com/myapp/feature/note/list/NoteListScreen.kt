package com.myapp.feature.note.list

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.result.Result
import com.myapp.core.designsystem.component.CardSkeleton
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.note.data.Note
import com.myapp.feature.note.ui.NoteListItem

/**
 * 笔记列表（PRD 3.4）。
 *
 * 交互约定：
 *   - 左滑 = 删除，随后 Snackbar 可撤销（软删除，恢复无损）
 *   - 点击 = 进编辑页
 *   - 搜索栏输入 = 实时 FTS 全文搜索（高亮在编辑页展示，列表只显示命中条目）
 *   - 标签 chip = 切换标签筛选（与搜索互不冲突，搜索优先）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tag by viewModel.tag.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val state by viewModel.items.collectAsStateWithLifecycle()
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
                title = { Text("笔记", style = MaterialTheme.typography.titleLarge) },
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
                onClick = { onNavigate(Route.NoteDetail()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建笔记")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SearchBar(
                query = query,
                onQueryChange = viewModel::updateQuery,
            )

            TagFilterRow(
                tags = tags,
                selected = tag,
                onSelect = viewModel::selectTag,
            )

            when (val s = state) {
                is Result.Loading -> Column(
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                ) {
                    CardSkeleton()
                }

                is Result.Error -> EmptyState(text = "列表加载失败了")

                is Result.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        text = emptyTextFor(query, tag),
                        actionLabel = "记一笔",
                        onAction = { onNavigate(Route.NoteDetail()) },
                    )
                } else {
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
                        items(items = s.data, key = { it.id }) { note ->
                            SwipeableNoteRow(
                                note = note,
                                onDelete = { viewModel.delete(note) },
                                onClick = { onNavigate(Route.NoteDetail(note.id)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        placeholder = { Text("搜索笔记内容", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.appColors.textTertiary,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "清除搜索")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.appColors.border,
        ),
    )
}

@Composable
private fun TagFilterRow(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    if (tags.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("全部", style = MaterialTheme.typography.labelLarge) },
            shape = MaterialTheme.shapes.small,
        )
        tags.forEach { tagItem ->
            FilterChip(
                selected = selected == tagItem,
                onClick = { onSelect(tagItem) },
                label = { Text(tagItem, style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoteRow(
    note: Note,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // confirmValueChange 只在创建 state 时捕获一次，直接闭包会拿到第一帧的旧回调；
    // 用 rememberUpdatedState 让它始终指向最新的那一份（与 TodoListScreen 同一坑）
    val currentDelete by rememberUpdatedState(onDelete)

    val dismissState = rememberSwipeToDismissBoxState(
        // 一律返回 false：动作交给数据流驱动列表变化，卡片本身滑回原位
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentDelete()
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            SwipeBackground(dismissState.dismissDirection)
        },
    ) {
        NoteListItem(note = note, onClick = onClick)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val color = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.appColors.danger
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.xl),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (direction == SwipeToDismissBoxValue.EndToStart) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun emptyTextFor(query: String, tag: String?): String = when {
    query.isNotBlank() -> "没找到包含「$query」的笔记"
    tag != null -> "带「$tag」标签的笔记还没有"
    else -> "一条笔记都还没写"
}
