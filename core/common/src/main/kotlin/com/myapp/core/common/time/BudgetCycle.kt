package com.myapp.core.common.time

import java.time.LocalDate

/**
 * 预算周期计算（PRD 3.6.2）。
 *
 * 「本期」= 从上一个 cycleStartDay 到下一个 cycleStartDay 前一天的区间，
 * **不是自然月**。钱是发薪日（默认 10 号）进账的，按自然月算余额会在月初和月末
 * 各错一次，这个数字就没有指导意义了。
 *
 * 周期边界一律用本地时区的 epochMilli 区间 [start, endExclusive)，
 * 与其他模块的按天查询保持同一套口径（PRD 3.6.2）。
 *
 * cycleStartDay 上限 28：29~31 号在部分月份不存在，落到 28 之后要额外定义
 * 「没有这天怎么办」，收益不值得这份复杂度。真发薪在 30 号的话设 28 号误差只有两天。
 *
 * 放 :core:common 而不是 :feature:ledger：桌面小组件也要算「本期剩余」，
 * 而 feature 之间禁止互相依赖（PRD 4.7.1），周期计算是纯日期数学，归 core 更合适。
 */
object BudgetCycle {

    /**
     * 给定 [cycleStartDay]（1~28）与参考日期 [today]，返回当前周期的 epochMilli 区间。
     *
     * 区间是左闭右开：[cycleStart, cycleEndExclusive)。endExclusive 用 startOfMonthOfNextCycle
     * 的零点毫秒，这样 SQL `WHERE occurred_at >= :start AND occurred_at < :endExclusive`
     * 能正确覆盖整个周期。
     *
     * 边界规则：today 的日号 >= cycleStartDay 时，周期起点是本月 cycleStartDay；
     * 否则周期起点是上月 cycleStartDay。例：cycleStartDay=10，today=8/15 -> 周期 8/10..9/10；
     * today=8/9 -> 周期 7/10..8/10；today=8/10 -> 周期 8/10..9/10（边界含）。
     */
    fun currentCycleRange(
        cycleStartDay: Int,
        today: LocalDate = AppTime.today(),
    ): LongRange {
        require(cycleStartDay in 1..28) { "cycleStartDay must be 1..28, got $cycleStartDay" }
        val thisMonthCycleStart = today.withDayOfMonth(cycleStartDay)
        val cycleStartDate = if (today.dayOfMonth >= cycleStartDay) {
            thisMonthCycleStart
        } else {
            thisMonthCycleStart.minusMonths(1)
        }
        val cycleEndExclusiveDate = cycleStartDate.plusMonths(1)
        val start = cycleStartDate.toEpochMilliAtStartOfDay()
        val endExclusive = cycleEndExclusiveDate.toEpochMilliAtStartOfDay()
        return start until endExclusive
    }

    /**
     * 最近 [count] 期的区间，**按时间正序**（最旧的在前，当前周期在最后）。
     * 用于历史周期回顾（PRD 3.6.2 的近 12 期柱状图）。
     *
     * 全部用**当前的** [cycleStartDay] 往回推，不去还原「那一期当时的发薪日是几号」。
     * 理由：发薪日改过之后，用历史发薪日切出来的区间长度不一（还可能重叠或留空档），
     * 柱子之间就不可比了，而这张图的用途恰恰是横向比较。改发薪日是低频操作，
     * 用统一口径重新切一遍历史，比画一排互相对不齐的柱子更有意义。
     */
    fun recentCycleRanges(
        cycleStartDay: Int,
        count: Int,
        today: LocalDate = AppTime.today(),
    ): List<LongRange> {
        require(cycleStartDay in 1..28) { "cycleStartDay must be 1..28, got $cycleStartDay" }
        require(count > 0) { "count must be > 0, got $count" }
        val current = currentCycleRange(cycleStartDay, today)
        val currentStartDate = AppTime.run { current.first.toLocalDate() }
        // (count - 1) 期之前 → 当前期，逐月往前推。用 minusMonths 而不是减固定天数：
        // 每期天数 28~31 不等，减天数会逐期漂移。
        return (count - 1 downTo 0).map { back ->
            val startDate = currentStartDate.minusMonths(back.toLong())
            val start = AppTime.run { startDate.toEpochMilliAtStartOfDay() }
            val endExclusive = AppTime.run { startDate.plusMonths(1).toEpochMilliAtStartOfDay() }
            start until endExclusive
        }
    }

    /** 下一个发薪日距今的天数。用于「距 3 月 10 日还有 12 天」这类提示。 */
    fun daysUntilNextCycleStart(
        cycleStartDay: Int,
        today: LocalDate = AppTime.today(),
    ): Long {
        require(cycleStartDay in 1..28) { "cycleStartDay must be 1..28, got $cycleStartDay" }
        val thisMonthCycleStart = today.withDayOfMonth(cycleStartDay)
        val nextCycleStart = if (today.dayOfMonth >= cycleStartDay) {
            thisMonthCycleStart.plusMonths(1)
        } else {
            thisMonthCycleStart
        }
        return java.time.temporal.ChronoUnit.DAYS.between(today, nextCycleStart)
    }

    private fun LocalDate.toEpochMilliAtStartOfDay(): Long =
        AppTime.run { toEpochMilliAtStartOfDay() }
}
