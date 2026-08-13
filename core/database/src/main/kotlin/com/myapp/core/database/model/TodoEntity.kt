package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * 待办（PRD 3.3）。
 *
 * 两个全局约定（PRD 4.7.7），所有实体都要遵守：
 *   - `uuid`：为将来跨设备同步预留的全局唯一标识，现在就要有，后补代价极大；
 *   - `createdAt` / `updatedAt`：审计与同步冲突解决的基础。
 * 需要软删除的表另加 `deletedAt`（同步需要 tombstone，硬删除会导致删除操作无法传播）。
 */
@Serializable
@Entity(
    tableName = "todo",
    indices = [
        Index("due_at"),
        Index("done"),
        Index("uuid", unique = true),
    ],
)
data class TodoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    val title: String,

    val note: String? = null,

    /** 截止时间，epochMilli。为空表示无期限。 */
    @ColumnInfo(name = "due_at")
    val dueAt: Long? = null,

    /** 0 低 / 1 中 / 2 高 */
    val priority: Int = 1,

    /** 逗号分隔，标签量级很小，不值得单开关联表。 */
    val tags: String = "",

    /**
     * 重复规则，如 "DAILY" / "WEEKLY:1,3,5" / "INTERVAL:14"。
     * 存字符串而非枚举，便于后续扩展新规则类型而不用做数据库迁移。
     */
    @ColumnInfo(name = "repeat_rule")
    val repeatRule: String? = null,

    val done: Boolean = false,

    @ColumnInfo(name = "done_at")
    val doneAt: Long? = null,

    /** 完成时填写的备注（PRD 3.3）。为空表示无备注。撤销完成时清空。 */
    @ColumnInfo(name = "completion_note")
    val completionNote: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
