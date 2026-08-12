package com.myapp.feature.ledger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.ledger.data.CategoryExpenseItem

/**
 * 分类支出排行的一行：图标 + 名称 + 占比条 + 金额。预算视图与统计页共用
 * （两边都是「一个区间内按分类汇总的支出」，只是区间口径不同）。
 */
@Composable
fun CategoryExpenseRow(item: CategoryExpenseItem, totalCents: Long) {
    val tint = categoryColor(item.color)
    val fraction = if (totalCents <= 0L) 0f else (item.totalCents.toFloat() / totalCents).coerceIn(0f, 1f)
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(item.icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = item.totalCents.yuanWithSymbol(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(modifier = Modifier.padding(vertical = Spacing.xs)) {
                    ProgressTrack(fraction = fraction, color = tint)
                }
                Text(
                    text = "${item.count} 笔 · 占比 ${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
        }
    }
}

/**
 * 进度条：定宽轨道 + 按比例填充的 Box。
 * 不用 LinearProgressIndicator 是为了自己控制圆角与配色（M3 默认带端点缺口与动效）。
 */
@Composable
fun ProgressTrack(fraction: Float, color: Color) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(shape)
            .background(MaterialTheme.appColors.border),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(shape)
                .background(color),
        )
    }
}

/** 进度条配色：>=90% 危险 / >=70% 警示 / 其余正常。预算视图与统计页同口径。 */
@Composable
fun progressColor(fraction: Float, overspent: Boolean = false): Color = when {
    overspent || fraction >= 0.9f -> MaterialTheme.appColors.danger
    fraction >= 0.7f -> MaterialTheme.appColors.warning
    else -> MaterialTheme.colorScheme.primary
}
