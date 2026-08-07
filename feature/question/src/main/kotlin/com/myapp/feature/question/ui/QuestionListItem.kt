package com.myapp.feature.question.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myapp.core.common.time.asRelativeText
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.question.data.Question
import com.myapp.feature.question.data.QuestionStatus

/**
 * 列表里的一条疑问。
 *
 * 视觉与 [com.myapp.feature.note.ui.NoteListItem] 同口径：
 * 1px 描边而非阴影；一屏只允许一个 Accent 焦点，状态只用一个小 icon 暗示。
 *
 * 标题取 content 首行非空文本，摘要取剩余行；
 * 标签最多显示 3 个 + "+N"；状态 icon 在右侧（OPEN=问号 / RESOLVED=对勾 / ARCHIVED=归档）。
 */
@Composable
fun QuestionListItem(
    question: Question,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = firstLine(question.content)
    val summary = question.content.lineSequence()
        .drop(1)
        .joinToString("\n")
        .trim()
        .ifBlank { null }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.appColors.border),
    ) {
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                question.context?.takeIf { it.isNotBlank() }?.let { ctx ->
                    Text(
                        text = ctx,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (question.tags.isNotEmpty()) {
                    TagChips(tags = question.tags)
                }

                Text(
                    text = (question.resolvedAt ?: question.updatedAt).asRelativeText(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textTertiary,
                )
            }

            StatusIcon(question.status)
        }
    }
}

@Composable
private fun StatusIcon(status: QuestionStatus) {
    val (icon, tint, desc) = when (status) {
        QuestionStatus.OPEN -> Triple(Icons.AutoMirrored.Outlined.HelpOutline, MaterialTheme.appColors.textTertiary, "待解决")
        QuestionStatus.RESOLVED -> Triple(Icons.Outlined.CheckCircle, MaterialTheme.appColors.textSecondary, "已解决")
        QuestionStatus.ARCHIVED -> Triple(Icons.Outlined.Archive, MaterialTheme.appColors.textTertiary, "已归档")
    }
    Icon(
        imageVector = icon,
        contentDescription = desc,
        tint = tint,
        modifier = Modifier.size(18.dp),
    )
}

@Composable
private fun TagChips(tags: List<String>) {
    val visible = tags.take(MAX_TAGS)
    val remaining = tags.size - visible.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        visible.forEach { tag ->
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.appColors.textSecondary,
                ),
            )
        }
        if (remaining > 0) {
            Text(
                text = "+$remaining",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** 取首行非空文本作标题。 */
private fun firstLine(content: String): String =
    content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""

private const val MAX_TAGS = 3
