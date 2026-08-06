package com.myapp.feature.todo.list

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.myapp.feature.todo.data.Todo
import com.myapp.feature.todo.data.TodoFilter
import com.myapp.feature.todo.ui.TodoListItem

/**
 * 待办列表（PRD 3.3）。
 *
 * 交互约定：
 *   - 右滑 = 完成 / 取消完成
 *   - 左滑 = 删除，随后 Snackbar 可撤销（软删除，恢复无损）
 *   - 点击 = 进编辑页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val state by viewModel.items.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 删除后的撤销提示。事件流是 Channel，不会在返回本页时重放
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
                title = { Text("待办", style = MaterialTheme.typography.titleLarge) },
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
                onClick = { onNavigate(Route.TodoDetail()) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建待办")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            FilterRow(
                selected = filter,
                onSelect = viewModel::selectFilter,
            )

            when (val s = state) {
                is Result.Loading -> Column(
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md),
                ) {
                    CardSkeleton()
                }

                is Result.Error -> EmptyState(text = "列表加载失败了")

                is Result.Success -> if (s.data.isEmpty()) {
                    // 「已完成」是回顾视图，空态里不该出现「加一条」
                    val offerCreate = filter != TodoFilter.DONE
                    EmptyState(
                        text = emptyTextFor(filter),
                        actionLabel = if (offerCreate) "加一条" else null,
                        onAction = if (offerCreate) ({ onNavigate(Route.TodoDetail()) }) else null,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.xl,
                            end = Spacing.xl,
                            top = Spacing.sm,
                            bottom = 96.dp, // 给 FAB 让位
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(items = s.data, key = { it.id }) { todo ->
                            SwipeableTodoRow(
                                todo = todo,
                                onToggle = { viewModel.toggle(todo) },
                                onDelete = { viewModel.delete(todo) },
                                onClick = { onNavigate(Route.TodoDetail(todo.id)) },
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
private fun FilterRow(
    selected: TodoFilter,
    onSelect: (TodoFilter) -> Unit,
) {
    // 四个筛选项在窄屏上会挤，用横向滚动兜底而不是换行——换行会让顶部高度跳变
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TodoFilter.entries.forEach { item ->
            FilterChip(
                selected = item == selected,
                onClick = { onSelect(item) },
                label = { Text(item.label, style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTodoRow(
    todo: Todo,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // confirmValueChange 只在创建 state 时捕获一次，直接闭包会拿到第一帧的旧回调；
    // 用 rememberUpdatedState 让它始终指向最新的那一份
    val currentToggle by rememberUpdatedState(onToggle)
    val currentDelete by rememberUpdatedState(onDelete)

    val dismissState = rememberSwipeToDismissBoxState(
        // 一律返回 false：动作交给数据流去驱动列表变化，卡片本身滑回原位。
        // 若返回 true，条目会停在「已划走」的状态，撤销恢复后位置就错了。
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> currentToggle()
                SwipeToDismissBoxValue.EndToStart -> currentDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            SwipeBackground(direction = dismissState.dismissDirection)
        },
    ) {
        TodoListItem(todo = todo, onToggle = onToggle, onClick = onClick)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val isDelete = direction == SwipeToDismissBoxValue.EndToStart
    val color = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.appColors.danger
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.appColors.success
        SwipeToDismissBoxValue.Settled -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.xl),
        contentAlignment = if (isDelete) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Icon(
                imageVector = if (isDelete) Icons.Outlined.Delete else Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun emptyTextFor(filter: TodoFilter): String = when (filter) {
    TodoFilter.TODAY -> "今天没有安排 🎉"
    TodoFilter.WEEK -> "这一周还很空"
    TodoFilter.ALL -> "一条待办都没有"
    TodoFilter.DONE -> "还没有完成过任何事"
}
