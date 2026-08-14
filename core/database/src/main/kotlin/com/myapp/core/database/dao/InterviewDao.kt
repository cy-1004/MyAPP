package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.myapp.core.database.model.InterviewChapterEntity
import com.myapp.core.database.model.InterviewQuestionEntity
import com.myapp.core.database.model.InterviewReviewEntity
import kotlinx.coroutines.flow.Flow

/** 章节 + 题目的列表投影：列表页只要标题和题数，不需要把 500 道题的正文全读出来。 */
data class InterviewChapterSummary(
    val id: Long,
    @androidx.room.ColumnInfo(name = "chapter_key") val key: String,
    @androidx.room.ColumnInfo(name = "doc_key") val docKey: String,
    @androidx.room.ColumnInfo(name = "doc_name") val docName: String,
    val title: String,
    @androidx.room.ColumnInfo(name = "in_pool") val inPool: Boolean,
    @androidx.room.ColumnInfo(name = "question_count") val questionCount: Int,
)

/** 抽题候选：只要 id/key/排序，不带正文（正文等选中之后再单独查）。 */
data class InterviewQuestionCandidate(
    val id: Long,
    @androidx.room.ColumnInfo(name = "question_key") val key: String,
    @androidx.room.ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Dao
interface InterviewDao {

    // ---- 章节 ----

    /**
     * 章节列表（带题数）。按文档、再按章节内顺序排。
     * `question_count` 走子查询而不是 JOIN + GROUP BY：章节只有二十几行，
     * 子查询更直观，也不用担心 GROUP BY 把 in_pool 之类的列带歪。
     */
    @Query(
        """
        SELECT interview_chapter.id AS id,
               interview_chapter.chapter_key AS chapter_key,
               interview_chapter.doc_key AS doc_key,
               interview_chapter.doc_name AS doc_name,
               interview_chapter.title AS title,
               interview_chapter.in_pool AS in_pool,
               (SELECT COUNT(*) FROM interview_question
                 WHERE interview_question.chapter_id = interview_chapter.id) AS question_count
        FROM interview_chapter
        ORDER BY interview_chapter.doc_key, interview_chapter.sort_order
        """,
    )
    fun observeChapters(): Flow<List<InterviewChapterSummary>>

    @Query("SELECT * FROM interview_chapter")
    suspend fun allChapters(): List<InterviewChapterEntity>

    @Query("SELECT * FROM interview_chapter WHERE id = :id")
    suspend fun getChapter(id: Long): InterviewChapterEntity?

    @Query("UPDATE interview_chapter SET in_pool = :inPool, updated_at = :now WHERE id = :id")
    suspend fun setChapterInPool(id: Long, inPool: Boolean, now: Long)

    /** 整篇文档一起开关，列表页「全选/全不选」用。 */
    @Query("UPDATE interview_chapter SET in_pool = :inPool, updated_at = :now WHERE doc_key = :docKey")
    suspend fun setDocInPool(docKey: String, inPool: Boolean, now: Long)

    // ---- 题目 ----

    /**
     * 抽题候选池：只取在池章节下的题目。
     * 排序保持「文档 → 章节 → 章节内顺序」，让没抽过的题按原书顺序推，读起来有连贯性。
     */
    @Query(
        """
        SELECT interview_question.id AS id,
               interview_question.question_key AS question_key,
               interview_question.sort_order AS sort_order
        FROM interview_question
        JOIN interview_chapter ON interview_question.chapter_id = interview_chapter.id
        WHERE interview_chapter.in_pool = 1
        ORDER BY interview_chapter.doc_key, interview_chapter.sort_order, interview_question.sort_order
        """,
    )
    suspend fun poolCandidates(): List<InterviewQuestionCandidate>

    @Query("SELECT * FROM interview_question WHERE id = :id")
    suspend fun getQuestion(id: Long): InterviewQuestionEntity?

    @Query("SELECT * FROM interview_question WHERE chapter_id = :chapterId ORDER BY sort_order")
    fun observeQuestionsInChapter(chapterId: Long): Flow<List<InterviewQuestionEntity>>

    @Query("SELECT COUNT(*) FROM interview_question")
    suspend fun questionCount(): Int

    // ---- 复习进度 ----

    @Query("SELECT * FROM interview_review")
    suspend fun allReviews(): List<InterviewReviewEntity>

    @Query("SELECT * FROM interview_review WHERE question_key = :questionKey")
    suspend fun getReview(questionKey: String): InterviewReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReview(review: InterviewReviewEntity)

    // ---- 导入 ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<InterviewChapterEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<InterviewQuestionEntity>)

    @Query("DELETE FROM interview_chapter WHERE doc_key = :docKey")
    suspend fun deleteChaptersOfDoc(docKey: String)

    @Query(
        """
        DELETE FROM interview_question
        WHERE chapter_id NOT IN (SELECT id FROM interview_chapter)
        """,
    )
    suspend fun deleteOrphanQuestions()

    /**
     * 重新导入一篇文档：先删旧章节，再插新章节与题目，最后清掉失去归属的题目。
     *
     * 整体放在一个事务里，避免中途失败留下「章节没了题目还在」的半截状态。
     * 复习进度不在这里动——它挂在 `question_key` 上，与本次增删的自增 id 无关，
     * 所以重导之后进度自动接回同名题目（见 [InterviewReviewEntity] 的说明）。
     */
    @Transaction
    suspend fun replaceDoc(
        docKey: String,
        chapters: List<InterviewChapterEntity>,
        questionsByChapterKey: Map<String, List<InterviewQuestionEntity>>,
    ) {
        // 先记下老章节的 in_pool，重新插入时沿用，避免每次导入都把用户的勾选重置
        val previousInPool = allChapters()
            .filter { it.docKey == docKey }
            .associate { it.key to it.inPool }

        deleteChaptersOfDoc(docKey)
        val ids = insertChapters(
            chapters.map { chapter ->
                chapter.copy(inPool = previousInPool[chapter.key] ?: chapter.inPool)
            },
        )
        val questions = chapters.mapIndexed { index, chapter ->
            questionsByChapterKey[chapter.key].orEmpty().map { it.copy(chapterId = ids[index]) }
        }.flatten()
        if (questions.isNotEmpty()) insertQuestions(questions)
        deleteOrphanQuestions()
    }
}
