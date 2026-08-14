package com.myapp.feature.period.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.result.Result
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.CardSkeleton
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.TabularNumbers
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.period.data.PeriodDayLog
import com.myapp.feature.period.data.PeriodRecord
import com.myapp.feature.period.data.PeriodState
import com.myapp.feature.period.data.PeriodStatus
import com.myapp.feature.period.data.computePhases
import com.myapp.feature.period.data.phaseOf
import com.myapp.core.network.deepseek.DeepSeekPricing
import com.myapp.feature.period.ui.DayLogDialog
import com.myapp.feature.period.ui.PeriodAiCard
import com.myapp.feature.period.ui.DayMark
import com.myapp.feature.period.ui.MonthCalendar
import com.myapp.feature.period.ui.durationText
import com.myapp.feature.period.ui.explanation
import com.myapp.feature.period.ui.headline
import com.myapp.feature.period.ui.markOf
import com.myapp.feature.period.ui.monthMarks
import com.myapp.feature.period.ui.monthPhases
import com.myapp.feature.period.ui.rangeText
import com.myapp.feature.period.ui.todayPhaseText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 经期日历（PRD 3.2）。
 *
 * 页面的主行动只有一个：记录开始 / 记录结束。
 * 「新增一条记录 ≤ 3 次点击」的验收标准靠这个按钮兑现——
 * 首页卡片进来（1）+ 点按钮（2），实际两步就完成了。
 */
@Composable
fun PeriodScreen(
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeriodViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dayLogs by viewModel.dayLogs.collectAsStateWithLifecycle()
    val ai by viewModel.ai.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val today = remember { AppTime.today() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var dayAction by remember { mutableStateOf<LocalDate?>(null) }
    var dayLogEditing by remember { mutableStateOf<LocalDate?>(null) }

    // 峰谷判定按进页面那一刻算一次。逐秒重算没有意义——真跨过边界时用户重进一次即可，
    // 而一个每秒重组的时钟会把整页的重组频率抬起来
    val now = remember { Instant.now() }
    val peak = remember(now) { DeepSeekPricing.isPeak(now) }
    val peakEndsAt = remember(now, peak) {
        if (!peak) {
            null
        } else {
            DeepSeekPricing.nextOffPeakStart(now)
                .atZone(ZoneId.systemDefault())
                .format(AppFormatters.time)
        }
    }
    /** 非空表示正在等用户确认「峰价时段仍然分析」，值是这次要不要强制刷新。 */
    var peakConfirm by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = "已删除 ${event.startDate.format(AppFormatters.date)} 的记录",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete(event.id)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("经期", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        when (val s = state) {
            is Result.Loading -> Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            ) {
                CardSkeleton()
            }

            is Result.Error -> EmptyState(
                text = "记录加载失败了",
                modifier = Modifier.padding(innerPadding),
            )

            is Result.Success -> LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.xl,
                    end = Spacing.xl,
                    top = Spacing.sm,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "status") {
                    StatusCard(
                        state = s.data,
                        onRecordStart = { viewModel.recordStart() },
                        onRecordEnd = { viewModel.recordEnd() },
                    )
                }

                item(key = "calendar") {
                    AppCard {
                        val marks = remember(month, s.data) {
                            monthMarks(month, s.data.records, s.data.predictedRange)
                        }
                        val phases = remember(s.data) { computePhases(s.data) }
                        val phaseMarks = remember(month, phases) { monthPhases(month, phases) }
                        MonthCalendar(
                            month = month,
                            marks = marks,
                            today = today,
                            onMonthChange = { month = it },
                            onDayClick = { dayAction = it },
                            phases = phaseMarks,
                            loggedDays = dayLogs.keys,
                        )
                        Legend(showPhases = phases != null)
                        if (phases == null) {
                            Text(
                                text = "样本不足，暂不推算排卵期——再记两三次经期就会显示",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.appColors.textSecondary,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        } else {
                            Text(
                                text = "排卵期与黄体期是按平均周期推算的，仅供参考，" +
                                    "不能作为避孕或备孕的依据",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.appColors.textSecondary,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                        }
                    }
                }

                item(key = "ai") {
                    PeriodAiCard(
                        state = ai,
                        peak = peak,
                        peakEndsAt = peakEndsAt,
                        onAnalyze = { force ->
                            // 峰价时不直接发：费用翻倍，得让用户知道了再点一次（PRD 3.14）
                            if (peak) peakConfirm = force else viewModel.analyze(force)
                        },
                        onOpenSettings = onOpenAiSettings,
                    )
                }

                if (s.data.records.isNotEmpty()) {
                    item(key = "history-title") {
                        Text(
                            text = "历史记录",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.appColors.textSecondary,
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                    items(items = s.data.records, key = { it.id }) { record ->
                        RecordRow(
                            record = record,
                            onDelete = { viewModel.delete(record.id, record.startDate) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    peakConfirm?.let { force ->
        AlertDialog(
            onDismissRequest = { peakConfirm = null },
            title = { Text("现在是峰价时段") },
            text = {
                Text(
                    "这个时段调用 DeepSeek 的费用是谷价的两倍" +
                        (peakEndsAt?.let { "，$it 之后转为谷价" } ?: "") +
                        "。要现在就分析吗？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    peakConfirm = null
                    viewModel.analyze(force)
                }) { Text("仍然分析") }
            },
            dismissButton = {
                TextButton(onClick = { peakConfirm = null }) { Text("等等再说") }
            },
        )
    }

    dayAction?.let { date ->
        DayActionDialog(
            date = date,
            state = state,
            dayLog = dayLogs[date],
            onDismiss = { dayAction = null },
            onRecordStart = {
                viewModel.recordStart(date)
                dayAction = null
            },
            onRecordEnd = {
                viewModel.recordEnd(date)
                dayAction = null
            },
            onEditDayLog = {
                dayAction = null
                dayLogEditing = date
            },
        )
    }

    dayLogEditing?.let { date ->
        DayLogDialog(
            date = date,
            existing = dayLogs[date],
            onDismiss = { dayLogEditing = null },
            onSave = {
                viewModel.saveDayLog(it)
                dayLogEditing = null
            },
            onDelete = {
                viewModel.deleteDayLog(date)
                dayLogEditing = null
            },
        )
    }
}

@Composable
private fun StatusCard(
    state: PeriodState,
    onRecordStart: () -> Unit,
    onRecordEnd: () -> Unit,
) {
    AppCard {
        Text(
            text = state.status.headline(),
            // 状态是整页唯一的焦点，用衬线大号 + 等宽数字
            style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.explanation(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.textSecondary,
        )
        // 卵泡期/黄体期在月历上只有一条细横线，这里补一句话说清今天在哪一段
        state.todayPhaseText(AppTime.today())?.let { phaseText ->
            Text(
                text = phaseText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
        }

        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 进行中就只给「结束」，不给「开始」——同时给两个按钮等于让用户替程序做判断
            if (state.status is PeriodStatus.Ongoing) {
                Button(onClick = onRecordEnd, shape = MaterialTheme.shapes.small) {
                    Text("今天结束了")
                }
            } else {
                Button(onClick = onRecordStart, shape = MaterialTheme.shapes.small) {
                    Text("今天开始了")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(showPhases: Boolean) {
    // 图例必须跟着月历上真实画出来的东西走：不画分期的时候列出分期只会让人找不到
    FlowRow(
        modifier = Modifier.padding(top = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        LegendItem(color = MaterialTheme.colorScheme.primary, label = "开始日")
        LegendItem(color = MaterialTheme.colorScheme.primaryContainer, label = "经期")
        LegendItem(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), label = "预测")
        if (showPhases) {
            LegendItem(
                color = MaterialTheme.colorScheme.tertiary,
                label = "排卵日（实线圈）",
            )
            LegendItem(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                label = "易孕期",
            )
            LegendItem(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                label = "黄体期（下横线）",
            )
        }
        LegendItem(color = MaterialTheme.appColors.warning, label = "有今日记录")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.textSecondary,
        )
    }
}

@Composable
private fun RecordRow(
    record: PeriodRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.rangeText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                record.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }
            }
            Text(
                text = record.durationText(),
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                color = MaterialTheme.appColors.textSecondary,
            )
            TextButton(onClick = onDelete) {
                Text(
                    "删除",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.danger,
                )
            }
        }
    }
}

@Composable
private fun DayActionDialog(
    date: LocalDate,
    state: Result<PeriodState>,
    dayLog: PeriodDayLog?,
    onDismiss: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordEnd: () -> Unit,
    onEditDayLog: () -> Unit,
) {
    val data = (state as? Result.Success)?.data
    val mark = data?.let { markOf(date, it.records, it.predictedRange) }
    val phase = data?.let { phaseOf(date, computePhases(it)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(date.format(AppFormatters.dateWithYear)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = when (mark) {
                        DayMark.ActualStart -> "这天是一次经期的开始"
                        DayMark.Actual -> "这天在一次经期区间内"
                        DayMark.Predicted -> "这天在预测区间内，还没有实际记录"
                        // 没有经期标记时才说分期：经期本身比「推算的第几期」确定得多
                        null -> phase?.let { "推算处于${it.label}" } ?: "还没有记录"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                // 已经记过就把内容摆出来，省得为了看一眼还要再点进去
                dayLog?.let { log ->
                    Text(
                        text = buildString {
                            append(log.tags.joinToString("、") { it.label })
                            if (log.note.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(log.note)
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }
                TextButton(
                    onClick = onEditDayLog,
                    contentPadding = PaddingValues(horizontal = Spacing.xs),
                ) {
                    Text(if (dayLog == null) "记录今日情况" else "修改今日情况")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRecordStart) { Text("记为开始") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(onClick = onRecordEnd, shape = MaterialTheme.shapes.small) {
                    Text("记为结束")
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
