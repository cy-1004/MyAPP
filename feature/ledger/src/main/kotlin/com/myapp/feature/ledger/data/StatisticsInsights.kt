package com.myapp.feature.ledger.data

import com.myapp.core.common.time.AppTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 统计页月度趋势的一个数据点：某自然月 + 该月支出合计（分）。 */
data class MonthlyExpensePoint(
    val month: LocalDate,
    val totalCents: Long,
)

private val YEAR_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

/**
 * 统计页派生逻辑（PRD 3.6.3：月度支出趋势 + 分类占比）。
 *
 * 月度趋势按**自然月**而不是预算周期算：这里回答的是「每个月大概花多少」，
 * 换发薪日会挪动预算周期的边界，但日历月份不该跟着变——用预算周期切月份，
 * 用户看着「8月」那一根柱子却包含了7月的钱，会觉得数字对不上。
 */
object StatisticsInsights {

    /** 最近 [count] 个自然月（含本月），按时间正序排列（最早的在前，趋势图从左到右递增）。 */
    fun lastMonths(count: Int, today: LocalDate = AppTime.today()): List<LocalDate> {
        require(count > 0) { "count must be positive, got $count" }
        val thisMonth = today.withDayOfMonth(1)
        return (count - 1 downTo 0).map { thisMonth.minusMonths(it.toLong()) }
    }

    /**
     * 把 DAO 按月分组的结果（[byYearMonth]，key 形如 "2026-08"）对齐到 [months]，
     * 没有支出的月份补 0——保证趋势图月份连续不断档，而不是数据缺失时柱子直接消失。
     */
    fun fillGaps(months: List<LocalDate>, byYearMonth: Map<String, Long>): List<MonthlyExpensePoint> =
        months.map { month ->
            MonthlyExpensePoint(month = month, totalCents = byYearMonth[month.format(YEAR_MONTH_FORMAT)] ?: 0L)
        }

    /** [month]（该月第一天）所在自然月的 [起, 止) 毫秒区间，供分类占比查询使用。 */
    fun monthRangeMillis(month: LocalDate): LongRange {
        val start = AppTime.run { month.toEpochMilliAtStartOfDay() }
        val endExclusive = AppTime.run { month.plusMonths(1).toEpochMilliAtStartOfDay() }
        return start until endExclusive
    }
}
