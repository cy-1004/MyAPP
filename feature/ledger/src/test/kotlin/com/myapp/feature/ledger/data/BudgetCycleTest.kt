package com.myapp.feature.ledger.data

import com.myapp.core.common.time.AppTime
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

    @Test(expected = IllegalArgumentException::class)
    fun `cycleStartDay 超出 28 抛异常`() {
        BudgetCycle.currentCycleRange(cycleStartDay = 29, today = LocalDate.of(2026, 8, 15))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cycleStartDay 小于 1 抛异常`() {
        BudgetCycle.currentCycleRange(cycleStartDay = 0, today = LocalDate.of(2026, 8, 15))
    }
}
