package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 疑问（PRD 3.5）。
 *
 * 与笔记的定位区分：笔记是「已知的沉淀」，疑问是「未解的待办知识」。
 * 解决后可通过 [com.myapp.core.common.contract.NoteWriter] 一键转为笔记。
 *
 * 与 [NoteEntity] 同遵循全局字段约定（PRD 4.7.7）：
 *   - `uuid` / `created_at` / `updated_at` / `deleted_at`：审计、软删除 tombstone
 *   - `resolved_at`：进入 RESOLVED 状态的时刻，列表按此对已解决组排序
 *
 * `status` 存 TEXT 字符串而非枚举（"OPEN" / "RESOLVED" / "ARCHIVED"），
 * 与 [AnniversaryEntity.repeatType] 同一套约定--加新状态不用做数据库迁移。
 *
 * PRD 4.2 的 schema 里没列 `updated_at` / `deleted_at`，但全局字段约定优先
 * （PRD 4.2 紧接着就声明了 uuid / created_at / updated_at / deleted_at 约定）。
 * 疑问支持撤销删除（与笔记同），故需要 `deleted_at`。
 */
@Entity(
    tableName = "question",
    indices = [
        Index("uuid", unique = true),
        Index("status"),
        Index("updated_at"),
    ],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    /** 疑问正文。必填。 */
    val content: String,

    /** 来源上下文（在哪里遇到的），可空。 */
    val context: String? = null,

    /** 逗号分隔的标签，与 [NoteEntity.tags] 同口径。 */
    val tags: String = "",

    /** "OPEN" | "RESOLVED" | "ARCHIVED"，见 [com.myapp.feature.question.data.QuestionStatus]。 */
    val status: String = "OPEN",

    /** 答案正文，解决时填写。OPEN 状态下可为 null。 */
    val answer: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null,
)
