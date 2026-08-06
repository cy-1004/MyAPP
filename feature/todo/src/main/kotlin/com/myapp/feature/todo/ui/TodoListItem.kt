package com.myapp.feature.todo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.theme.MotionTokens
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.todo.data.Priority
import com.myapp.feature.todo.data.Todo

/**
 * 列表里的一条待办。
 *
 * 视觉遵循 PRD 5.3：卡片用 1px 描边而非阴影；一屏只允许一个 Accent 焦点，
 * 所以高优先级只用一根 3dp 竖条提示，不整条染色。
 */
@Composable
fun TodoListItem(
    todo: Todo,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checkScale by animateFloatAsState(
        targetValue = if (todo.done) 1.12f else 1f,
        animationSpec = MotionTokens.enterSpring(),
        label = "listCheckScale",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.appColors.border),
    ) {
        // 点击放在 Row 而不是 Surface 上：Surface 会按 shape 裁剪子元素，
        // 涟漪因此自动跟着圆角走，不用再手动 clip 一次
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(
                    start = Spacing.md,
                    end = Spacing.lg,
                    top = Spacing.md,
                    bottom = Spacing.md,
                ),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // 勾选热区独立于整行点击：点圆圈=完成，点其他=进编辑。
            // indication 置空是刻意的——默认涟漪在暖米白底上会发灰（PRD 5.3）
            val interaction = remember { MutableInteractionSource() }
            Icon(
                imageVector = if (todo.done) {
                    Icons.Outlined.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = if (todo.done) "标记为未完成" else "标记为完成",
                tint = when {
                    todo.done -> MaterialTheme.appColors.success
                    todo.isOverdue -> MaterialTheme.appColors.danger
                    else -> MaterialTheme.appColors.textTertiary
                },
                modifier = Modifier
                    .size(24.dp)
                    .scale(checkScale)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onToggle,
                    ),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = todo.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (todo.done) {
                        MaterialTheme.appColors.textTertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (todo.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                todo.note?.takeIf { it.isNotBlank() }?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                MetaRow(todo)
            }

            if (todo.priority == Priority.HIGH && !todo.done) {
                Box(
                    modifier = Modifier
                        .padding(top = Spacing.xs)
                        .size(width = 3.dp, height = 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** 副信息行：截止时间 + 重复标记。都没有时整行不渲染，避免留出空高度。 */
@Composable
private fun MetaRow(todo: Todo) {
    val due = todo.dueAt
    val repeats = !todo.repeatRule.isNullOrBlank()
    if (due == null && !repeats) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (due != null) {
            Text(
                text = formatDueAt(due),
                style = MaterialTheme.typography.labelMedium,
                color = if (todo.isOverdue) {
                    MaterialTheme.appColors.danger
                } else {
                    MaterialTheme.appColors.textSecondary
                },
            )
        }
        if (repeats) {
            Icon(
                imageVector = Icons.Outlined.Repeat,
                contentDescription = "重复任务",
                tint = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
