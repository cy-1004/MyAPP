package com.myapp.feature.anniversary.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.anniversary.edit.AnniversaryEditScreen
import com.myapp.feature.anniversary.list.AnniversaryListScreen

/**
 * 纪念日的导航图。只在 :app 的 AppNavHost 里注册一次，其余模块无感知。
 */
fun NavGraphBuilder.anniversaryGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    composable<Route.AnniversaryList> {
        AnniversaryListScreen(onNavigate = onNavigate, onBack = onBack)
    }
    composable<Route.AnniversaryDetail> {
        AnniversaryEditScreen(onBack = onBack)
    }
}
