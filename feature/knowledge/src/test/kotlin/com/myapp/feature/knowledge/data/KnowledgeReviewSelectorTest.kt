package com.myapp.feature.knowledge.data

import com.myapp.core.common.time.AppTime
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 知识池挑选 + 间隔复习算法测试（PRD 3.8）。
 *
 * 最容易出错的地方是"同一天重复调用要稳定"——08:00 的通知和首页卡片必须是同一道题。
 * 稳定性由 `lastShownAt == today` 这一笔保证（调用方在选中后落库），测的就是这条链路。
 */
class KnowledgeReviewSelectorTest {

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

    // 用 get() 而不是 val：class 属性初始化发生在 @Before 设置 UTC 之前，
    // 用系统时区算出的 today 会跟测试方法体里（UTC 下）算出的期望值差着一个时区偏移。
    private val today: Long get() = millis(LocalDate.of(2026, 8, 13))

    @Test
    fun `今天已经推过的原样返回 保证通知与首页卡片一致`() {
        val candidates = (1L..50L).map { PoolCandidate(it) }
        val reviews = mapOf(
            7L to KnowledgeReview(7, intervalLevel = 2, nextDueAt = today + millisPerDay, lastShownAt = today),
        )
        // 7 今天推过了，即使它 nextDueAt 在未来（已不在候选池），也必须还返回它
        repeat(5) {
            assertEquals(7L, KnowledgeReviewSelector.pickNext(candidates, reviews, today)?.sourceId)
        }
    }

    @Test
    fun `没推过的和已到期的在同一个池里抽`() {
        // 1 从没推过，2 已到期——两者都该有机会被抽到，不是「新题永远优先」
        val candidates = listOf(PoolCandidate(1), PoolCandidate(2))
        val picked = (0L until 60L).map { dayOffset ->
            val day = today + dayOffset * millisPerDay
            val reviews = mapOf(
                2L to KnowledgeReview(2, intervalLevel = 0, nextDueAt = day, lastShownAt = day - millisPerDay),
            )
            KnowledgeReviewSelector.pickNext(candidates, reviews, day)?.sourceId
        }.toSet()
        assertEquals(setOf(1L, 2L), picked)
    }

    @Test
    fun `不再天天推同一条 随机结果随日期变化`() {
        val candidates = (1L..100L).map { PoolCandidate(it) }
        val picked = (0L until 30L)
            .map { KnowledgeReviewSelector.pickNext(candidates, emptyMap(), today + it * millisPerDay)?.sourceId }
            .toSet()
        // 改版前这里恒等于 {1}（永远取排序后的第一条），现在必须散开
        assertTrue("30 天只抽到 ${picked.size} 种题，随机没生效", picked.size > 10)
    }

    @Test
    fun `同一天多次调用结果一致`() {
        val candidates = (1L..100L).map { PoolCandidate(it) }
        val first = KnowledgeReviewSelector.pickNext(candidates, emptyMap(), today)?.sourceId
        repeat(10) {
            assertEquals(first, KnowledgeReviewSelector.pickNext(candidates, emptyMap(), today)?.sourceId)
        }
    }

    @Test
    fun `还没到期的不会被选中`() {
        val candidates = listOf(PoolCandidate(1))
        val reviews = mapOf(
            1L to KnowledgeReview(1, intervalLevel = 0, nextDueAt = today + millisPerDay, lastShownAt = today - millisPerDay),
        )
        assertNull(KnowledgeReviewSelector.pickNext(candidates, reviews, today))
    }

    @Test
    fun `推送留痕 明天重新进入候选池但不动档位`() {
        val mastered = KnowledgeReview(1, intervalLevel = 3, nextDueAt = today, lastShownAt = today - millisPerDay)
        val shown = KnowledgeReviewSelector.onShown(mastered, sourceId = 1, today = today)
        assertEquals(today, shown.lastShownAt)
        assertEquals(millis(LocalDate.of(2026, 8, 14)), shown.nextDueAt)
        // 只是推给用户看了，没得到反馈，不该把攒到第 3 档的复习进度打回 0
        assertEquals(3, shown.intervalLevel)
    }

    @Test
    fun `推送留痕对同一天幂等`() {
        val already = KnowledgeReview(1, intervalLevel = 2, nextDueAt = today + millisPerDay, lastShownAt = today)
        assertEquals(already, KnowledgeReviewSelector.onShown(already, sourceId = 1, today = today))
    }

    @Test
    fun `候选为空返回null`() {
        assertNull(KnowledgeReviewSelector.pickNext(emptyList(), emptyMap(), today))
    }

    @Test
    fun `已掌握进一级 间隔天数正确推进`() {
        val review = KnowledgeReviewSelector.onMastered(review = null, sourceId = 1, today = today)
        assertEquals(0, review.intervalLevel)
        assertEquals(millis(LocalDate.of(2026, 8, 14)), review.nextDueAt) // 第 0 档 = 1 天

        val level2 = KnowledgeReviewSelector.onMastered(review, sourceId = 1, today = today)
        assertEquals(1, level2.intervalLevel)
        assertEquals(millis(LocalDate.of(2026, 8, 16)), level2.nextDueAt) // 第 1 档 = 3 天
    }

    @Test
    fun `已掌握到最后一档后原地循环 不越界不退出`() {
        var review: KnowledgeReview? = null
        repeat(INTERVAL_DAYS.size + 3) {
            review = KnowledgeReviewSelector.onMastered(review, sourceId = 1, today = today)
        }
        assertEquals(INTERVAL_DAYS.lastIndex, review!!.intervalLevel)
        assertEquals(millis(LocalDate.of(2026, 8, 13).plusDays(30)), review!!.nextDueAt)
    }

    @Test
    fun `再看看重置到第0档 明天就能再见到`() {
        val advanced = KnowledgeReviewSelector.onMastered(null, sourceId = 1, today = today)
        val snoozed = KnowledgeReviewSelector.onSnoozed(advanced, sourceId = 1, today = today)
        assertEquals(0, snoozed.intervalLevel)
        assertEquals(millis(LocalDate.of(2026, 8, 14)), snoozed.nextDueAt)
    }

    private companion object {
        const val millisPerDay = 24 * 60 * 60 * 1000L
    }
}
