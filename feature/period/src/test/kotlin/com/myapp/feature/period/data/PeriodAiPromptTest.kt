package com.myapp.feature.period.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发给 DeepSeek 的内容（PRD 3.14）。
 *
 * 这是全 App 唯一把用户手写的私密文字送出设备的地方，所以测试的重点不是
 * 「文案好不好看」，而是**边界**：该发的发了、不该发的一条都没发、
 * 数据没变时指纹不动、数据变了指纹一定变。
 */
class PeriodAiPromptTest {

    private val today = LocalDate.of(2026, 8, 14)

    private fun record(start: String, end: String?, note: String? = null, id: Long = start.hashCode().toLong()) =
        PeriodRecord(
            id = id,
            startDate = LocalDate.parse(start),
            endDate = end?.let(LocalDate::parse),
            note = note,
        )

    /** 记录按时间倒序，与 PeriodRepository 的口径一致。 */
    private fun state(vararg records: PeriodRecord) = PeriodState.Empty.copy(
        records = records.toList(),
        avgCycleDays = 29,
        avgDurationDays = 5,
        cycleSamples = records.size - 1,
        reliable = records.size >= 4,
        predictedStart = LocalDate.of(2026, 8, 17),
    )

    private fun logs(vararg logs: PeriodDayLog) = logs.associateBy { it.date }

    @Test
    fun `正文包含统计、经期区间与每日记录`() {
        val input = PeriodAiPrompt.buildInput(
            state = state(
                record("2026-07-20", "2026-07-25"),
                record("2026-06-22", "2026-06-27"),
            ),
            dayLogs = logs(
                PeriodDayLog(
                    date = LocalDate.of(2026, 7, 21),
                    tags = listOf(DayLogTag.CRAMPS),
                    note = "有血丝",
                ),
            ),
            today = today,
        )
        assertTrue(input, input.contains("平均周期 29 天"))
        assertTrue(input, input.contains("2026-07-20"))
        assertTrue(input, input.contains("共 6 天"))
        // 两次开始相隔 28 天，这个间隔要显式给出，别让模型自己去减日期
        assertTrue(input, input.contains("距上次开始 28 天"))
        assertTrue(input, input.contains("痛经"))
        assertTrue(input, input.contains("有血丝"))
    }

    @Test
    fun `经期备注会一起发出去`() {
        // 用户选的数据范围是「全部」，备注属于其中一项——这条测试是那个选择的凭据
        val input = PeriodAiPrompt.buildInput(
            state = state(record("2026-07-20", "2026-07-25", note = "这次量特别少")),
            dayLogs = emptyMap(),
            today = today,
        )
        assertTrue(input, input.contains("备注：这次量特别少"))
    }

    @Test
    fun `最多只发 6 次经期`() {
        val many = (1..10).map { record("2026-0${(it % 9) + 1}-01".let { d -> d }, null, id = it.toLong()) }
        val input = PeriodAiPrompt.buildInput(state(*many.toTypedArray()), emptyMap(), today)
        assertTrue(input, input.contains("【最近 6 次经期】"))
    }

    @Test
    fun `比最早那次经期还早的日记录不发`() {
        // 更早的记录对「最近的节律」没有解释力，发出去只是白白多暴露一段隐私
        val input = PeriodAiPrompt.buildInput(
            state = state(record("2026-07-20", "2026-07-25")),
            dayLogs = logs(
                PeriodDayLog(date = LocalDate.of(2026, 5, 1), note = "很久以前的事"),
                PeriodDayLog(date = LocalDate.of(2026, 7, 22), note = "这次的事"),
            ),
            today = today,
        )
        assertFalse(input, input.contains("很久以前的事"))
        assertTrue(input, input.contains("这次的事"))
    }

    @Test
    fun `空的日记录不占位置`() {
        val input = PeriodAiPrompt.buildInput(
            state = state(record("2026-07-20", "2026-07-25")),
            dayLogs = logs(PeriodDayLog(date = LocalDate.of(2026, 7, 22))),
            today = today,
        )
        assertTrue(input, input.contains("（这段时间没有每日记录）"))
    }

    @Test
    fun `样本不足时明确告诉模型别下定论`() {
        val input = PeriodAiPrompt.buildInput(
            state = state(record("2026-07-20", "2026-07-25")).copy(reliable = false),
            dayLogs = emptyMap(),
            today = today,
        )
        assertTrue(input, input.contains("样本量不足"))
    }

    @Test
    fun `指纹只认数据，不认日期变化`() {
        // today 每天都在变；若它算进指纹，「数据没变就不重复调用」每天零点就失效了
        val s = state(record("2026-07-20", "2026-07-25"))
        val fingerprint = PeriodAiPrompt.fingerprint(s, emptyMap())
        assertEquals(fingerprint, PeriodAiPrompt.fingerprint(s, emptyMap()))
    }

    @Test
    fun `改了日记录的文字，指纹就变`() {
        val s = state(record("2026-07-20", "2026-07-25"))
        val before = PeriodAiPrompt.fingerprint(
            s,
            logs(PeriodDayLog(date = LocalDate.of(2026, 7, 22), note = "有血丝")),
        )
        val after = PeriodAiPrompt.fingerprint(
            s,
            logs(PeriodDayLog(date = LocalDate.of(2026, 7, 22), note = "有血丝，量变多")),
        )
        assertNotEquals(before, after)
    }

    @Test
    fun `新增一次经期记录，指纹就变`() {
        val before = PeriodAiPrompt.fingerprint(state(record("2026-07-20", "2026-07-25")), emptyMap())
        val after = PeriodAiPrompt.fingerprint(
            state(record("2026-08-17", null), record("2026-07-20", "2026-07-25")),
            emptyMap(),
        )
        assertNotEquals(before, after)
    }

    @Test
    fun `指纹是 SHA-256 的十六进制`() {
        val fingerprint = PeriodAiPrompt.fingerprint(state(record("2026-07-20", "2026-07-25")), emptyMap())
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint, fingerprint.all { it in "0123456789abcdef" })
    }

    @Test
    fun `系统指令禁止诊断与用药`() {
        // 这几句是这个功能能不能存在的前提，不能在后续调整措辞时被顺手删掉
        assertTrue(PeriodAiPrompt.INSTRUCTIONS.contains("不做诊断"))
        assertTrue(PeriodAiPrompt.INSTRUCTIONS.contains("药"))
        assertTrue(PeriodAiPrompt.INSTRUCTIONS.contains("建议找医生看看"))
    }
}
