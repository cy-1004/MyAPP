package com.myapp.feature.note.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.myapp.core.common.time.asRelativeText
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.note.data.Note
import java.io.File

/**
 * 列表里的一条笔记。
 *
 * 视觉遵循 PRD 5.3：1px 描边而非阴影；一屏只允许一个 Accent 焦点，
 * 置顶只用一个小 icon 暗示，不整条染色。
 *
 * 标题取 [firstLine]（content 首行非空文本），摘要取剩余行；
 * 标签最多显示 3 个 + "+N"，多了用横向滚动避免换行；
 * 首张图缩略图 48dp 圆角，多于 1 张时右下角标 "×N"。
 */
@Composable
fun NoteListItem(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = firstLine(note.content)
    val summary = note.content.lineSequence()
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

                if (note.tags.isNotEmpty()) {
                    TagChips(tags = note.tags)
                }

                MetaRow(note)
            }

            // 置顶 icon：单独一列，与文字垂直对齐到顶部
            if (note.pinned) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = "已置顶",
                    tint = MaterialTheme.appColors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                note.images.firstOrNull()?.let {
                    FirstImage(
                        filesDir = LocalContext.current.filesDir,
                        relativePath = it,
                        count = note.images.size,
                    )
                }
            }
        }
    }
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

@Composable
private fun MetaRow(note: Note) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = note.updatedAt.asRelativeText(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.textTertiary,
        )
    }
}

@Composable
private fun FirstImage(filesDir: File, relativePath: String, count: Int) {
    Box(modifier = Modifier.size(48.dp)) {
        AsyncImage(
            model = File(filesDir, relativePath),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        if (count > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "×$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

private const val MAX_TAGS = 3
