package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * 经期记录（PRD 3.2）。
 *
 * 与 [AnniversaryEntity] 同理，日期存 epochDay。
 * [endDate] 为空表示「进行中」——这是刻意允许的状态，记录开始时不该逼用户先填结束日。
 */
@Serializable
@Entity(
    tableName = "period_record",
    indices = [
        Index("start_date"),
        Index("uuid", unique = true),
    ],
)
data class PeriodRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    /** epochDay。 */
    @ColumnInfo(name = "start_date")
    val startDate: Long,

    /** epochDay；为空表示进行中。 */
    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,

    /** 痛经 / 量 / 情绪等自由记录。 */
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
