package com.myapp.feature.knowledge.interview

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.InterviewChapterSummary
import com.myapp.core.database.dao.InterviewDao
import com.myapp.core.database.model.InterviewReviewEntity
import com.myapp.feature.knowledge.data.KnowledgeReview
import com.myapp.feature.knowledge.data.KnowledgeReviewSelector
import com.myapp.feature.knowledge.data.PoolCandidate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 章节列表的展示模型。 */
data class InterviewChapterUi(
    val id: Long,
    val docKey: String,
    val docName: String,
    val title: String,
    val questionCount: Int,
    val inPool: Boolean,
)

/** 一道题的完整内容，阅读页与首页卡片共用。 */
data class InterviewQuestionUi(
    val id: Long,
    val key: String,
    val chapterTitle: String,
    val docKey: String,
    val docName: String,
    val title: String,
    val body: String,
)

/**
 * md 面试题库（PRD 3.7 / 3.8）。
 *
 * 抽题**完全复用 M7 已有的 [KnowledgeReviewSelector]**——那套「没推过的优先，
 * 之后按 1/3/7/15/30 天到期重排」的逻辑与知识源无关，是纯粹的调度算法，
 * 换个候选集就能直接用。这里只负责把题目喂给它、把结果翻译回题目。
 *
 * 复习进度挂在题目的稳定 key 上而不是自增 id 上（见 `InterviewReviewEntity`），
 * 所以 [pickDaily] 里要在 id 与 key 之间来回映射一次。
 */
@Singleton
class InterviewRepository @Inject constructor(
    private val dao: InterviewDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeChapters(): Flow<List<InterviewChapterUi>> =
        dao.observeChapters().map { list -> list.map { it.toUi() } }

    suspend fun setChapterInPool(chapterId: Long, inPool: Boolean): Unit = withContext(io) {
        dao.setChapterInPool(chapterId, inPool, AppTime.now())
    }

    suspend fun setDocInPool(docKey: String, inPool: Boolean): Unit = withContext(io) {
        dao.setDocInPool(docKey, inPool, AppTime.now())
    }

    suspend fun getQuestion(id: Long): InterviewQuestionUi? = withContext(io) {
        val question = dao.getQuestion(id) ?: return@withContext null
        val chapter = dao.getChapter(question.chapterId) ?: return@withContext null
        InterviewQuestionUi(
            id = question.id,
            key = question.key,
            chapterTitle = chapter.title,
            docKey = chapter.docKey,
            docName = chapter.docName,
            title = question.title,
            body = question.body,
        )
    }

    suspend fun questionCount(): Int = withContext(io) { dao.questionCount() }

    /**
     * 今天该看哪道题。池里没有在用的章节时返回 null，
     * 由调用方决定降级到什么（首页卡片降级到笔记，见 KnowledgeRepository）。
     *
     * **有副作用**：选中后立刻记一笔「今天推过了」（[KnowledgeReviewSelector.onShown]），
     * 否则用户不点反馈的话这题明天还在候选池里。写库是幂等的--同一天再调
     * 返回同一条、不重复写，08:00 的通知与首页卡片因此始终一致。
     */
    suspend fun pickDaily(): InterviewQuestionUi? = withContext(io) {
        val candidates = dao.poolCandidates()
        if (candidates.isEmpty()) return@withContext null

        val reviewsByKey = dao.allReviews().associateBy { it.questionKey }
        // 调度器按 Long id 工作，这里把「key -> 进度」翻译成「id -> 进度」
        val reviewsById = candidates.mapNotNull { candidate ->
            reviewsByKey[candidate.key]?.let { review ->
                candidate.id to KnowledgeReview(
                    sourceId = candidate.id,
                    intervalLevel = review.intervalLevel,
                    nextDueAt = review.nextDueAt,
                    lastShownAt = review.lastShownAt,
                )
            }
        }.toMap()

        val today = AppTime.run { today().toEpochMilliAtStartOfDay() }
        val picked = KnowledgeReviewSelector.pickNext(
            candidates = candidates.map { PoolCandidate(sourceId = it.id) },
            reviews = reviewsById,
            today = today,
        ) ?: return@withContext null

        markShown(picked.sourceId, reviewsById[picked.sourceId], today)
        getQuestion(picked.sourceId)
    }

    /** 落「今天推过了」这一笔。已经是今天的记录时 [KnowledgeReviewSelector.onShown] 原样返回，这里就不写库。 */
    private suspend fun markShown(questionId: Long, current: KnowledgeReview?, today: Long) {
        if (current?.lastShownAt == today) return
        val question = dao.getQuestion(questionId) ?: return
        val existing = dao.getReview(question.key)
        val next = KnowledgeReviewSelector.onShown(current, questionId, today)
        dao.upsertReview(
            InterviewReviewEntity(
                id = existing?.id ?: 0L,
                questionKey = question.key,
                intervalLevel = next.intervalLevel,
                nextDueAt = next.nextDueAt,
                lastShownAt = next.lastShownAt,
                updatedAt = AppTime.now(),
            ),
        )
    }

    /** 「已掌握」/「再看看」反馈，推进或重置间隔档位（PRD 3.8）。 */
    suspend fun recordFeedback(questionId: Long, mastered: Boolean): Unit = withContext(io) {
        val question = dao.getQuestion(questionId) ?: return@withContext
        val existing = dao.getReview(question.key)
        val today = AppTime.run { today().toEpochMilliAtStartOfDay() }
        val current = existing?.let {
            KnowledgeReview(
                sourceId = questionId,
                intervalLevel = it.intervalLevel,
                nextDueAt = it.nextDueAt,
                lastShownAt = it.lastShownAt,
            )
        }
        val next = if (mastered) {
            KnowledgeReviewSelector.onMastered(current, questionId, today)
        } else {
            KnowledgeReviewSelector.onSnoozed(current, questionId, today)
        }
        dao.upsertReview(
            InterviewReviewEntity(
                id = existing?.id ?: 0L,
                questionKey = question.key,
                intervalLevel = next.intervalLevel,
                nextDueAt = next.nextDueAt,
                lastShownAt = next.lastShownAt,
                updatedAt = AppTime.now(),
            ),
        )
    }

    private fun InterviewChapterSummary.toUi() = InterviewChapterUi(
        id = id,
        docKey = docKey,
        docName = docName,
        title = title,
        questionCount = questionCount,
        inPool = inPool,
    )
}
