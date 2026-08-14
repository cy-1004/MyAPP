package com.myapp.feature.period.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.TabularNumbers
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.period.data.CyclePhases
import com.myapp.feature.period.data.PeriodRecord
import com.myapp.feature.period.data.PhaseMark
import com.myapp.feature.period.data.phaseOf
import java.time.LocalDate
import java.time.YearMonth

/** 某一天在日历上的标记。 */
enum class DayMark {
    /** 实际记录的开始日。 */
    ActualStart,

    /** 实际记录区间内的其他天。 */
    Actual,

    /** 预测区间。用虚线圈表示，与实际记录明确区分。 */
    Predicted,
}

/**
 * 月历。
 *
 * 实际记录用实心色块、预测用虚线圈——**这两者绝不能长得像**，
 * 否则用户会把预测当成已发生的事实（PRD 3.2）。
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    marks: Map<LocalDate, DayMark>,
    today: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** 分期标记（PRD 3.2）。预测不可靠时上层传空表，日历上就什么都不画。 */
    phases: Map<LocalDate, PhaseMark> = emptyMap(),
    /** 有身体情况记录的日子，右上角打点。 */
    loggedDays: Set<LocalDate> = emptySet(),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MonthHeader(month = month, onMonthChange = onMonthChange)
        WeekdayHeader()
        MonthGrid(
            month = month,
            marks = marks,
            phases = phases,
            loggedDays = loggedDays,
            today = today,
            onMonthChange = onMonthChange,
            onDayClick = onDayClick,
        )
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一月")
        }
        Text(
            text = "${month.year} 年 ${month.monthValue} 月",
            style = MaterialTheme.typography.titleMedium.merge(TabularNumbers),
            color = MaterialTheme.colorScheme.onSurface,
        )
        IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一月")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAYS.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = Spacing.xs),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    marks: Map<LocalDate, DayMark>,
    phases: Map<LocalDate, PhaseMark>,
    loggedDays: Set<LocalDate>,
    today: LocalDate,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit,
) {
    // 周一为一周之首（中文习惯）：dayOfWeek 的 ISO 值 1=周一，正好可以直接减 1
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val totalCells = leadingBlanks + month.lengthOfMonth()
    val rows = (totalCells + 6) / 7

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(month) {
                // 水平滑动切月。累计量声明在这里而不是 composable 作用域里——
                // 重组会重建 composable 的局部变量，手势中途就丢了
                var dragged = 0f
                // 阈值 60px：太小会在竖向滚动时误触
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = {
                        when {
                            dragged > 60f -> onMonthChange(month.minusMonths(1))
                            dragged < -60f -> onMonthChange(month.plusMonths(1))
                        }
                    },
                    onHorizontalDrag = { _, delta -> dragged += delta },
                )
            },
    ) {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val cellIndex = row * 7 + column
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth in 1..month.lengthOfMonth()) {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            mark = marks[date],
                            phase = phases[date],
                            hasLog = date in loggedDays,
                            isToday = date == today,
                            isFuture = date.isAfter(today),
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    mark: DayMark?,
    phase: PhaseMark?,
    hasLog: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val dashedColor = accent.copy(alpha = 0.55f)

    // 分期用第二套颜色（tertiary），与经期的 primary 拉开；
    // 但**不能只靠颜色**——排卵日另有实线圈、黄体期另有底部横条，
    // 深色模式和色觉障碍下形状才是能分辨的那一维（PRD 3.2）
    val phaseAccent = MaterialTheme.colorScheme.tertiary

    val textColor = when {
        mark == DayMark.ActualStart -> MaterialTheme.colorScheme.onPrimary
        mark == DayMark.Actual -> MaterialTheme.colorScheme.onPrimaryContainer
        isFuture -> MaterialTheme.appColors.textTertiary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 分期垫在最底层：经期标记（实心块/虚线圈）永远盖在它上面——
        // 已经发生的事实优先于推算出来的分期
        if (mark == null && phase != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .then(
                        when (phase) {
                            PhaseMark.Ovulation -> Modifier
                                .background(phaseAccent.copy(alpha = 0.18f), CircleShape)
                                .drawBehind {
                                    drawCircle(
                                        color = phaseAccent,
                                        radius = size.minDimension / 2 - 1.dp.toPx(),
                                        style = Stroke(width = 1.5.dp.toPx()),
                                    )
                                }

                            PhaseMark.Fertile ->
                                Modifier.background(phaseAccent.copy(alpha = 0.18f), CircleShape)

                            // 黄体期与卵泡期不铺底色：整月都铺满会变成一张色块图，
                            // 反而看不出经期在哪。只在数字下方给一条细横线
                            PhaseMark.Luteal, PhaseMark.Follicular -> Modifier
                        },
                    ),
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .then(
                    when (mark) {
                        // 开始日用实心强调色点出来，一眼能数出「这是第几次」
                        DayMark.ActualStart -> Modifier.background(accent, CircleShape)
                        DayMark.Actual -> Modifier.background(container, CircleShape)
                        DayMark.Predicted -> Modifier.drawBehind {
                            drawCircle(
                                color = dashedColor,
                                radius = size.minDimension / 2 - 1.dp.toPx(),
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                                ),
                            )
                        }

                        null -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge.merge(TabularNumbers),
                color = textColor,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }

        // 黄体期/卵泡期：数字下方一条细横线。两者用同一形状、不同深浅——
        // 它们是「当前处于哪一段」的辅助信息，不该比经期本身更抢眼
        if (mark == null && (phase == PhaseMark.Luteal || phase == PhaseMark.Follicular)) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 7.dp)
                    .size(width = 14.dp, height = 2.dp)
                    .background(
                        phaseAccent.copy(alpha = if (phase == PhaseMark.Luteal) 0.7f else 0.3f),
                    ),
            )
        }

        // 有身体情况记录：右上角一个小点。位置刻意与「今天」的底部圆点错开
        if (hasLog) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(5.dp)
                    .background(MaterialTheme.appColors.warning, CircleShape),
            )
        }

        // 今天：底部一个小圆点。用描边或换底色都会和经期标记打架
        if (isToday) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(3.dp)
                    .background(
                        if (mark == DayMark.ActualStart) Color.White else accent,
                        CircleShape,
                    ),
            )
        }
    }
}

/**
 * 计算某一天的标记。
 *
 * 实际记录优先于预测：已经发生的事实不该被预测覆盖掉。
 */
fun markOf(
    date: LocalDate,
    records: List<PeriodRecord>,
    predicted: ClosedRange<LocalDate>?,
): DayMark? {
    records.forEach { record ->
        if (date == record.startDate) return DayMark.ActualStart
        if (record.covers(date)) return DayMark.Actual
    }
    if (predicted != null && date in predicted) return DayMark.Predicted
    return null
}

/** 一整个月的标记表，供 [MonthCalendar] 直接查。 */
fun monthMarks(
    month: YearMonth,
    records: List<PeriodRecord>,
    predicted: ClosedRange<LocalDate>?,
): Map<LocalDate, DayMark> = buildMap {
    (1..month.lengthOfMonth()).forEach { day ->
        val date = month.atDay(day)
        markOf(date, records, predicted)?.let { put(date, it) }
    }
}

/** 一整个月的分期表。[phases] 为 null（预测不可靠）时返回空表，日历上什么都不画。 */
fun monthPhases(month: YearMonth, phases: CyclePhases?): Map<LocalDate, PhaseMark> = buildMap {
    if (phases == null) return@buildMap
    (1..month.lengthOfMonth()).forEach { day ->
        val date = month.atDay(day)
        phaseOf(date, phases)?.let { put(date, it) }
    }
}

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
