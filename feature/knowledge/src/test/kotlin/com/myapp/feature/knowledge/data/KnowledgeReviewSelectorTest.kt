package com.myapp.feature.knowledge.data

import com.myapp.core.common.time.AppTime
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 知识池挑选 + 间隔复习算法测试（PRD 3.8）。
 *
 * 最容易出错的地方是"同一天重复调用要稳定"——这里没有额外存储"今天选中谁"，
 * 完全靠排序确定性保证，测的就是这份确定性。
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
    fun `从没推过的优先于已到期的`() {
        val candidates = listOf(PoolCandidate(1, sortOrder = 1), PoolCandidate(2, sortOrder = 2))
        val reviews = mapOf(
            2L to KnowledgeReview(2, intervalLevel = 0, nextDueAt = today - 1, lastShownAt = today - 1),
        )
        // 1 从没推过，2 已到期——从没推过的赢
        assertEquals(1L, KnowledgeReviewSelector.pickNext(candidates, reviews, today)?.sourceId)
    }

    @Test
    fun `都没推过时按sortOrder确定性排序`() {
        val candidates = listOf(PoolCandidate(2, sortOrder = 2), PoolCandidate(1, sortOrder = 1))
        assertEquals(1L, KnowledgeReviewSelector.pickNext(candidates, emptyMap(), today)?.sourceId)
    }

    @Test
    fun `到期的按最逾期优先`() {
        val candidates = listOf(PoolCandidate(1, sortOrder = 1), PoolCandidate(2, sortOrder = 2))
        val reviews = mapOf(
            1L to KnowledgeReview(1, intervalLevel = 0, nextDueAt = today - 1, lastShownAt = today - 1),
            2L to KnowledgeReview(2, intervalLevel = 0, nextDueAt = today - 5, lastShownAt = today - 5),
        )
        // 2 逾期更久，优先推 2
        assertEquals(2L, KnowledgeReviewSelector.pickNext(candidates, reviews, today)?.sourceId)
    }

    @Test
    fun `还没到期的不会被选中`() {
        val candidates = listOf(PoolCandidate(1, sortOrder = 1))
        val reviews = mapOf(
            1L to KnowledgeReview(1, intervalLevel = 0, nextDueAt = today + millisPerDay, lastShownAt = today),
        )
        assertNull(KnowledgeReviewSelector.pickNext(candidates, reviews, today))
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
