package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

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
 * `pinned` 只管首页快捷入口；`inPool`（M7 新增）单独管是否进入每日知识点的候选池——
 * 两者拆开是因为「想在首页快速打开」和「想每天被随机推送复习」是两件不同的事。
 * 迁移时把老用户已有的 `pinned` 值原样拷给 `inPool`，避免升级后知识池突然清空。
 */
@Serializable
@Entity(
    tableName = "knowledge_source",
    indices = [
        Index("uuid", unique = true),
        Index("sort_order"),
        Index("pinned"),
        Index("in_pool"),
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

    @ColumnInfo(name = "in_pool")
    val inPool: Boolean = false,

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
