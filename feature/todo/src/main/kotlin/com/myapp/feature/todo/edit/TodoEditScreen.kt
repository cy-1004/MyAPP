package com.myapp.feature.todo.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.time.AppTime
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.designsystem.component.bringIntoViewOnFocus
import com.myapp.feature.todo.data.Priority
import com.myapp.feature.todo.data.RepeatRule
import com.myapp.feature.todo.ui.ALL_DAY_END
import com.myapp.feature.todo.ui.formatDueAt
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * 新建 / 编辑待办（PRD 3.3）。
 *
 * 新建与编辑是同一个页面：字段完全一致，拆成两个页面只会让表单逻辑复制两份。
 * 区别只有标题文案和「删除」按钮是否出现。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodoEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()

    // 保存 / 删除完成后由 ViewModel 通知返回，避免页面自己猜什么时候写完了
    LaunchedEffect(Unit) {
        viewModel.results.collect { onBack() }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    // 新建时直接把光标放进标题——少一次点击，这是记事类应用的高频路径
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
                        text = if (draft.isNew) "新建待办" else "编辑待办",
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("要做什么") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocus)
                    .bringIntoViewOnFocus(),
            )

            // 完成备注（只读展示）：已完成态才有，未完成或新建态不显示。
            // 不可编辑 -- 它是「完成那一刻」的备注，编辑待办本身时不应改动它。
            // 先取到局部变量：displayCompletionNote 是别的模块里的 public 属性，
            // Kotlin 不对跨模块的 public 属性做智能转换（无法保证不是自定义 getter）。
            val completionNote = draft.displayCompletionNote
            if (!draft.isNew && !completionNote.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            text = "完成备注",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.appColors.textSecondary,
                        )
                        Text(
                            text = completionNote,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注（可选）") },
                minLines = 3,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus(),
            )

            SectionLabel("截止时间")
            DueAtRow(
                dueAt = draft.dueAt,
                onQuickPick = viewModel::updateDueAt,
                onPickDate = { showDatePicker = true },
                onPickTime = { showTimePicker = true },
            )

            SectionLabel("优先级")
            ChipRow(
                options = listOf(Priority.LOW, Priority.NORMAL, Priority.HIGH),
                selected = draft.priority,
                labelOf = Priority::label,
                onSelect = viewModel::updatePriority,
            )

            SectionLabel("重复")
            ChipRow(
                options = listOf(
                    RepeatRule.NONE,
                    RepeatRule.DAILY,
                    RepeatRule.WEEKDAYS,
                    RepeatRule.MONTHLY_INTERVAL,
                ),
                selected = draft.repeatRule,
                labelOf = { RepeatRule.describe(it) },
                onSelect = viewModel::updateRepeatRule,
            )
        }
    }

    if (showDatePicker) {
        val initial = draft.dueAt ?: AppTime.now()
        val pickerState = rememberDatePickerState(
            // DatePicker 用的是 UTC 零点毫秒，这里把本地日期搬到 UTC 同一天，
            // 否则东八区选出来的日期会整体偏一天
            initialSelectedDateMillis = with(AppTime) {
                initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            viewModel.updateDueAt(combine(date, timeOf(draft.dueAt)))
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

    if (showTimePicker) {
        val current = timeOf(draft.dueAt)
        val timeState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val date = draft.dueAt?.let { with(AppTime) { it.toLocalDate() } }
                            ?: AppTime.today()
                        viewModel.updateDueAt(
                            combine(date, LocalTime.of(timeState.hour, timeState.minute)),
                        )
                        showTimePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = timeState) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueAtRow(
    dueAt: Long?,
    onQuickPick: (Long?) -> Unit,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 高频选项做成一键，剩下的才走日期选择器——绝大多数待办落在今明两天
            QuickChip("今天") { onQuickPick(combine(AppTime.today(), DEFAULT_TIME)) }
            QuickChip("明天") { onQuickPick(combine(AppTime.today().plusDays(1), DEFAULT_TIME)) }
            QuickChip("选日期", onClick = onPickDate)
            if (dueAt != null) {
                QuickChip("选时刻", onClick = onPickTime)
                QuickChip("清除") { onQuickPick(null) }
            }
        }

        Text(
            text = dueAt?.let { formatDueAt(it) } ?: "无期限",
            style = MaterialTheme.typography.bodyMedium,
            color = if (dueAt == null) {
                MaterialTheme.appColors.textTertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = MaterialTheme.shapes.small,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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

/** 快捷选项的默认时刻：不指定时刻就当作「当天内」。 */
private val DEFAULT_TIME: LocalTime = ALL_DAY_END

private fun timeOf(dueAt: Long?): LocalTime =
    dueAt?.let { with(AppTime) { it.toLocalDateTime().toLocalTime() } } ?: DEFAULT_TIME

private fun combine(date: LocalDate, time: LocalTime): Long =
    with(AppTime) { date.atTime(time).toEpochMilli() }
