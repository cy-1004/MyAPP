package com.myapp.feature.knowledge.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.data.KnowledgeShareTarget
import com.myapp.feature.knowledge.extract.settings.KnowledgeExtractSettingsScreen
import com.myapp.feature.knowledge.list.KnowledgeListScreen
import com.myapp.feature.knowledge.list.KnowledgeSourceEditScreen
import com.myapp.feature.knowledge.reader.KnowledgeReaderScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 系统分享菜单「分享到 MyAPP」→ 知识源新建页深链，:app 用 EntryPointAccessors 取用
 * （与 LedgerGraphEntryPoint 同一套模式）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface KnowledgeGraphEntryPoint {
    fun knowledgeShareTarget(): KnowledgeShareTarget
}

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
