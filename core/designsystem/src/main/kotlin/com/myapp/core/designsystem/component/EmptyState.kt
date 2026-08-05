package com.myapp.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 空态（PRD 5.4）：一句友好文案 + 可选主操作，**绝不留白屏**。
 *
 * 空态文案要具体、带一点温度，不要用「暂无数据」这种系统腔。
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
