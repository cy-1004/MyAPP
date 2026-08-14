package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一道面试题的间隔复习进度（PRD 3.8）。字段语义与 [KnowledgeReviewEntity] 完全一致，
 * 复用同一个纯函数调度器（`KnowledgeReviewSelector`）。
 *
 * 为什么另起一张表而不是给 `knowledge_review` 加一列区分类型：
 * 那张表的 key 是飞书知识源的 id，而面试题的稳定标识是字符串 [questionKey]，
 * 两者类型都不一样，硬塞进一张表要么加冗余列要么改唯一索引，得不偿失。
 * 飞书降级为「只读收藏」后（PRD 3.7）不再参与抽题，`knowledge_review` 自然停止增长，
 * 保留原表只是为了不动已有数据。
 *
 * key 用 [questionKey] 而不是 `interview_question.id`：题库文档更新后会重新导入，
 * 自增 id 会变，挂在 id 上的复习进度会全部失联。
 */
@Entity(
    tableName = "interview_review",
    indices = [Index(value = ["question_key"], unique = true)],
)
data class InterviewReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "question_key")
    val questionKey: String,

    /** 间隔档位下标，对应 `KnowledgeReviewSelector.INTERVAL_DAYS`（1/3/7/15/30 天）。 */
    @ColumnInfo(name = "interval_level")
    val intervalLevel: Int,

    @ColumnInfo(name = "next_due_at")
    val nextDueAt: Long,

    @ColumnInfo(name = "last_shown_at")
    val lastShownAt: Long?,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
