package com.myapp.feature.period.data

import com.myapp.core.common.time.AppTime
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 经期中每日关怀提醒的排期逻辑（PRD 3.2）。
 *
 * 必须有测试：这条链路真机上很难验--要看到它排进闹钟，得先有一条「进行中」的经期记录，
 * 而经期记录是真实数据，不能为了测试随便造一条再删。
 */
class CareReminderPlanTest {

    private val savedZone = AppTime.zone

    @Before
    fun fixZone() {
        AppTime.zone = ZoneOffset.UTC
    }

    @After
    fun restoreZone() {
        AppTime.zone = savedZone
    }

    private val start = LocalDate.of(2026, 8, 10)

    /** 某天 [hour] 点整的时间戳，用来构造「现在几点」。 */
    private fun at(date: LocalDate, hour: Int): Long =
        AppTime.run { date.toEpochMilliAtTime(hour) }

    @Test
    fun `开始当天早上记录 三天全排上`() {
        val plan = careReminderPlan(start, endDate = null, now = at(start, 8))
        assertEquals(listOf(1, 2, 3), plan.map { it.day })
        assertEquals(at(start, CARE_REMINDER_HOUR), plan[0].triggerAtMillis)
        assertEquals(at(start.plusDays(1), CARE_REMINDER_HOUR), plan[1].triggerAtMillis)
        assertEquals(at(start.plusDays(2), CARE_REMINDER_HOUR), plan[2].triggerAtMillis)
    }

    @Test
    fun `晚上才补记开始日 当天那条不再排`() {
        // 23 点才想起来记，第 1 天的 19:00 已经过了——
        // 排一个过去的时间点会被 AlarmManager 立刻触发，补记一次就弹一串旧提醒
        val plan = careReminderPlan(start, endDate = null, now = at(start, 23))
        assertEquals(listOf(2, 3), plan.map { it.day })
    }

    @Test
    fun `提前记了结束日 结束日之后的不再排`() {
        // 第 2 天就结束了，第 3 天那条该撤掉
        val plan = careReminderPlan(start, endDate = start.plusDays(1), now = at(start, 8))
        assertEquals(listOf(1, 2), plan.map { it.day })
    }

    @Test
    fun `补记一段已经结束的旧经期 一条都不排`() {
        val oldStart = LocalDate.of(2026, 7, 1)
        val plan = careReminderPlan(oldStart, endDate = oldStart.plusDays(6), now = at(start, 8))
        assertTrue(plan.isEmpty())
    }

    @Test
    fun `当天恰好 19 点整不重复排`() {
        // 边界：triggerAt <= now 才跳过，等于也算过了——
        // 用 < 的话 19:00:00 这一瞬间记录会排一条立刻触发的闹钟
        val plan = careReminderPlan(start, endDate = null, now = at(start, CARE_REMINDER_HOUR))
        assertEquals(listOf(2, 3), plan.map { it.day })
    }

    @Test
    fun `永远不超过三天`() {
        val plan = careReminderPlan(start, endDate = start.plusDays(9), now = at(start, 8))
        assertEquals(CARE_REMINDER_DAYS, plan.size)
    }
}
