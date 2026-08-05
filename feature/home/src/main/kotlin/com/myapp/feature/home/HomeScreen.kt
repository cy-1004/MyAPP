package com.myapp.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import com.myapp.core.designsystem.theme.LocalMotionLevel
import com.myapp.core.designsystem.theme.MotionTokens
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.navigation.Route

/**
 * 首页：一个不认识任何业务的卡片编排器。
 *
 * 它做的全部事情就是——拿到 `Set<HomeCard>`，排序，逐个渲染。
 * 想加一张卡片？去对应 feature 里实现 HomeCard 并 @IntoSet，这里不用动。
 */
@Composable
fun HomeScreen(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val cards by viewModel.visibleCards.collectAsStateWithLifecycle()

    // 入场瀑布动画只在首次进入时播放，之后重组不再重播（PRD 6.2）
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.xl,
            end = Spacing.xl,
            top = Spacing.lg,
            bottom = 96.dp, // 给底部导航与 FAB 留出空间
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item(key = "greeting") {
            GreetingHeader()
        }

        // 稳定 key 用卡片 id——顺序变化时不重建整列，也让入场动画不错乱
        items(
            count = cards.size,
            key = { index -> cards[index].id },
        ) { index ->
            StaggeredCard(index = index, entered = entered) {
                cards[index].Content(onNavigate)
            }
        }
    }
}

/**
 * 错峰入场：自上而下依次淡入 + 上移，形成瀑布感。
 * 每张卡延迟 40ms（MotionTokens.StaggerDelayMs），超过 6 张后不再累加延迟，
 * 否则最后一张要等太久。
 */
@Composable
private fun StaggeredCard(
    index: Int,
    entered: Boolean,
    content: @Composable () -> Unit,
) {
    val motionLevel = LocalMotionLevel.current
    if (!motionLevel.enableTransitions) {
        content()
        return
    }

    val delay = (index.coerceAtMost(6)) * MotionTokens.StaggerDelayMs
    val duration = motionLevel.scale(MotionTokens.DurationStandard)

    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(tween(duration, delayMillis = delay)) +
            slideInVertically(
                animationSpec = tween(duration, delayMillis = delay),
                initialOffsetY = { it / 6 },
            ),
    ) {
        content()
    }
}

@Composable
private fun GreetingHeader() {
    val today = remember { AppTime.today() }
    Column(
        modifier = Modifier.padding(bottom = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = today.format(AppFormatters.date),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = weekdayLabel(today.dayOfWeek.value),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.textSecondary,
        )
    }
}

private fun weekdayLabel(isoDayOfWeek: Int): String = when (isoDayOfWeek) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
