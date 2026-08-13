package com.myapp.feature.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 预算预警判定测试（PRD 3.6.2：80%/100% 各一次性通知）。
 */
class BudgetAlertEvaluatorTest {

    private val budget = 3000_00L
    private val none = AlertState(notified80 = false, notified100 = false)

    @Test
    fun `未到80%不触发`() {
        assertEquals(emptyList<AlertKind>(), BudgetAlertEvaluator.evaluate(2000_00L, budget, none))
    }

    @Test
    fun `到80%触发一次`() {
        assertEquals(listOf(AlertKind.REACHED_80), BudgetAlertEvaluator.evaluate(2400_00L, budget, none))
    }

    @Test
    fun `已触发过80%的不重复触发`() {
        val state = AlertState(notified80 = true, notified100 = false)
        assertEquals(emptyList<AlertKind>(), BudgetAlertEvaluator.evaluate(2900_00L, budget, state))
    }

    @Test
    fun `一笔大额支出直接跳到105%只触发100不触发80`() {
        assertEquals(listOf(AlertKind.REACHED_100), BudgetAlertEvaluator.evaluate(3150_00L, budget, none))
    }

    @Test
    fun `已80未100时到达100触发100`() {
        val state = AlertState(notified80 = true, notified100 = false)
        assertEquals(listOf(AlertKind.REACHED_100), BudgetAlertEvaluator.evaluate(3000_00L, budget, state))
    }

    @Test
    fun `两者都已触发不再触发`() {
        val state = AlertState(notified80 = true, notified100 = true)
        assertEquals(emptyList<AlertKind>(), BudgetAlertEvaluator.evaluate(5000_00L, budget, state))
    }

    @Test
    fun `预算不大于0不触发`() {
        assertEquals(emptyList<AlertKind>(), BudgetAlertEvaluator.evaluate(100_00L, 0L, none))
    }
}
