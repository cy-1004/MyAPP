package com.myapp.feature.ledger.data

import com.myapp.core.common.time.BudgetCycle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预算视图派生指标测试（PRD 3.6.2）。
 *
 * 这些数字用户是拿来做花钱决策的：显示「日均还能花 ￥120」而实际只剩 ￥30，
 * 会直接让人超支——比不显示更糟，所以边界一条条钉死。
 *
 * 周期区间统一走 [BudgetCycle.currentCycleRange]，与生产代码同一个入口，
 * 避免测试里手搓区间跟实际口径漂移。
 */
class BudgetInsightsTest {

    /** 发薪日 10 号，参考日 8/15 -> 周期 8/10..9/9，共 31 天。 */
    private fun cycle(cycleStartDay: Int = 10, today: LocalDate = LocalDate.of(2026, 8, 15)) =
        BudgetCycle.currentCycleRange(cycleStartDay, today)

    @Test
    fun cycleProgress_countsTodayAsElapsedAndRemaining() {
        val range = cycle()
        val p = BudgetInsights.cycleProgress(range.first, range.last + 1, LocalDate.of(2026, 8, 15))
        assertEquals(31, p.totalDays)          // 8/10..9/9
        assertEquals(6, p.elapsedDays)         // 8/10 是第 1 天，8/15 是第 6 天
        assertEquals(26, p.remainingDays)      // 含今天：31 - 6 + 1
    }

    @Test
    fun cycleProgress_firstDay() {
        val today = LocalDate.of(2026, 8, 10)
        val range = cycle(today = today)
        val p = BudgetInsights.cycleProgress(range.first, range.last + 1, today)
        assertEquals(1, p.elapsedDays)
        assertEquals(p.totalDays, p.remainingDays)
    }

    /** 周期最后一天：剩余天数是 1（今天还能花），不能是 0。 */
    @Test
    fun cycleProgress_lastDay_remainsOne() {
        val today = LocalDate.of(2026, 9, 9)
        val range = cycle(today = today)
        val p = BudgetInsights.cycleProgress(range.first, range.last + 1, today)
        assertEquals(p.totalDays, p.elapsedDays)
        assertEquals(1, p.remainingDays)
    }

    /** 进程跨天存活时 today 可能已经跑出当初算的区间，夹到边界不能出负数。 */
    @Test
    fun cycleProgress_todayOutsideRange_isClamped() {
        val range = cycle(today = LocalDate.of(2026, 8, 15))
        val after = BudgetInsights.cycleProgress(range.first, range.last + 1, LocalDate.of(2026, 12, 1))
        assertEquals(after.totalDays, after.elapsedDays)
        assertEquals(1, after.remainingDays)

        val before = BudgetInsights.cycleProgress(range.first, range.last + 1, LocalDate.of(2026, 1, 1))
        assertEquals(1, before.elapsedDays)
        assertEquals(before.totalDays, before.remainingDays)
    }

    /** 2 月短周期：发薪日 1 号、2 月 -> 28 天（2026 不是闰年）。 */
    @Test
    fun cycleProgress_shortMonth() {
        val today = LocalDate.of(2026, 2, 20)
        val range = BudgetCycle.currentCycleRange(1, today)
        val p = BudgetInsights.cycleProgress(range.first, range.last + 1, today)
        assertEquals(28, p.totalDays)
        assertEquals(20, p.elapsedDays)
        assertEquals(9, p.remainingDays)
    }

    @Test
    fun dailyAvailable_dividesRemainingByRemainingDays() {
        assertEquals(100_00L, BudgetInsights.dailyAvailableCents(1000_00L, 10))
    }

    /** 已超支时返回 0：「日均还能花 -￥50」没有指导意义。 */
    @Test
    fun dailyAvailable_whenOverspent_isZero() {
        assertEquals(0L, BudgetInsights.dailyAvailableCents(-500_00L, 10))
        assertEquals(0L, BudgetInsights.dailyAvailableCents(0L, 10))
    }

    @Test
    fun dailyAvailable_guardsZeroDays() {
        assertEquals(0L, BudgetInsights.dailyAvailableCents(1000_00L, 0))
    }

    /** 匀速理想值按已过天数算：3000 元 / 30 天，第 10 天该花 1000。 */
    @Test
    fun pace_idealIsProportionalToElapsedDays() {
        val progress = CycleProgress(totalDays = 30, elapsedDays = 10, remainingDays = 21)
        val pace = BudgetInsights.pace(spentCents = 1200_00L, budgetCents = 3000_00L, progress = progress)
        assertEquals(1000_00L, pace.idealSpentCents)
        assertEquals(200_00L, pace.diffCents)
    }

    @Test
    fun pace_negativeDiffMeansSaved() {
        val progress = CycleProgress(totalDays = 30, elapsedDays = 10, remainingDays = 21)
        val pace = BudgetInsights.pace(spentCents = 600_00L, budgetCents = 3000_00L, progress = progress)
        assertEquals(-400_00L, pace.diffCents)
    }

    /** 容差 5%：3000 元预算下 ±150 元内都算「节奏正常」，不给无谓的警示。 */
    @Test
    fun pace_isOnTrack_withinFivePercentTolerance() {
        val progress = CycleProgress(totalDays = 30, elapsedDays = 10, remainingDays = 21)
        val budget = 3000_00L
        assertTrue(BudgetInsights.pace(1140_00L, budget, progress).isOnTrack(budget))
        assertTrue(BudgetInsights.pace(860_00L, budget, progress).isOnTrack(budget))
        assertFalse(BudgetInsights.pace(1200_00L, budget, progress).isOnTrack(budget))
        assertFalse(BudgetInsights.pace(800_00L, budget, progress).isOnTrack(budget))
    }

    /** 预算为 0（理论上进不来，setBudget 要求 >= 0）时不能除零。 */
    @Test
    fun pace_zeroBudget_isSafe() {
        val progress = CycleProgress(totalDays = 30, elapsedDays = 10, remainingDays = 21)
        val pace = BudgetInsights.pace(100_00L, 0L, progress)
        assertEquals(0L, pace.idealSpentCents)
        assertTrue(pace.isOnTrack(0L))
    }

    /** 第 10 天花了 1000，按 30 天周期外推：1000 / 10 * 30 = 3000。 */
    @Test
    fun predictedTotal_extrapolatesBySpeed() {
        assertEquals(3000_00L, BudgetInsights.predictedTotalCents(1000_00L, elapsedDays = 10, totalDays = 30))
    }

    /** elapsedDays<=0 没有速率可言，直接返回已支出兜底，不外推。 */
    @Test
    fun predictedTotal_guardsZeroElapsedDays() {
        assertEquals(500_00L, BudgetInsights.predictedTotalCents(500_00L, elapsedDays = 0, totalDays = 30))
    }

    @Test
    fun predictedOverspend_returnsPositiveDiffWhenOverBudget() {
        assertEquals(500_00L, BudgetInsights.predictedOverspendCents(3500_00L, 3000_00L))
    }

    /** 预测值没超预算（含刚好持平）时不返回超支金额——负数/零没有警示意义。 */
    @Test
    fun predictedOverspend_null_whenNotOverBudget() {
        assertEquals(null, BudgetInsights.predictedOverspendCents(2500_00L, 3000_00L))
        assertEquals(null, BudgetInsights.predictedOverspendCents(3000_00L, 3000_00L))
    }
}
