package com.myapp.feature.period.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.period.calendar.PeriodScreen

fun NavGraphBuilder.periodGraph(onBack: () -> Unit) {
    composable<Route.PeriodCalendar> {
        PeriodScreen(onBack = onBack)
    }
}
