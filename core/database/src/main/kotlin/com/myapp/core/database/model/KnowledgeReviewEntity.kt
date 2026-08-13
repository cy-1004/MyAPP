package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 知识点间隔复习状态（PRD 3.8）。
 *
 * 一行对应一个 `(sourceId, sectionIndex)`——V1 不做章节切分，`sectionIndex` 固定 0，
 * 字段留着给未来的按章节复习用（同 [KnowledgeContentEntity] 的理由）。
 *
 * `intervalLevel` 是 [com.myapp.feature.knowledge.data.INTERVAL_DAYS] 的下标：
 * 「已掌握」进一级，封顶在最后一档后原地循环（不退出知识池，PRD 的间隔复习是
 * 长期低频复现，不是学完就消失——不然全部标完知识池会变空）。
 * 「再看看」回到第 0 档，`nextDueAt` 设成明天。
 *
 * 没有对应行 = 这个知识源从没被推送过，选择算法里优先级最高。
 */
@Serializable
@Entity(
    tableName = "knowledge_review",
    indices = [Index("source_id", "section_index", unique = true)],
)
data class KnowledgeReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_id")
    val sourceId: Long,

    @ColumnInfo(name = "section_index")
    val sectionIndex: Int = 0,

    @ColumnInfo(name = "interval_level")
    val intervalLevel: Int = 0,

    @ColumnInfo(name = "next_due_at")
    val nextDueAt: Long,

    @ColumnInfo(name = "last_shown_at")
    val lastShownAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
