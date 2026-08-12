package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 知识源（PRD 3.7 / 4.2）：一个飞书公开网页链接。
 *
 * 与其它表同遵循全局字段约定（PRD 4.7.7）：`uuid` 跨设备同步预留，
 * `created_at`/`updated_at` 审计，`deleted_at` 软删除支持撤销。
 *
 * `fetchStatus` 存字符串不存枚举（与 [AnniversaryEntity.repeatType] 同一套约定），
 * 取值 "PENDING" / "SUCCESS" / "FAILED" / "LOGIN_REQUIRED"，见
 * `com.myapp.feature.knowledge.data.KnowledgeFetchStatus`。
 *
 * `pinned` 兼「置顶」与「加入知识池」两个语义：M7（每日知识推送）尚未落地，
 * V1 不额外加 `in_pool` 字段，避免用户要在两个开关间选，等 M7 真正消费知识池
 * 时再评估是否要拆开。
 */
@Entity(
    tableName = "knowledge_source",
    indices = [
        Index("uuid", unique = true),
        Index("sort_order"),
        Index("pinned"),
    ],
)
data class KnowledgeSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    val url: String,

    val title: String,

    @ColumnInfo(name = "group_name")
    val groupName: String = "",

    val pinned: Boolean = false,

    val enabled: Boolean = true,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "fetch_status")
    val fetchStatus: String = "PENDING",

    @ColumnInfo(name = "last_fetch_at")
    val lastFetchAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
