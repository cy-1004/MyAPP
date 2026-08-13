package com.myapp.core.common.time

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * BudgetCycle 周期边界回归（PRD 3.6.2）。
 *
 * 周期算错一天，余额就和银行对不上；用 5 条覆盖跨月 / 边界含 / 闰年 / 自然月。
 * 全部用 UTC 固定时区，避免 CI 跑在不同时区下结果漂移。
 *
 * 注意 LongRange 是 `start until endExclusive`，range.last = endExclusive - 1ms，
 * 测试里要还原「endExclusive 那天」得用 range.last + 1。
 */
class BudgetCycleTest {

    private val savedZone = AppTime.zone

    @Before
    fun fixZone() {
        AppTime.zone = ZoneOffset.UTC
    }

    @After
    fun restoreZone() {
        AppTime.zone = savedZone
    }

    @Test
    fun `cycleStartDay=10 today=8月15日 周期为本月10日到下月10日`() {
        val range = BudgetCycle.currentCycleRange(cycleStartDay = 10, today = LocalDate.of(2026, 8, 15))
        val start = with(AppTime) { range.first.toLocalDate() }
        val endExclusive = with(AppTime) { (range.last + 1).toLocalDate() }
        assertEquals(LocalDate.of(2026, 8, 10), start)
        assertEquals(LocalDate.of(2026, 9, 10), endExclusive)
    }

    @Test
    fun `cycleStartDay=10 today=8月9日 周期回退到上月10日`() {
        val range = BudgetCycle.currentCycleRange(cycleStartDay = 10, today = LocalDate.of(2026, 8, 9))
        val start = with(AppTime) { range.first.toLocalDate() }
        val endExclusive = with(AppTime) { (range.last + 1).toLocalDate() }
        assertEquals(LocalDate.of(2026, 7, 10), start)
        assertEquals(LocalDate.of(2026, 8, 10), endExclusive)
    }

    @Test
    fun `cycleStartDay=10 today=8月10日 边界含 本期开始`() {
        val range = BudgetCycle.currentCycleRange(cycleStartDay = 10, today = LocalDate.of(2026, 8, 10))
        val start = with(AppTime) { range.first.toLocalDate() }
        val endExclusive = with(AppTime) { (range.last + 1).toLocalDate() }
        assertEquals(LocalDate.of(2026, 8, 10), start)
        assertEquals(LocalDate.of(2026, 9, 10), endExclusive)
    }

    @Test
    fun `cycleStartDay=28 today=2月28日 闰年 周期为2月28日到3月28日`() {
        // 2024 是闰年，2 月有 29 天；cycleStartDay=28 应当落在 2 月 28 日
        val range = BudgetCycle.currentCycleRange(cycleStartDay = 28, today = LocalDate.of(2024, 2, 28))
        val start = with(AppTime) { range.first.toLocalDate() }
        val endExclusive = with(AppTime) { (range.last + 1).toLocalDate() }
        assertEquals(LocalDate.of(2024, 2, 28), start)
        assertEquals(LocalDate.of(2024, 3, 28), endExclusive)
    }

    @Test
    fun `cycleStartDay=1 today=任意 周期为自然月`() {
        val range = BudgetCycle.currentCycleRange(cycleStartDay = 1, today = LocalDate.of(2026, 8, 15))
        val start = with(AppTime) { range.first.toLocalDate() }
        val endExclusive = with(AppTime) { (range.last + 1).toLocalDate() }
        assertEquals(LocalDate.of(2026, 8, 1), start)
        assertEquals(LocalDate.of(2026, 9, 1), endExclusive)
    }

    @Test
    fun `daysUntilNextCycleStart 跨月倒计时`() {
        // cycleStartDay=10, today=8/15 -> 下个发薪日 9/10，相距 26 天
        val days = BudgetCycle.daysUntilNextCycleStart(cycleStartDay = 10, today = LocalDate.of(2026, 8, 15))
        assertEquals(26L, days)
    }

    @Test
    fun `daysUntilNextCycleStart 当月未到发薪日 倒计本月`() {
        // cycleStartDay=10, today=8/5 -> 本月发薪日 8/10，相距 5 天
        val days = BudgetCycle.daysUntilNextCycleStart(cycleStartDay = 10, today = LocalDate.of(2026, 8, 5))
        assertEquals(5L, days)
    }

    @Test
    fun `recentCycleRanges 正序返回且最后一期是当前期`() {
        val today = LocalDate.of(2026, 8, 15)
        val ranges = BudgetCycle.recentCycleRanges(cycleStartDay = 10, count = 12, today = today)
        assertEquals(12, ranges.size)
        assertEquals(BudgetCycle.currentCycleRange(10, today), ranges.last())
        // 最旧的一期：当前期起点 8/10 往前推 11 个月 -> 2025/9/10
        val firstStart = AppTime.run { ranges.first().first.toLocalDate() }
        assertEquals(LocalDate.of(2025, 9, 10), firstStart)
    }

    @Test
    fun `recentCycleRanges 首尾相接不留空档不重叠`() {
        val ranges = BudgetCycle.recentCycleRanges(
            cycleStartDay = 10,
            count = 12,
            today = LocalDate.of(2026, 8, 15),
        )
        // 上一期的 endExclusive 必须正好是下一期的 start，否则柱子之间会漏账或重复计账
        ranges.zipWithNext { prev, next ->
            assertEquals(prev.last + 1, next.first)
        }
    }

    @Test
    fun `recentCycleRanges 逐月推进 跨年且每期天数随月份变`() {
        val ranges = BudgetCycle.recentCycleRanges(
            cycleStartDay = 10,
            count = 6,
            today = LocalDate.of(2026, 3, 20),
        )
        val startDates = ranges.map { AppTime.run { it.first.toLocalDate() } }
        assertEquals(
            listOf(
                LocalDate.of(2025, 10, 10),
                LocalDate.of(2025, 11, 10),
                LocalDate.of(2025, 12, 10),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 3, 10),
            ),
            startDates,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recentCycleRanges count 为 0 抛异常`() {
        BudgetCycle.recentCycleRanges(cycleStartDay = 10, count = 0, today = LocalDate.of(2026, 8, 15))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cycleStartDay 超出 28 抛异常`() {
        BudgetCycle.currentCycleRange(cycleStartDay = 29, today = LocalDate.of(2026, 8, 15))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cycleStartDay 小于 1 抛异常`() {
        BudgetCycle.currentCycleRange(cycleStartDay = 0, today = LocalDate.of(2026, 8, 15))
    }
}
