package com.myapp.feature.question.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.question.edit.QuestionEditScreen
import com.myapp.feature.question.list.QuestionListScreen

/**
 * 疑问的导航图。只在 :app 的 AppNavHost 里注册一次，其余模块无感知。
 *
 * 新建与编辑共用 [Route.QuestionDetail]：id 默认 0 表示新建，与待办/笔记同一套约定。
 */
fun NavGraphBuilder.questionGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    composable<Route.QuestionList> {
        QuestionListScreen(onNavigate = onNavigate, onBack = onBack)
    }
    composable<Route.QuestionDetail> {
        QuestionEditScreen(onNavigate = onNavigate, onBack = onBack)
    }
}
