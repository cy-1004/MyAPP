package com.myapp.feature.period.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预测逻辑的回归测试。
 *
 * 这块必须有测试：算错了 UI 照样显示一个像模像样的日期，
 * 肉眼根本看不出来，只有拿构造好的样本比对才能发现。
 */
class PeriodPredictionTest {

    private val today = LocalDate.of(2026, 8, 6)

    /** 传入的记录必须按开始日倒序，与 DAO 的返回顺序一致。 */
    private fun records(vararg pairs: Pair<LocalDate, LocalDate?>): List<PeriodRecord> =
        pairs.mapIndexed { index, (start, end) ->
            PeriodRecord(id = index + 1L, startDate = start, endDate = end, note = null)
        }

    @Test
    fun `没有记录时返回空状态`() {
        val state = computeState(emptyList(), today)
        assertEquals(PeriodStatus.NoData, state.status)
        assertEquals(DEFAULT_CYCLE_DAYS, state.avgCycleDays)
        assertFalse(state.reliable)
    }

    @Test
    fun `只有一次记录时用默认周期并标记样本不足`() {
        val state = computeState(
            records(LocalDate.of(2026, 8, 4) to null),
            today,
        )
        assertEquals(DEFAULT_CYCLE_DAYS, state.avgCycleDays)
        assertEquals(0, state.cycleSamples)
        assertFalse("只有一次记录不该被当成可靠预测", state.reliable)
        assertEquals(LocalDate.of(2026, 9, 1), state.predictedStart)
    }

    @Test
    fun `进行中的记录算出第几天`() {
        val state = computeState(
            records(LocalDate.of(2026, 8, 4) to null),
            today,
        )
        val status = state.status
        assertTrue(status is PeriodStatus.Ongoing)
        // 8/4 开始，8/6 是第 3 天（当天算第 1 天）
        assertEquals(3, (status as PeriodStatus.Ongoing).day)
    }

    @Test
    fun `多次记录取平均周期并预测下次开始`() {
        // 间隔依次为 28、30、29 天，均值 29
        val state = computeState(
            records(
                LocalDate.of(2026, 7, 20) to LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 6, 22) to LocalDate.of(2026, 6, 27),
                LocalDate.of(2026, 5, 23) to LocalDate.of(2026, 5, 27),
                LocalDate.of(2026, 4, 25) to LocalDate.of(2026, 4, 29),
            ),
            today,
        )
        assertEquals(29, state.avgCycleDays)
        assertEquals(3, state.cycleSamples)
        assertTrue("三个以上间隔应视为可靠", state.reliable)
        assertEquals(LocalDate.of(2026, 8, 18), state.predictedStart)

        val status = state.status
        assertTrue(status is PeriodStatus.Waiting)
        assertEquals(12L, (status as PeriodStatus.Waiting).daysUntil)
    }

    @Test
    fun `平均持续天数含首尾两天`() {
        val state = computeState(
            records(
                LocalDate.of(2026, 7, 20) to LocalDate.of(2026, 7, 24), // 5 天
                LocalDate.of(2026, 6, 22) to LocalDate.of(2026, 6, 27), // 6 天
            ),
            today,
        )
        assertEquals(6, state.avgDurationDays) // (5+6)/2 = 5.5 → 四舍五入 6
    }

    @Test
    fun `忘记记录结束时不会一直显示进行中`() {
        // 40 天前开始且没有结束日，显然是漏记了，不该显示「经期第 41 天」
        val state = computeState(
            records(
                LocalDate.of(2026, 6, 27) to null,
                LocalDate.of(2026, 5, 30) to LocalDate.of(2026, 6, 4),
            ),
            today,
        )
        assertTrue(state.status is PeriodStatus.Waiting)
    }

    @Test
    fun `已推迟时天数为负`() {
        val state = computeState(
            records(
                LocalDate.of(2026, 6, 27) to LocalDate.of(2026, 7, 2),
                LocalDate.of(2026, 5, 30) to LocalDate.of(2026, 6, 4),
            ),
            today,
        )
        // 6/27 + 28 = 7/25，已经过去 12 天
        val status = state.status as PeriodStatus.Waiting
        assertEquals(-12L, status.daysUntil)
    }

    @Test
    fun `补录历史时的异常间隔被剔除`() {
        // 中间夹了一条隔了一年多的记录（典型的录错年份），不该把均值拉到 200 多天
        val state = computeState(
            records(
                LocalDate.of(2026, 7, 20) to LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 6, 22) to LocalDate.of(2026, 6, 27),
                LocalDate.of(2025, 1, 5) to LocalDate.of(2025, 1, 10),
            ),
            today,
        )
        assertEquals(28, state.avgCycleDays)
        assertEquals(1, state.cycleSamples)
    }

    @Test
    fun `预测区间长度跟随平均持续天数`() {
        val state = computeState(
            records(
                LocalDate.of(2026, 7, 20) to LocalDate.of(2026, 7, 24), // 5 天
                LocalDate.of(2026, 6, 22) to LocalDate.of(2026, 6, 26), // 5 天
            ),
            today,
        )
        val range = requireNotNull(state.predictedRange)
        assertEquals(state.predictedStart, range.start)
        assertEquals(state.predictedStart!!.plusDays(4), range.endInclusive)
    }
}
