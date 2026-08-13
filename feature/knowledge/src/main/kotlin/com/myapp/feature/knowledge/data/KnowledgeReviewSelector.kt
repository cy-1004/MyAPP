package com.myapp.feature.knowledge.data

import com.myapp.core.common.time.AppTime

/** 间隔复习档位对应的天数（PRD 3.8）。下标即 [KnowledgeReview.intervalLevel]。 */
val INTERVAL_DAYS = listOf(1, 3, 7, 15, 30)

/** 知识池候选：只带选择算法需要的字段，避免这个纯函数依赖 Room 实体。 */
data class PoolCandidate(
    val sourceId: Long,
    val sortOrder: Int,
)

/** 一条知识点的复习状态。没有对应行 = 从没被推送过。 */
data class KnowledgeReview(
    val sourceId: Long,
    val intervalLevel: Int,
    val nextDueAt: Long,
    val lastShownAt: Long?,
)

/**
 * 知识池每日挑选 + 间隔复习算法（PRD 3.8），纯函数，不依赖 Android/Room。
 *
 * 同一天内重复调用 [pickNext] 会返回同一条：不额外存"今天选中谁"，
 * 因为只有用户点了反馈按钮（[onMastered]/[onSnoozed]）才会推进 `nextDueAt`，
 * 选择结果天然幂等，省掉一份状态。
 */
object KnowledgeReviewSelector {

    /**
     * 优先级：从没推过的（按 sortOrder/sourceId 保证确定性）> 到期的（按最逾期优先）> 无候选返回 null。
     */
    fun pickNext(
        candidates: List<PoolCandidate>,
        reviews: Map<Long, KnowledgeReview>,
        today: Long,
    ): PoolCandidate? {
        if (candidates.isEmpty()) return null
        val neverShown = candidates
            .filter { reviews[it.sourceId] == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.sourceId }))
        if (neverShown.isNotEmpty()) return neverShown.first()

        val due = candidates
            .mapNotNull { c -> reviews[c.sourceId]?.let { c to it } }
            .filter { (_, review) -> review.nextDueAt <= today }
            .sortedWith(compareBy({ (_, review) -> review.nextDueAt }, { (c, _) -> c.sourceId }))
        return due.firstOrNull()?.first
    }

    /** 已掌握：进一级，封顶在最后一档后原地循环（长期低频复现，不退出知识池）。 */
    fun onMastered(review: KnowledgeReview?, sourceId: Long, today: Long): KnowledgeReview {
        val nextLevel = ((review?.intervalLevel ?: -1) + 1).coerceAtMost(INTERVAL_DAYS.lastIndex)
        return advance(sourceId, nextLevel, today)
    }

    /** 再看看：回到第 0 档，明天就能再见到。 */
    fun onSnoozed(review: KnowledgeReview?, sourceId: Long, today: Long): KnowledgeReview =
        advance(sourceId, level = 0, today = today)

    private fun advance(sourceId: Long, level: Int, today: Long): KnowledgeReview {
        val todayDate = AppTime.run { today.toLocalDate() }
        val dueDate = todayDate.plusDays(INTERVAL_DAYS[level].toLong())
        return KnowledgeReview(
            sourceId = sourceId,
            intervalLevel = level,
            nextDueAt = AppTime.run { dueDate.toEpochMilliAtStartOfDay() },
            lastShownAt = today,
        )
    }
}
