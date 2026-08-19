package com.myapp.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.myapp.core.designsystem.theme.LocalMotionLevel
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 空态（PRD 5.4）：一句友好文案 + 可选主操作，**绝不留白屏**。
 *
 * 空态文案要具体、带一点温度，不要用「暂无数据」这种系统腔。
 *
 * 顶部带一个循环播放的矢量小动画（PRD 6.1）--三个圆点错峰呼吸，用 Accent/AccentContainer
 * 配色，只是个装饰性的小点缀，跟空态文案一起出现。只接这一处：`EmptyState` 是全 App
 * 唯一的空态入口，各列表页（待办/纪念日/笔记/分类……）不用各自接一遍就都有了。
 * 受 [LocalMotionLevel] 门控（`enableConfetti` 档才播，与撒花同一套「动效降级」判断），
 * 降级时只留文案，不留一块空白占位--动画只是点缀，不该占版式。
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (LocalMotionLevel.current.enableConfetti) {
            EmptyStateAnimation()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyStateAnimation() {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("lottie/empty_state_pulse.json"),
    )
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever,
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(56.dp),
    )
}

/** 卡片内的错误态：单张卡片出错不影响整页（PRD 4.7.2）。 */
@Composable
fun CardErrorState(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    EmptyState(
        text = "这块内容加载失败了",
        modifier = modifier,
        actionLabel = if (onRetry != null) "重试" else null,
        onAction = onRetry,
    )
}
