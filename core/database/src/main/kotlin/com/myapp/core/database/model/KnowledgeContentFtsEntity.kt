package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * 知识正文全文搜索虚表（PRD 3.7）。与 [NoteFtsEntity]/`question_fts` 同一套模式：
 * FTS4 外部内容表，复用 `knowledge_content.content_text`，Room 自动生成同步触发器。
 *
 * 和 `note`/`note_fts` 一样是同一条迁移（`MIGRATION_6_7`）里一起建的新表，
 * 没有历史数据要补，不需要 `question_fts` 那种 `rebuild` 步骤。
 *
 * **迁移纪律**：CREATE VIRTUAL TABLE 与触发器 SQL 必须与
 * `schemas/7.json` 的 `createSql` 逐字符一致。
 */
@Entity(tableName = "knowledge_content_fts")
@Fts4(contentEntity = KnowledgeContentEntity::class)
data class KnowledgeContentFtsEntity(
    @ColumnInfo(name = "content_text")
    val contentText: String,
)
