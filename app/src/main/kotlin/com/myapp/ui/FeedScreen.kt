package com.myapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.articles.RssArticleListScreen
import com.myapp.feature.knowledge.list.KnowledgeListScreen

/**
 * 底部导航「知识」tab 的落地页（Route.Feed）：顶部子 tab 分「知识库」（M6）/「资讯」（M8 RSS）。
 *
 * 只能放在 :app——`:feature:knowledge` 和 `:feature:feed` 互不依赖，需要同时用到两者的组合页
 * 只能装在唯一允许依赖所有 feature 的 :app 里（同 PRD 4.7.1 的依赖方向约束）。
 * 子 tab 切换是本地 Compose 状态，不走 NavController：两个子页面各自有独立的 Scaffold/FAB，
 * 用 NavController 切换会污染返回栈（子 tab 本不该产生"返回上一个 tab"的历史记录）。
 */
@Composable
fun FeedScreen(onNavigate: (Route) -> Unit, modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val titles = remember { listOf("知识库", "资讯") }

    Column(modifier = modifier.fillMaxSize()) {
        // 子 tab 本身要手动吃掉状态栏高度（edge-to-edge 强制生效，PRD 9.2）；
        // 下面 KnowledgeListScreen/RssArticleListScreen 各自的 Scaffold+TopAppBar
        // 也会按 WindowInsets.statusBars 再算一次顶部 padding，两层叠加会在 TabRow
        // 下面多出一条状态栏高度的空白——用 consumeWindowInsets 把状态栏 inset
        // 标记为「已经被 TabRow 吃掉」，子屏幕就不会重复占位。
        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.statusBarsPadding()) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.statusBars)) {
            when (selectedTab) {
                0 -> KnowledgeListScreen(onNavigate = onNavigate, modifier = Modifier.fillMaxSize())
                else -> RssArticleListScreen(onNavigate = onNavigate, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
