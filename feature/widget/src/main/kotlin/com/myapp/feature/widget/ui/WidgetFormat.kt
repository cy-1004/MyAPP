package com.myapp.feature.widget.ui

import java.time.LocalDate

/**
 * 小组件展示格式化。
 *
 * 金额逻辑与 :feature:ledger 的 LedgerFormat 同源，但 widget 不能依赖 feature
 * （PRD 4.7.1），这里保留一份最小的分 -> 元文本。
 */
private val WEEKDAYS = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 分 -> 元文本：2390 -> "23.90"，-2390 -> "-23.90"。 */
fun Long.yuanText(): String {
    val sign = if (this < 0) "-" else ""
    val abs = if (this < 0) -this else this
    val yuan = abs / 100
    val fen = abs % 100
    val fenStr = if (fen in 0L..9L) "0$fen" else fen.toString()
    return "$sign$yuan.$fenStr"
}

/** 带人民币符号：2390 -> "￥23.90"。 */
fun Long.yuanWithSymbol(): String = "￥${yuanText()}"

/** 千分位分组：1523450 -> "￥15,234.50"。用于预算/剩余这类可能到千元的数字。 */
fun Long.yuanGrouped(): String {
    val sign = if (this < 0) "-" else ""
    val abs = if (this < 0) -this else this
    val yuan = abs / 100
    val fen = abs % 100
    val grouped = yuan.toString().reversed().chunked(3).joinToString(",").reversed()
    val fenStr = if (fen in 0L..9L) "0$fen" else fen.toString()
    return "￥$sign$grouped.$fenStr"
}

/** 星期中文：「周一」..「周日」。 */
fun LocalDate.weekdayCn(): String = WEEKDAYS[dayOfWeek.value - 1]
