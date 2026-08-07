package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 笔记（PRD 3.4）。
 *
 * 与 [TodoEntity] 一样遵循全局字段约定（PRD 4.7.7）：
 *   - `uuid`：跨设备同步预留
 *   - `created_at` / `updated_at`：审计与冲突解决
 *   - `deleted_at`：软删除 tombstone，支持撤销删除（Snackbar 无损恢复）
 *
 * PRD 4.2 的表结构里没有 `deleted_at`，但与全局约定冲突时以约定为准--
 * 笔记是高频误删对象，撤销价值高于字段一致。
 *
 * 不设独立 `title` 字段：PRD 4.2 只有 `content`，列表展示时取首行非空文本作标题。
 * 加 title 字段会引入冗余迁移与「首行 vs title」的不一致问题。
 */
@Entity(
    tableName = "note",
    indices = [
        Index("uuid", unique = true),
        Index("pinned"),
        Index("updated_at"),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    /** Markdown 正文。FTS 索引此列以支持全文搜索。 */
    val content: String,

    /** 逗号分隔的标签。标签量级小，不值得单开关联表（参考 [TodoEntity.tags]）。 */
    val tags: String = "",

    /**
     * 图片相对路径列表，用 `` 分隔（文件名禁用字符，不会与路径冲突）。
     * 用 [com.myapp.core.database.Converters] 在 List<String> <-> String 间转换。
     */
    @ColumnInfo(name = "images_json")
    val imagesJson: String = "",

    val pinned: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
