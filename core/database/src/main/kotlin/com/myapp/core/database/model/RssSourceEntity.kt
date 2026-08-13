package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * RSS/Atom 订阅源（PRD 3.9）。字段形状与 [KnowledgeSourceEntity] 同一套约定
 * （uuid 同步预留、created/updated_at 审计、deleted_at 软删除支持撤销）。
 */
@Serializable
@Entity(
    tableName = "rss_source",
    indices = [
        Index("uuid", unique = true),
        Index("sort_order"),
    ],
)
data class RssSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    val url: String,

    val title: String,

    @ColumnInfo(name = "group_name")
    val groupName: String = "",

    val enabled: Boolean = true,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "last_fetch_at")
    val lastFetchAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
