package com.myapp.core.database.model

import androidx.room.Entity
import androidx.room.Fts4

/**
 * 笔记全文搜索虚表（PRD 3.4 / 4.2）。
 *
 * 用 FTS4 的**外部内容表**模式（`contentEntity = [NoteEntity]`）：
 *   - FTS 索引复用 `note.content`，不重复存储
 *   - Room 自动生成 3 个同步触发器（BeforeUpdate / BeforeDelete / AfterUpdate），
 *     note 表的增删改自动反映到 note_fts
 *   - 检索时 `JOIN note_fts ON note.id = note_fts.rowid WHERE note_fts MATCH :query`
 *
 * 不用独立 FTS 表（save 时双写同步）：手动同步易漏易错，存储翻倍。
 *
 * **迁移纪律**：FTS 虚表的 CREATE VIRTUAL TABLE 与触发器 SQL 必须与
 * `schemas/3.json` 的 `createSql` 逐字符一致，包括触发器名大小写与 `docid` 关键字。
 */
@Entity(tableName = "note_fts")
@Fts4(contentEntity = NoteEntity::class)
data class NoteFtsEntity(
    val content: String,
)
