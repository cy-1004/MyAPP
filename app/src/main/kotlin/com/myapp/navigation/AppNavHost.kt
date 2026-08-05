package com.myapp.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.myapp.core.designsystem.theme.MotionTokens
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.home.HomeScreen
import com.myapp.feature.todo.navigation.todoGraph

/**
 * 全局导航图。
 *
 * :app 是唯一知道「所有 feature 都存在」的地方——这正是它作为组装层的职责。
 * 新增 feature 时这里只加一行 `xxxGraph(...)`，其余模块无感知（PRD 4.7.3）。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val onNavigate: (Route) -> Unit = { route -> navController.navigate(route) }
    val onBack: () -> Unit = { navController.popBackStack() }

    NavHost(
        navController = navController,
        startDestination = Route.Home,
        modifier = modifier,
        // 页面切换用 fade-through 而非左右滑：左右滑在竖向卡片流里显得廉价（PRD 6.2）
        enterTransition = { fadeIn(MotionTokens.standardTween()) },
        exitTransition = { fadeOut(MotionTokens.exitTween()) },
    ) {
        composable<Route.Home> {
            HomeScreen(onNavigate = onNavigate)
        }

        todoGraph(onNavigate = onNavigate, onBack = onBack)

        // 后续 feature 按此模式逐个注册：
        // noteGraph(onNavigate, onBack)
        // questionGraph(onNavigate, onBack)
        // periodGraph(onNavigate, onBack)
        // ledgerGraph(onNavigate, onBack)
        // feedGraph(onNavigate, onBack)
        // settingsGraph(onNavigate, onBack)
    }
}
