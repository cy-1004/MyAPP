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
import com.myapp.feature.anniversary.navigation.anniversaryGraph
import com.myapp.feature.home.HomeScreen
import com.myapp.feature.period.navigation.periodGraph
import com.myapp.feature.settings.navigation.settingsGraph
import com.myapp.feature.todo.navigation.todoGraph

/**
 * 全局导航图。
 *
 * :app 是唯一知道「所有 feature 都存在」的地方--这正是它作为组装层的职责。
 * 新增 feature 时这里只加一行 `xxxGraph(...)`，其余模块无感知（PRD 4.7.3）。
 *
 * `startDestination` 由 MainActivity 根据首启状态决定（保活自检 PRD 9.3）：
 * 首次安装未完成自检 -> KeepAliveCheck；否则 -> Home。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: Route,
    modifier: Modifier = Modifier,
) {
    val onNavigate: (Route) -> Unit = { route -> navController.navigate(route) }
    val onBack: () -> Unit = { navController.popBackStack() }

    // 向导完成：清掉 KeepAliveCheck 页，进 Home。
    // 首启时栈为 [KeepAliveCheck]，popUpTo inclusive 后清空，再 navigate(Home)；
    // 非首启时栈为 [Home, Settings, KeepAliveCheck]，popUpTo 只清 KeepAliveCheck，
    // launchSingleTop 复用栈里已有的 Home（若已在栈里则回到它而不新建）。
    val onKeepAliveComplete: () -> Unit = {
        navController.navigate(Route.Home) {
            popUpTo(Route.KeepAliveCheck::class) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination::class,
        modifier = modifier,
        // 页面切换用 fade-through 而非左右滑：左右滑在竖向卡片流里显得廉价（PRD 6.2）
        enterTransition = { fadeIn(MotionTokens.standardTween()) },
        exitTransition = { fadeOut(MotionTokens.exitTween()) },
    ) {
        composable<Route.Home> {
            HomeScreen(onNavigate = onNavigate)
        }

        todoGraph(onNavigate = onNavigate, onBack = onBack)
        anniversaryGraph(onNavigate = onNavigate, onBack = onBack)
        periodGraph(onBack = onBack)
        settingsGraph(
            onNavigate = onNavigate,
            onBack = onBack,
            onKeepAliveComplete = onKeepAliveComplete,
            isFirstRun = startDestination::class == Route.KeepAliveCheck::class,
        )

        // 后续 feature 按此模式逐个注册：
        // noteGraph(onNavigate, onBack)
        // questionGraph(onNavigate, onBack)
        // ledgerGraph(onNavigate, onBack)
        // feedGraph(onNavigate, onBack)
    }
}
