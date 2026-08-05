package com.myapp.feature.todo.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.ui.navigation.Route

/**
 * 本 feature 自己的导航图片段（PRD 4.7.3）。
 *
 * :app 只需在导航图里调用一次 `todoGraph(...)`，
 * 新增页面时只改这个文件，不触碰其他模块。
 */
fun NavGraphBuilder.todoGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    composable<Route.TodoList> {
        TodoListScreen(onNavigate = onNavigate)
    }

    composable<Route.TodoDetail> { /* backStackEntry ->
        val args = backStackEntry.toRoute<Route.TodoDetail>()
        TodoDetailScreen(id = args.id, onBack = onBack)
    */ }
}

/** TODO 完整实现：今日 / 最近 7 天 / 全部 / 已完成 四个视图（PRD 3.3）。 */
@Composable
private fun TodoListScreen(onNavigate: (Route) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text("待办", style = MaterialTheme.typography.headlineMedium)
        Text(
            "列表页待实现",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
