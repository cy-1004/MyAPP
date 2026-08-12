package com.myapp.feature.feed.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.articles.RssArticleDetailScreen
import com.myapp.feature.feed.articles.RssArticleListScreen
import com.myapp.feature.feed.sources.RssSourceEditScreen
import com.myapp.feature.feed.sources.RssSourceListScreen

/**
 * 本 feature 自己的导航图片段（同 knowledgeGraph 模式）。
 * `Route.RssArticles` 主要作为首页卡片「查看更多」的跳转目标——「资讯」子 tab 本身
 * 直接用 `RssArticleListScreen` 组合进 :app 的 FeedScreen，不经这个导航图。
 */
fun NavGraphBuilder.feedGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
) {
    composable<Route.RssArticles> {
        RssArticleListScreen(onNavigate = onNavigate)
    }

    composable<Route.RssSources> {
        RssSourceListScreen(onBack = onBack, onNavigate = onNavigate)
    }

    composable<Route.RssSourceDetail> {
        RssSourceEditScreen(onBack = onBack)
    }

    composable<Route.RssArticleDetail> {
        RssArticleDetailScreen(onBack = onBack)
    }
}
