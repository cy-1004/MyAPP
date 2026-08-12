package com.myapp.core.database.model

import androidx.room.Entity
import androidx.room.Fts4

/**
 * 疑问全文搜索虚表（PRD 3.5）。
 *
 * 与 [NoteFtsEntity] 同一套模式：FTS4 外部内容表，复用 `question.content`，
 * Room 自动生成同步触发器。只索引 `content`（与此前 LIKE 搜索同口径），
 * 不索引 `answer`/`tags`——量级小，不需要为此扩大范围。
 *
 * **迁移纪律**：CREATE VIRTUAL TABLE 与触发器 SQL 必须与
 * `schemas/6.json` 的 `createSql` 逐字符一致。
 */
@Entity(tableName = "question_fts")
@Fts4(contentEntity = QuestionEntity::class)
data class QuestionFtsEntity(
    val content: String,
)
