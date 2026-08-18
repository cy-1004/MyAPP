package com.myapp.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.myapp.core.designsystem.theme.LocalSharedTransitionScope
import com.myapp.core.designsystem.theme.MotionTokens
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.anniversary.navigation.anniversaryGraph
import com.myapp.feature.feed.navigation.feedGraph
import com.myapp.feature.home.HomeScreen
import com.myapp.feature.knowledge.navigation.knowledgeGraph
import com.myapp.feature.ledger.navigation.ledgerGraph
import com.myapp.feature.note.navigation.noteGraph
import com.myapp.feature.period.navigation.periodGraph
import com.myapp.feature.question.navigation.questionGraph
import com.myapp.feature.settings.navigation.settingsGraph
import com.myapp.feature.todo.navigation.todoGraph
import com.myapp.ui.FeedScreen

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

    // 共享元素转场的外层作用域（PRD 6.2）。
    //
    // 必须包在 NavHost **外面**：共享元素要在「离场页」和「入场页」之间连线，
    // 而这两个页面分属两个不同的导航目的地，只有它们共同的祖先才看得见双方。
    // 作用域经 LocalSharedTransitionScope 下发给各 feature（feature 之间不许互相依赖，
    // 也不该为一个动效把作用域透传进每层 Composable 的签名）。
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = startDestination::class,
                modifier = modifier,
                // 页面切换用 fade-through 而非左右滑：左右滑在竖向卡片流里显得廉价（PRD 6.2）。
                // 共享元素与它并行：整页淡入淡出的同时，被标记的那个元素连续变形，
                // 这正是「点的那张卡片长成了这一页」的观感来源。
                enterTransition = { fadeIn(MotionTokens.standardTween()) },
                exitTransition = { fadeOut(MotionTokens.exitTween()) },
            ) {
                composable<Route.Home> {
                    HomeScreen(onNavigate = onNavigate)
                }

                composable<Route.Feed> {
                    FeedScreen(onNavigate = onNavigate)
                }

                todoGraph(onNavigate = onNavigate, onBack = onBack)
                anniversaryGraph(onNavigate = onNavigate, onBack = onBack)
                periodGraph(onNavigate = onNavigate, onBack = onBack)
                noteGraph(onNavigate = onNavigate, onBack = onBack)
                questionGraph(onNavigate = onNavigate, onBack = onBack)
                ledgerGraph(onNavigate = onNavigate, onBack = onBack, navController = navController)
                knowledgeGraph(onNavigate = onNavigate, onBack = onBack)
                feedGraph(onNavigate = onNavigate, onBack = onBack)
                settingsGraph(
                    onNavigate = onNavigate,
                    onBack = onBack,
                    onKeepAliveComplete = onKeepAliveComplete,
                    isFirstRun = startDestination::class == Route.KeepAliveCheck::class,
                )
            }
        }
    }
}
