package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 面试题库的一章（PRD 3.7），对应 md 里的一个 `#` 标题，如「一、基础篇」「五、Redis」。
 *
 * **知识池的开关粒度就是章节**：500 道题一条条勾不现实，
 * 但「这阵子只想复习 Redis 和 Spring」是真实需求，章节这一级刚好。
 *
 * [key] 是稳定标识（`docKey + 章节标题` 的规范化结果），不是自增 id——
 * 题库文档以后会更新、重新导入，自增 id 每次都会变，
 * 用它当「这一章是不是同一章」的判据会让 [inPool] 每次导入都被重置。
 */
@Entity(
    tableName = "interview_chapter",
    indices = [
        Index(value = ["chapter_key"], unique = true),
        Index("doc_key"),
    ],
)
data class InterviewChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * 稳定标识，跨重新导入不变。
     * 列名叫 `chapter_key` 而不是 `key`：`KEY` 是 SQL 保留字，
     * 用它当列名的话每条 query 里都得加反引号，迟早会漏一个。
     */
    @ColumnInfo(name = "chapter_key")
    val key: String,

    /** 属于哪篇文档（`backend` / `llm`），同时也是 assets 子目录名。 */
    @ColumnInfo(name = "doc_key")
    val docKey: String,

    /** 文档显示名，如「后端」「大模型」。 */
    @ColumnInfo(name = "doc_name")
    val docName: String,

    val title: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    /** 是否参与每日抽题。导入时默认全部加入，用户可按章关掉。 */
    @ColumnInfo(name = "in_pool")
    val inPool: Boolean = true,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
