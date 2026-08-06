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
import com.myapp.feature.period.data.PeriodRecord
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
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MonthHeader(month = month, onMonthChange = onMonthChange)
        WeekdayHeader()
        MonthGrid(
            month = month,
            marks = marks,
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
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer
    val dashedColor = accent.copy(alpha = 0.55f)

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

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
