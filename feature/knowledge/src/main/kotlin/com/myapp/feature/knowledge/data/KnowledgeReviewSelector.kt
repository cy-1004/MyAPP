package com.myapp.feature.knowledge.data

import com.myapp.core.common.time.AppTime
import kotlin.random.Random

/** 间隔复习档位对应的天数（PRD 3.8）。下标即 [KnowledgeReview.intervalLevel]。 */
val INTERVAL_DAYS = listOf(1, 3, 7, 15, 30)

/**
 * 知识池候选：只带选择算法需要的字段，避免这个纯函数依赖 Room 实体。
 *
 * 曾经有个 `sortOrder` 字段，是 `interview_question.sort_order`（**章内**序号）。
 * [KnowledgeReviewSelector.pickNext] 拿它当全局排序键用，把所有章节的第 1 题
 * （章内序号都是 0）一起顶到队首，于是每天推的都是「某一章的第 1 题」。
 * 改成随机抽之后这个字段没有用途了，直接删掉，免得再有人当它是全局序。
 */
data class PoolCandidate(
    val sourceId: Long,
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
 * 同一天内重复调用 [pickNext] 会返回同一条--08:00 的通知和首页卡片必须是同一道题，
 * 否则点开通知看到的和卡片上写的对不上。幂等靠的是 [KnowledgeReview.lastShownAt]：
 * 调用方在选中后调 [onShown] 记一笔「今天推过了」，[pickNext] 下次先认这一笔。
 */
object KnowledgeReviewSelector {

    /**
     * 今天该看哪道题。
     *
     * 1. 今天已经选过（某条 `lastShownAt == today`）就返回同一条，保证幂等；
     * 2. 否则从「没推过的 + 已到期的」里**随机**抽一条。
     *
     * 随机而不是按顺序：候选按固定顺序排的话，只要队首那条不被消费掉，
     * 它就会天天霸榜（这正是「每天都推某一章第 1 题」的成因之一）。
     * 用 [today] 做随机种子，同一天算出来的下标恒定，不用额外存状态。
     *
     * 「没推过的」和「已到期的」放在同一个池里抽，不分优先级：
     * 题库有几百道，新题优先会让复习项排到几百天以后，间隔复习就名存实亡了。
     */
    fun pickNext(
        candidates: List<PoolCandidate>,
        reviews: Map<Long, KnowledgeReview>,
        today: Long,
    ): PoolCandidate? {
        if (candidates.isEmpty()) return null

        val shownToday = candidates.firstOrNull { reviews[it.sourceId]?.lastShownAt == today }
        if (shownToday != null) return shownToday

        val eligible = candidates.filter { candidate ->
            val review = reviews[candidate.sourceId]
            review == null || review.nextDueAt <= today
        }
        if (eligible.isEmpty()) return null
        return eligible[Random(today).nextInt(eligible.size)]
    }

    /**
     * 记一笔「今天推过了」。
     *
     * 用户没点「已掌握」/「再看看」时也要落这一笔，否则该题明天仍在候选池里，
     * 随机抽还可能抽到它，用户感知就是「翻来覆去推同一道」。
     * 效果是「明天重新进入候选池」，但**不动 [KnowledgeReview.intervalLevel]**--
     * 推送不等于用户消化了，把一道熬到第 4 档的题因为一次没点开就打回第 0 档，
     * 会把用户攒的复习进度洗掉。档位只由 [onMastered]/[onSnoozed] 改。
     *
     * 已经有今天记录时原样返回，避免重复写库把 `lastShownAt` 抖成别的值。
     */
    fun onShown(review: KnowledgeReview?, sourceId: Long, today: Long): KnowledgeReview {
        if (review != null && review.lastShownAt == today) return review
        val tomorrow = AppTime.run { today.toLocalDate().plusDays(1).toEpochMilliAtStartOfDay() }
        return KnowledgeReview(
            sourceId = sourceId,
            intervalLevel = review?.intervalLevel ?: 0,
            nextDueAt = tomorrow,
            lastShownAt = today,
        )
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
