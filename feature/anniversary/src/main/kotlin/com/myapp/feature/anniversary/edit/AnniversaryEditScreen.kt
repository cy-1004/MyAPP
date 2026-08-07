package com.myapp.feature.anniversary.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.LunarCalendar
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.anniversary.data.AnniversaryRepeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 新建 / 编辑纪念日（PRD 3.2）。
 *
 * 与待办编辑页同构：同一个页面承担新建与编辑，区别只在标题与「删除」按钮。
 */
@Composable
fun AnniversaryEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnniversaryEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.results.collect { onBack() }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(loaded) {
        if (loaded && draft.isNew) titleFocus.requestFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draft.isNew) "新建纪念日" else "编辑纪念日",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!draft.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.appColors.danger,
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = draft.canSave) {
                        Text("保存", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("这是什么日子") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus),
            )

            SectionLabel("日期")
            DateRow(
                date = draft.date,
                isLunar = draft.isLunar,
                onPickDate = { showDatePicker = true },
            )

            SectionLabel("历法")
            ChipRow(
                options = listOf(false, true),
                selected = draft.isLunar,
                labelOf = { if (it) "农历" else "公历" },
                onSelect = viewModel::updateLunar,
            )
            if (draft.isLunar) {
                // 农历纪念日每年对应的公历日期都不同，这里必须说清楚存的是哪一天，
                // 否则用户会以为自己填错了
                Hint("按上面这天对应的农历月日，每年重复")
            }

            SectionLabel("重复")
            ChipRow(
                options = AnniversaryRepeat.all,
                selected = draft.repeatType,
                labelOf = AnniversaryRepeat::label,
                onSelect = viewModel::updateRepeatType,
            )
            Hint(AnniversaryRepeat.hint(draft.repeatType))

            SectionLabel("提前提醒")
            ChipRow(
                options = REMIND_OPTIONS,
                selected = draft.remindDaysBefore,
                labelOf = ::remindLabel,
                onSelect = viewModel::updateRemindDays,
            )

            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注（可选）") },
                minLines = 2,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("置顶", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "首页卡片与桌面小组件默认显示这一个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }
                Switch(checked = draft.pinned, onCheckedChange = viewModel::updatePinned)
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            // DatePicker 用 UTC 零点毫秒，把本地日期搬到 UTC 同一天，否则东八区会偏一天
            initialSelectedDateMillis = draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.updateDate(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun DateRow(
    date: LocalDate,
    isLunar: Boolean,
    onPickDate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            QuickChip("选日期", onClick = onPickDate)
        }
        Text(
            text = buildString {
                append(date.format(AppFormatters.dateWithYear))
                if (isLunar && LunarCalendar.isSupported(date)) {
                    append("　农历")
                    append(LunarCalendar.fromSolar(date).format())
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.appColors.textSecondary,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.appColors.textTertiary,
    )
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = MaterialTheme.shapes.small,
    )
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

private val REMIND_OPTIONS = listOf(0, 1, 3, 7)

private fun remindLabel(days: Int): String = when (days) {
    0 -> "当天"
    else -> "提前 $days 天"
}
