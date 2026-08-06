package com.myapp.feature.todo.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.todo.edit.TodoEditScreen
import com.myapp.feature.todo.list.TodoListScreen

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
        TodoListScreen(onNavigate = onNavigate, onBack = onBack)
    }

    // 新建与编辑共用：Route.TodoDetail() 的 id 默认为 0，表示新建
    composable<Route.TodoDetail> {
        TodoEditScreen(onBack = onBack)
    }
}
