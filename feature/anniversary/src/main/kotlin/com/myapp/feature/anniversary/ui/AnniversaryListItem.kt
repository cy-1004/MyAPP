package com.myapp.feature.anniversary.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.TabularNumbers
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.anniversary.data.Anniversary

/**
 * 纪念日条目。
 *
 * 版式是「左标题 + 右倒数」——倒数数字是这张卡唯一的视觉焦点，
 * 用衬线大号 + 等宽数字，其余全部降到次级色（PRD 5.3：一屏最多一个焦点）。
 */
@Composable
fun AnniversaryListItem(
    item: Anniversary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.appColors.border),
    ) {
        Row(
            // clickable 放在内层：Surface 会裁剪，涟漪才会跟着圆角走
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (item.pinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "已置顶",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.subtitle(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            CountdownText(item = item)
        }
    }
}

@Composable
fun CountdownText(
    item: Anniversary,
    modifier: Modifier = Modifier,
) {
    val countdown = item.countdown()
    // 今天发生的用强调色点亮，其余保持中性——否则满屏橙色就没有焦点了
    val accent = if (item.happeningToday()) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        countdown.prefix?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Text(
            text = countdown.number,
            // 等宽数字：天数每天都在变，不等宽会让整行左右跳动
            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers).copy(
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
            ),
            color = accent,
        )
        if (countdown.unit.isNotEmpty()) {
            Text(
                text = countdown.unit,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textSecondary,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}
