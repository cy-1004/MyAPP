package com.myapp.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.settings.SettingsScreen
import com.myapp.feature.settings.cardorder.HomeCardOrderScreen
import com.myapp.feature.settings.keepalive.KeepAliveCheckScreen
import com.myapp.feature.settings.periodreminder.PeriodReminderSettingsScreen

/**
 * 设置模块的导航图（PRD 4.7.3）。
 *
 * `onKeepAliveComplete` 单独拎出来：向导完成后需要做 `navigate(Home) + popUpTo(KeepAliveCheck)`
 * 的栈操作，这必须由持有 NavController 的 :app 层执行，feature 不该知道栈结构。
 */
fun NavGraphBuilder.settingsGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    onKeepAliveComplete: () -> Unit,
    isFirstRun: Boolean = false,
) {
    composable<Route.Settings> {
        SettingsScreen(onNavigate = onNavigate, onBack = onBack)
    }

    composable<Route.KeepAliveCheck> {
        KeepAliveCheckScreen(
            onBack = onBack,
            onComplete = onKeepAliveComplete,
            firstRun = isFirstRun,
        )
    }

    composable<Route.HomeCardOrder> {
        HomeCardOrderScreen(onBack = onBack)
    }

    composable<Route.PeriodReminderSettings> {
        PeriodReminderSettingsScreen(onBack = onBack)
    }
}
