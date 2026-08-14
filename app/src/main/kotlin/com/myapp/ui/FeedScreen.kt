package com.myapp.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.articles.RssArticleListContent
import com.myapp.feature.knowledge.interview.InterviewChapterListContent

/**
 * 底部导航「知识」tab 的落地页（Route.Feed）：分「题库」（md 面试题库）/「资讯」（RSS）。
 *
 * 只能放在 :app——`:feature:knowledge` 和 `:feature:feed` 互不依赖，需要同时用到两者的组合页
 * 只能装在唯一允许依赖所有 feature 的 :app 里（同 PRD 4.7.1 的依赖方向约束）。
 * 子 tab 切换是本地 Compose 状态，不走 NavController：用 NavController 切换会污染返回栈
 * （子 tab 本不该产生「返回上一个 tab」的历史记录）。
 *
 * **顶部只有一层**：以前是 TabRow 一层 + 每个子页面各自的 TopAppBar 一层，
 * 两层叠起来吃掉近三分之一屏，而且都不跟随滚动。现在 tab 直接坐在 TopAppBar 的
 * title 槽里，整条顶栏用 enterAlwaysScrollBehavior，往下滚就收起去。
 * 代价是两个子页面不能再各自挂 Scaffold，所以它们都改成了 content 形态
 * （[InterviewChapterListContent] / [RssArticleListContent]），顶栏、Snackbar 由这里统一提供。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(onNavigate: (Route) -> Unit, modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val titles = remember { listOf("题库", "资讯") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        titles.forEachIndexed { index, title ->
                            TabPill(
                                text = title,
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                            )
                        }
                    }
                },
                actions = {
                    // 每个 tab 的右上角入口不同：题库进网页收藏，资讯进订阅源管理
                    val target = if (selectedTab == 0) Route.KnowledgeSources else Route.RssSources
                    val label = if (selectedTab == 0) "网页收藏" else "订阅源管理"
                    IconButton(onClick = { onNavigate(target) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = label)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        // 底栏是 MyApp 里浮在 Box 上的覆盖层，不在 innerPadding 里，要单独让出高度，
        // 否则列表最后几条会被它挡住（同 SettingsScreen 的处理）。
        val contentPadding = PaddingValues(
            start = Spacing.xl,
            end = Spacing.xl,
            top = innerPadding.calculateTopPadding(),
            bottom = LocalBottomBarHeight.current + Spacing.xxl,
        )
        when (selectedTab) {
            0 -> InterviewChapterListContent(
                onNavigate = onNavigate,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            )

            else -> RssArticleListContent(
                onNavigate = onNavigate,
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * 顶栏里的 tab。
 *
 * 不用 `TabRow`：它坚持撑满宽度并自带 48dp 高度和指示器，塞进 TopAppBar 的
 * title 槽里既高又占满，右边的 action 图标会被挤掉——那样就回到了「顶部太占地方」。
 * 这里用两枚紧凑的 pill，选中态靠底色区分。
 */
@Composable
private fun TabPill(text: String, selected: Boolean, onClick: () -> Unit) {
    // 用 primaryContainer 而不是 secondaryContainer：后者在本主题下是紫色调，
    // 与全 App 的陶土橙强调色（底栏选中态同款）对不上
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            androidx.compose.ui.graphics.Color.Transparent
        },
        label = "tabPillBackground",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.appColors.textSecondary
            },
        )
    }
}
