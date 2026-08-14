package com.myapp.feature.period.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排卵期 / 黄体期推算（PRD 3.2）。
 *
 * 这套数字用户看不出对错——月历上画出来就长得像那么回事。
 * 所以口径必须有测试钉死，尤其是「不该画的时候不画」那几条。
 */
class CyclePhasesTest {

    private fun state(
        predictedStart: LocalDate?,
        reliable: Boolean,
        lastEnd: LocalDate? = null,
        lastStart: LocalDate = LocalDate.of(2026, 8, 1),
    ) = PeriodState.Empty.copy(
        records = listOf(PeriodRecord(id = 1, startDate = lastStart, endDate = lastEnd, note = null)),
        reliable = reliable,
        predictedStart = predictedStart,
    )

    @Test
    fun `排卵日是下次开始前 14 天，不是周期中点`() {
        // 30 天周期：中点法会算到第 15 天（8-16），正确口径是 8-31 减 14 = 8-17
        val phases = computePhases(
            state(predictedStart = LocalDate.of(2026, 8, 31), reliable = true),
        )!!
        assertEquals(LocalDate.of(2026, 8, 17), phases.ovulationDay)
    }

    @Test
    fun `长周期不会把排卵日算得离谱`() {
        // 35 天周期：中点法会算到第 17~18 天，实际应在第 21 天附近
        val phases = computePhases(
            state(
                lastStart = LocalDate.of(2026, 8, 1),
                predictedStart = LocalDate.of(2026, 9, 5),
                reliable = true,
            ),
        )!!
        assertEquals(LocalDate.of(2026, 8, 22), phases.ovulationDay)
    }

    @Test
    fun `易孕窗口是排卵日前 5 天到后 1 天`() {
        val phases = computePhases(
            state(predictedStart = LocalDate.of(2026, 8, 31), reliable = true),
        )!!
        assertEquals(LocalDate.of(2026, 8, 12), phases.fertileWindow.start)
        assertEquals(LocalDate.of(2026, 8, 18), phases.fertileWindow.endInclusive)
    }

    @Test
    fun `黄体期从排卵日次日到下次开始前一天`() {
        val phases = computePhases(
            state(predictedStart = LocalDate.of(2026, 8, 31), reliable = true),
        )!!
        assertEquals(LocalDate.of(2026, 8, 18), phases.luteal.start)
        assertEquals(LocalDate.of(2026, 8, 30), phases.luteal.endInclusive)
    }

    @Test
    fun `卵泡期从本次经期结束次日算起`() {
        val phases = computePhases(
            state(
                predictedStart = LocalDate.of(2026, 8, 31),
                reliable = true,
                lastEnd = LocalDate.of(2026, 8, 5),
            ),
        )!!
        assertEquals(LocalDate.of(2026, 8, 6), phases.follicular!!.start)
        assertEquals(LocalDate.of(2026, 8, 16), phases.follicular!!.endInclusive)
    }

    @Test
    fun `经期还进行中时不给卵泡期，但其余分期照常`() {
        // 没点「结束」就没有起点。拿开始日凑一个会把经期本身算进卵泡期
        val phases = computePhases(
            state(predictedStart = LocalDate.of(2026, 8, 31), reliable = true, lastEnd = null),
        )!!
        assertNull(phases.follicular)
        assertEquals(LocalDate.of(2026, 8, 17), phases.ovulationDay)
    }

    @Test
    fun `预测不可靠时一律不给分期`() {
        // 分期是从预测倒推的，预测不准分期只会更不准——宁可不画（PRD 3.2）
        assertNull(computePhases(state(predictedStart = LocalDate.of(2026, 8, 31), reliable = false)))
    }

    @Test
    fun `没有预测时不给分期`() {
        assertNull(computePhases(state(predictedStart = null, reliable = true)))
    }

    @Test
    fun `重叠的日子显示更具体的那个分期`() {
        val phases = computePhases(
            state(
                predictedStart = LocalDate.of(2026, 8, 31),
                reliable = true,
                lastEnd = LocalDate.of(2026, 8, 5),
            ),
        )!!
        // 排卵日本身也在易孕窗口里 → 显示排卵日
        assertEquals(PhaseMark.Ovulation, phaseOf(LocalDate.of(2026, 8, 17), phases))
        // 排卵日后 1 天同时属于易孕窗口末尾与黄体期首日 → 显示易孕期
        assertEquals(PhaseMark.Fertile, phaseOf(LocalDate.of(2026, 8, 18), phases))
        // 再往后只剩黄体期
        assertEquals(PhaseMark.Luteal, phaseOf(LocalDate.of(2026, 8, 19), phases))
        // 经期结束后、易孕窗口之前是卵泡期
        assertEquals(PhaseMark.Follicular, phaseOf(LocalDate.of(2026, 8, 8), phases))
    }

    @Test
    fun `分期表为空时任何一天都没有标记`() {
        assertNull(phaseOf(LocalDate.of(2026, 8, 17), null))
    }

    @Test
    fun `极短周期不会算出倒着走的卵泡期`() {
        // 经期结束日已经晚于推算的排卵日（周期短 + 经期长），此时卵泡期没有意义
        val phases = computePhases(
            state(
                lastStart = LocalDate.of(2026, 8, 1),
                predictedStart = LocalDate.of(2026, 8, 21),
                reliable = true,
                lastEnd = LocalDate.of(2026, 8, 9),
            ),
        )!!
        assertEquals(LocalDate.of(2026, 8, 7), phases.ovulationDay)
        assertNull(phases.follicular)
    }

    @Test
    fun `标签落库往返一致，认不出的 id 被丢弃`() {
        val tags = listOf(DayLogTag.CRAMPS, DayLogTag.DISCHARGE_BLOOD)
        val raw = DayLogTag.join(tags)
        // 存的是 id 而不是中文文案：改文案不该让历史记录失效
        assertTrue(raw, raw.contains("cramps") && raw.contains("discharge_blood"))
        assertEquals(setOf(DayLogTag.CRAMPS, DayLogTag.DISCHARGE_BLOOD), DayLogTag.parse(raw).toSet())
        assertEquals(listOf(DayLogTag.CRAMPS), DayLogTag.parse("cramps,不认识的标签"))
        assertEquals(emptyList<DayLogTag>(), DayLogTag.parse(""))
    }

    @Test
    fun `标签与文本都空的记录视为空`() {
        assertTrue(PeriodDayLog(date = LocalDate.of(2026, 8, 14), note = "   ").isEmpty)
        assertTrue(!PeriodDayLog(date = LocalDate.of(2026, 8, 14), note = "有血丝").isEmpty)
    }
}
