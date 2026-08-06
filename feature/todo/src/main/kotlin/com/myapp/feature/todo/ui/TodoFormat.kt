package com.myapp.feature.todo.ui

import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import java.time.LocalTime

/** 「当天内」约定使用的时刻：23:59。 */
val ALL_DAY_END: LocalTime = LocalTime.of(23, 59)

/**
 * 截止时间的展示文案。
 *
 * 「今天 18:00」比「3月5日 18:00」有用得多——最近几天是绝大多数待办的落点，
 * 让人不用在脑子里做一次日期减法。
 */
fun formatDueAt(dueAt: Long): String = with(AppTime) {
    val dateTime = dueAt.toLocalDateTime()
    val date = dateTime.toLocalDate()
    val days = daysFromToday(date)

    val dayLabel = when (days) {
        0L -> "今天"
        1L -> "明天"
        2L -> "后天"
        -1L -> "昨天"
        else -> if (date.year == today().year) {
            date.format(AppFormatters.date)
        } else {
            date.format(AppFormatters.dateWithYear)
        }
    }

    // 23:59 与 00:00 通常表示「当天内」而不是精确到点，显示时刻反而是噪音
    val time = dateTime.toLocalTime()
    if (time == LocalTime.MIDNIGHT || time == ALL_DAY_END) {
        dayLabel
    } else {
        "$dayLabel ${dateTime.format(AppFormatters.time)}"
    }
}
