package com.myapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myapp.feature.knowledge.navigation.KnowledgeGraphEntryPoint
import com.myapp.feature.ledger.data.LedgerDeepLink
import com.myapp.feature.ledger.navigation.LedgerGraphEntryPoint
import com.myapp.feature.widget.data.WidgetNavTarget
import com.myapp.feature.widget.data.WidgetScreens
import com.myapp.feature.widget.di.WidgetDataProvider
import dagger.hilt.android.EntryPointAccessors
import com.myapp.core.designsystem.component.BottomBarItem
import com.myapp.core.designsystem.component.FabAction
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.component.MyAppBottomBar
import com.myapp.core.designsystem.component.MultiActionFab
import com.myapp.core.ui.navigation.Route
import com.myapp.core.ui.navigation.TopLevelDestination
import com.myapp.navigation.AppNavHost
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * 顶层组装：Box 叠层放 AppNavHost + 底部导航 + 全局 FAB。
 *
 * 不用 Scaffold.bottomBar slot：Haze 毛玻璃需要 bottomBar 与 content 的 bounds 交叠
 * 才能采样到内容像素，而 Scaffold 把两者摆成兄弟节点互不重叠，Haze 会失效。
 * 改用 Box 叠层：AppNavHost 带 hazeSource 占满底层，MyAppBottomBar 带 hazeEffect 叠在上方。
 *
 * 底栏与 FAB 只在顶级目的地显示；进 Detail/编辑页等二级页面时全部隐藏。
 */
@Composable
fun MyApp(initialRoute: Route) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRouteStr = currentEntry?.destination?.route
    val hazeState = remember { HazeState() }

    // 自动记账通知 → 确认页深链（MainActivity 写入，这里收集后导航）
    val context = LocalContext.current
    val ledgerDeepLink = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LedgerGraphEntryPoint::class.java,
        ).ledgerDeepLink()
    }
    LaunchedEffect(Unit) {
        ledgerDeepLink.target.collect { id ->
            if (id != null) {
                navController.navigate(Route.LedgerDetail(id)) {
                    launchSingleTop = true
                }
                ledgerDeepLink.consume()
            }
        }
    }

    // 小组件点击 → 目标页面（MainActivity 写入，这里收集后导航）
    val widgetNavTarget = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataProvider::class.java,
        ).widgetNavTarget()
    }
    LaunchedEffect(Unit) {
        widgetNavTarget.target.collect { screen ->
            if (screen != null) {
                val route = widgetScreenToRoute(screen)
                if (route != null) {
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
                widgetNavTarget.consume()
            }
        }
    }

    // 系统分享菜单「分享到 MyAPP」→ 知识源新建页（MainActivity 写入，这里收集后导航）
    val knowledgeShareTarget = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            KnowledgeGraphEntryPoint::class.java,
        ).knowledgeShareTarget()
    }
    LaunchedEffect(Unit) {
        knowledgeShareTarget.target.collect { url ->
            if (url != null) {
                navController.navigate(Route.KnowledgeSourceDetail(sharedUrl = url)) {
                    launchSingleTop = true
                }
                knowledgeShareTarget.consume()
            }
        }
    }

    // 用 route 字符串匹配顶级目的地：toRoute<Route>() 不支持 sealed interface
    // （kotlinx.serialization 多态解码会抛 "Polymorphic value has not been read"）
    val topLevelDest = currentRouteStr?.let { str ->
        TopLevelDestination.entries.firstOrNull { dest -> str == dest.route::class.qualifiedName }
    }
    val showBottomBar = topLevelDest != null
    val showFab = currentRouteStr == Route.Home::class.qualifiedName

    var fabExpanded by remember { mutableStateOf(false) }
    if (fabExpanded) {
        BackHandler { fabExpanded = false }
    }

    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    LaunchedEffect(showBottomBar) {
        if (!showBottomBar) bottomBarHeight = 0.dp
    }

    CompositionLocalProvider(LocalBottomBarHeight provides bottomBarHeight) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavHost(
                navController = navController,
                startDestination = initialRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            )

            if (showBottomBar) {
                MyAppBottomBar(
                    items = TopLevelDestination.entries.map { it.toBottomBarItem() },
                    selectedIndex = TopLevelDestination.entries.indexOf(topLevelDest),
                    onItemClick = { index ->
                        val dest = TopLevelDestination.entries[index]
                        if (dest.route == Route.Home) {
                            // Home 是 startDestination，已在栈底。
                            // 用 navigate + launchSingleTop + restoreState 会让 navController
                            // 认为"目标已在栈里"，整个 navigate 变 no-op，连 popUpTo 都不执行，
                            // 上面的 Ledger/Todo 等页面弹不掉——实测从任何顶级页点主页都卡住。
                            // 改用 popBackStack 直接弹回 Home，行为确定。
                            navController.popBackStack(Route.Home, inclusive = false)
                        } else {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    hazeState = hazeState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned {
                            bottomBarHeight = with(density) { it.size.height.toDp() }
                        },
                )
            }

            if (showFab) {
                MultiActionFab(
                    actions = listOf(
                        FabAction("记笔记", Icons.AutoMirrored.Outlined.Article) {
                            navController.navigate(Route.NoteDetail())
                        },
                        FabAction("记疑问", Icons.AutoMirrored.Outlined.HelpOutline) {
                            navController.navigate(Route.QuestionDetail())
                        },
                        FabAction("记一笔", Icons.Outlined.AccountBalanceWallet) {
                            navController.navigate(Route.LedgerDetail())
                        },
                        FabAction("加待办", Icons.Outlined.CheckCircle) {
                            navController.navigate(Route.TodoDetail())
                        },
                    ),
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    hazeState = hazeState,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun TopLevelDestination.toBottomBarItem() = BottomBarItem(
    label = label,
    selectedIcon = selectedIcon,
    unselectedIcon = unselectedIcon,
)

/** 小组件目标页 → 导航路由；未知页忽略（等 ActionCallback 侧收敛）。 */
private fun widgetScreenToRoute(screen: String): Route? = when (screen) {
    WidgetScreens.HOME -> Route.Home
    WidgetScreens.LEDGER -> Route.Ledger
    WidgetScreens.LEDGER_NEW -> Route.LedgerDetail()
    WidgetScreens.TODO -> Route.TodoList
    WidgetScreens.ANNIVERSARY -> Route.AnniversaryList
    else -> null
}
