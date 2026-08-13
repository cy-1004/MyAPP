package com.myapp.feature.ledger.data

import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.BudgetCycle
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 历史周期达成情况测试（PRD 3.6.2）。
 *
 * 这里最容易出错也最容易被忽略的是「那一期该跟哪份预算比」：
 * 对错了会让用户看到一排凭空判定的超支/达成，而他当时可能根本没设过预算。
 */
class BudgetHistoryInsightsTest {

    private val savedZone = AppTime.zone

    @Before
    fun fixZone() {
        AppTime.zone = ZoneOffset.UTC
    }

    @After
    fun restoreZone() {
        AppTime.zone = savedZone
    }

    private fun millis(date: LocalDate): Long = AppTime.run { date.toEpochMilliAtStartOfDay() }

    private fun budget(
        id: Long,
        effectiveFrom: LocalDate,
        totalCents: Long,
        cycleStartDay: Int = 10,
    ) = Budget(
        id = id,
        cycleStartDay = cycleStartDay,
        totalAmountCents = totalCents,
        effectiveFrom = millis(effectiveFrom),
        autoRollover = true,
    )

    @Test
    fun `期末前最后一次设定的预算才是这一期的目标`() {
        val budgets = listOf(
            budget(1, LocalDate.of(2026, 6, 1), 300_000L),
            // 8/10 开始的这一期中途改成 5000，用户心里的目标就是 5000
            budget(2, LocalDate.of(2026, 8, 20), 500_000L),
        )
        val cycleEnd = millis(LocalDate.of(2026, 9, 10))
        assertEquals(500_000L, BudgetHistoryInsights.budgetForCycle(budgets, cycleEnd)?.totalAmountCents)
    }

    @Test
    fun `这一期结束之后才设的预算不能追认到这一期`() {
        val budgets = listOf(budget(1, LocalDate.of(2026, 8, 20), 500_000L))
        // 7/10..8/10 这一期结束时，用户还没设过任何预算
        val cycleEnd = millis(LocalDate.of(2026, 8, 10))
        assertNull(BudgetHistoryInsights.budgetForCycle(budgets, cycleEnd))
    }

    @Test
    fun `一份预算都没有时每一期都没有目标`() {
        val cycleEnd = millis(LocalDate.of(2026, 9, 10))
        assertNull(BudgetHistoryInsights.budgetForCycle(emptyList(), cycleEnd))
    }

    @Test
    fun `没有预算的期不判超支 花再多也是灰柱`() {
        val perf = CyclePerformance(
            start = millis(LocalDate.of(2026, 7, 10)),
            endExclusive = millis(LocalDate.of(2026, 8, 10)),
            budgetCents = null,
            spentCents = 999_999_99L,
        )
        assertFalse(perf.isOverBudget)
    }

    @Test
    fun `正好花光算达成 不算超支`() {
        val exact = CyclePerformance(0L, 1L, budgetCents = 300_000L, spentCents = 300_000L)
        assertFalse(exact.isOverBudget)
        val over = CyclePerformance(0L, 1L, budgetCents = 300_000L, spentCents = 300_001L)
        assertTrue(over.isOverBudget)
    }

    @Test
    fun `performances 把区间 支出 预算三者对齐`() {
        val today = LocalDate.of(2026, 8, 15)
        val ranges = BudgetCycle.recentCycleRanges(cycleStartDay = 10, count = 3, today = today)
        // 6/10..7/10、7/10..8/10、8/10..9/10 三期
        val budgets = listOf(budget(1, LocalDate.of(2026, 7, 15), 300_000L))
        val spent = listOf(100_000L, 400_000L, 50_000L)

        val result = BudgetHistoryInsights.performances(ranges, spent, budgets)

        assertEquals(3, result.size)
        // 第 1 期结束时（7/10）还没设预算 -> 无目标
        assertNull(result[0].budgetCents)
        assertFalse(result[0].isOverBudget)
        // 第 2 期结束时（8/10）预算已是 3000，花了 4000 -> 超支
        assertEquals(300_000L, result[1].budgetCents)
        assertTrue(result[1].isOverBudget)
        // 第 3 期花了 500 -> 没超
        assertFalse(result[2].isOverBudget)
        assertEquals(ranges.map { it.first }, result.map { it.start })
    }

    @Test
    fun `performances 的区间与支出长度对不上直接抛 而不是静默错位`() {
        val ranges = BudgetCycle.recentCycleRanges(10, 3, LocalDate.of(2026, 8, 15))
        val e = runCatching {
            BudgetHistoryInsights.performances(ranges, listOf(1L, 2L), emptyList())
        }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun `柱高比例用统一分母 且分母为 0 时不除零`() {
        val perf = CyclePerformance(0L, 1L, budgetCents = 300_000L, spentCents = 150_000L)
        assertEquals(0.5f, perf.fractionOf(300_000L), 0.0001f)
        assertEquals(0f, perf.fractionOf(0L), 0.0001f)
        // 超过分母时截到 1f，柱子不会画出容器
        assertEquals(1f, perf.fractionOf(100_000L), 0.0001f)
    }
}
