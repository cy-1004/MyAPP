package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 面试题库的一道题（PRD 3.7），对应 md 里的一个 `##` 标题及其下的正文。
 *
 * [title] 是题干，[body] 是答案原文（保留 markdown：代码块、列表、图片链接都在里面，
 * 阅读页按 markdown 渲染）。`###` 及更深的标题留在 [body] 里，不单独成题。
 *
 * [key] 同 [InterviewChapterEntity.key] 的用意：题库重新导入时靠它认出「还是这道题」，
 * 复习进度（[InterviewReviewEntity]）挂在 key 上而不是自增 id 上，重导不丢进度。
 */
@Entity(
    tableName = "interview_question",
    indices = [
        Index(value = ["question_key"], unique = true),
        Index("chapter_id"),
    ],
)
data class InterviewQuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 列名避开 SQL 保留字 `KEY`，同 [InterviewChapterEntity.key]。 */
    @ColumnInfo(name = "question_key")
    val key: String,

    @ColumnInfo(name = "chapter_id")
    val chapterId: Long,

    val title: String,

    val body: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
