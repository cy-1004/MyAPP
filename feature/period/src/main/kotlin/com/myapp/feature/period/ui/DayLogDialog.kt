package com.myapp.feature.period.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.period.data.DayLogTag
import com.myapp.feature.period.data.DayLogTagGroup
import com.myapp.feature.period.data.PeriodDayLog
import java.time.LocalDate

/**
 * 记录某一天的身体情况（PRD 3.2「每日异常记录」）。
 *
 * 预置标签负责「可统计」的那一半，自由文本负责兜底的那一半。
 * 两样都清空再保存 = 删掉这一天的记录，所以底部的「删除」只是个快捷方式，
 * 不是另一条独立路径。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayLogDialog(
    date: LocalDate,
    existing: PeriodDayLog?,
    onDismiss: () -> Unit,
    onSave: (PeriodDayLog) -> Unit,
    onDelete: () -> Unit,
) {
    val selected = remember(date, existing) { existing?.tags.orEmpty().toMutableStateList() }
    var note by remember(date, existing) { mutableStateOf(existing?.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.format(AppFormatters.dateWithYear)} 的情况") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                DayLogTagGroup.entries.forEach { group ->
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        DayLogTag.entries.filter { it.group == group }.forEach { tag ->
                            FilterChip(
                                selected = tag in selected,
                                onClick = {
                                    if (tag in selected) selected.remove(tag) else selected.add(tag)
                                },
                                label = {
                                    Text(tag.label, style = MaterialTheme.typography.labelLarge)
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("还想记点什么（可留空）") },
                    minLines = 2,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(PeriodDayLog(date = date, tags = selected.toList(), note = note))
                },
            ) { Text("保存") }
        },
        dismissButton = {
            if (existing != null) {
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.appColors.danger)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
