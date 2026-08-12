package com.myapp.feature.ledger.ui

import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.AppFormatters
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 金额显示格式化。
 *
 * 分（Long）-> 元文本：2390 -> "23.90"，2300 -> "23.00"，230 -> "2.30"。
 * 不用 BigDecimal/String.format 是为了零分配；金额量级在 Long 范围内够用。
 */
fun Long.yuanText(): String {
    val abs = if (this < 0) -this else this
    val sign = if (this < 0) "-" else ""
    val yuan = abs / 100
    val fen = abs % 100
    val fenStr = when (fen) {
        in 0L..9L -> "0$fen"
        else -> fen.toString()
    }
    return "$sign$yuan.$fenStr"
}

/** 带人民币符号：2390 -> "￥23.90"。 */
fun Long.yuanWithSymbol(): String = "￥${yuanText()}"

/**
 * 周期区间文本：start..endExclusive（不含）-> "8月10日 - 9月9日"。
 * endExclusive 是下个周期起点的零点，减 1 天得到本期最后一天。
 */
fun cycleRangeText(startMillis: Long, endExclusiveMillis: Long): String {
    val startDate = AppTime.run { startMillis.toLocalDate() }
    val endDate = AppTime.run { (endExclusiveMillis - 1).toLocalDate() }
    val sameYear = startDate.year == endDate.year
    val sameMonth = sameYear && startDate.month == endDate.month
    return when {
        sameMonth -> "${startDate.format(AppFormatters.dateWithYear)} - ${endDate.dayOfMonth}日"
        sameYear -> "${startDate.format(AppFormatters.date)} - ${endDate.format(AppFormatters.date)}"
        else -> "${startDate.format(AppFormatters.dateWithYear)} - ${endDate.format(AppFormatters.dateWithYear)}"
    }
}

private val MONTH_ONLY_FORMAT = DateTimeFormatter.ofPattern("M月")
private val MONTH_WITH_YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月")

/** 趋势图柱子下的短标签："2026-08-01" -> "8月"。 */
fun LocalDate.monthLabelText(): String = format(MONTH_ONLY_FORMAT)

/** 统计页月份选择器标题："2026-08-01" -> "2026年8月"。 */
fun LocalDate.monthTitleText(): String = format(MONTH_WITH_YEAR_FORMAT)
