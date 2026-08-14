package com.myapp.feature.period.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.period.calendar.PeriodScreen

fun NavGraphBuilder.periodGraph(onNavigate: (Route) -> Unit, onBack: () -> Unit) {
    composable<Route.PeriodCalendar> {
        PeriodScreen(
            onBack = onBack,
            // AI 设置页在 :feature:settings，经期页只发一个 Route 出去，
            // 由 :app 决定怎么跳——feature 之间不互相依赖（PRD 4.7）
            onOpenAiSettings = { onNavigate(Route.AiSettings) },
        )
    }
}
