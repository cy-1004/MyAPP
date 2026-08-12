package com.myapp.feature.todo.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.result.Result
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.CardErrorState
import com.myapp.core.designsystem.component.CardHeader
import com.myapp.core.designsystem.component.CardSkeleton
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.MotionTokens
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardOrder
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.todo.data.Todo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * 「今日待办」首页卡片。
 *
 * 这是 HomeCard 插件机制的参考实现（PRD 4.7.2）--照着这个文件的结构，
 * 新增任何首页卡片都不需要修改 :feature:home 的任何代码。
 *
 * 点击单条待办不再直接 toggle 完成 -- 弹 [CompleteTodoDialog] 让用户确认 + 可选填备注，
 * 完成后用 Snackbar 提供撤销入口（避免误触把待办标完成）。
 */
class TodoHomeCard @Inject constructor() : HomeCard {

    override val id: String = "todo"
    override val defaultOrder: Int = HomeCardOrder.TODO
    override val displayName: String = "今日待办"

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        // 每张卡片持有自己的 ViewModel 和独立数据流，
        // 慢的卡片不会阻塞快的（PRD 4.5）。
        val viewModel: TodoCardViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val pendingTodo by viewModel.pendingTodo.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        // 完成后的撤销提示（与 TodoListScreen 删除撤销同套路：Channel 事件 + Snackbar Short）
        LaunchedEffect(Unit) {
            viewModel.undoEvents.collect { event ->
                val result = snackbarHostState.showSnackbar(
                    message = "已完成「${event.title}」",
                    actionLabel = "撤销",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoComplete(event.id)
                }
            }
        }

        // Box 叠层：AppCard 在底，Snackbar 浮在卡片底部（HomeScreen 没有 Scaffold，
        // 卡片需要自己 hold SnackbarHostState；与 TodoListScreen 的 Scaffold.snackbarHost 不同）
        Box {
            AppCard(onClick = { onNavigate(Route.TodoList) }) {
                CardHeader(title = "今日待办")

                when (val s = state) {
                    is Result.Loading -> CardSkeleton()

                    is Result.Error -> CardErrorState()

                    is Result.Success -> {
                        if (s.data.isEmpty()) {
                            EmptyState(
                                text = "今天没有安排 🎉",
                                actionLabel = "加一条",
                                // 直接进新建页而不是列表页--空态里点「加一条」的意图很明确
                                onAction = { onNavigate(Route.TodoDetail()) },
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                s.data.take(MAX_ITEMS).forEach { todo ->
                                    TodoRow(
                                        todo = todo,
                                        onRequestComplete = { viewModel.requestComplete(todo) },
                                    )
                                }
                                val remaining = s.data.size - MAX_ITEMS
                                if (remaining > 0) {
                                    Text(
                                        text = "还有 $remaining 项 ->",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.appColors.textSecondary,
                                        modifier = Modifier.padding(top = Spacing.xs),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        pendingTodo?.let { todo ->
            CompleteTodoDialog(
                todoTitle = todo.title,
                onDismiss = viewModel::cancelCompleteDialog,
                onConfirm = { note -> viewModel.confirmComplete(note) },
            )
        }
    }

    private companion object {
        const val MAX_ITEMS = 3
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    onRequestComplete: () -> Unit,
) {
    // 勾选后条目滑出列表（PRD 6.2）：先播完成动画，再由数据流移除
    AnimatedVisibility(
        visible = !todo.done,
        exit = fadeOut(MotionTokens.exitTween()) + shrinkVertically(MotionTokens.exitTween()),
    ) {
        val checkScale by animateFloatAsState(
            targetValue = if (todo.done) 1.1f else 1f,
            animationSpec = MotionTokens.enterSpring(),
            label = "checkScale",
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRequestComplete)
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Icon(
                imageVector = if (todo.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = "标记为完成",
                tint = if (todo.done) {
                    MaterialTheme.appColors.success
                } else if (todo.isOverdue) {
                    MaterialTheme.appColors.danger
                } else {
                    MaterialTheme.appColors.textTertiary
                },
                modifier = Modifier
                    .size(22.dp)
                    .scale(checkScale),
            )

            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (todo.isOverdue) {
                    MaterialTheme.appColors.danger
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            todo.dueAt?.let { due ->
                Text(
                    text = with(com.myapp.core.common.time.AppTime) {
                        due.toLocalDateTime().format(com.myapp.core.common.time.AppFormatters.time)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
        }
    }
}

/**
 * 把卡片注入全局卡片集合。
 * **这是唯一的注册动作**--首页会自动发现并渲染它。
 */
@Module
@InstallIn(SingletonComponent::class)
interface TodoHomeCardModule {
    @Binds
    @IntoSet
    fun bindTodoHomeCard(card: TodoHomeCard): HomeCard
}
