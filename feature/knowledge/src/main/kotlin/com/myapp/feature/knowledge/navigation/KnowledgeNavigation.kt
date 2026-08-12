package com.myapp.feature.knowledge.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.extract.settings.KnowledgeExtractSettingsScreen
import com.myapp.feature.knowledge.list.KnowledgeListScreen
import com.myapp.feature.knowledge.list.KnowledgeSourceEditScreen
import com.myapp.feature.knowledge.reader.KnowledgeReaderScreen

fun NavGraphBuilder.knowledgeGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    composable<Route.KnowledgeSources> {
        KnowledgeListScreen(onNavigate = onNavigate)
    }

    composable<Route.KnowledgeSourceDetail> {
        KnowledgeSourceEditScreen(onBack = onBack)
    }

    composable<Route.KnowledgeReader> {
        KnowledgeReaderScreen(onBack = onBack)
    }

    composable<Route.KnowledgeExtractSettings> {
        KnowledgeExtractSettingsScreen(onBack = onBack)
    }
}
