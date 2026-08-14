package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * 某一天的身体情况记录（PRD 3.2「每日异常记录」）。
 *
 * **刻意与 [PeriodRecordEntity] 分开、也不挂在它下面**：排卵期出血、经期之外的异常
 * 同样要能记，挂在「某一次月经」下面的话这些日子就没地方放。
 * 两张表各有各的生命周期，靠日期在 UI 层对齐即可。
 *
 * [date] 上有唯一索引：一天只有一条，重复记录就是覆盖同一条。
 *
 * 没有 `deleted_at`：这条记录本身就是「用户手写的一句话」，删掉即删掉，
 * 不像经期记录那样删错了会让整条周期统计错位、需要撤销。
 */
@Serializable
@Entity(
    tableName = "period_day_log",
    indices = [
        Index("log_date", unique = true),
        Index("uuid", unique = true),
    ],
)
data class PeriodDayLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    /** epochDay。与其它日期字段同口径。 */
    @ColumnInfo(name = "log_date")
    val date: Long,

    /** 预置标签 id，逗号分隔（与 `note.tags` 同一套约定）。可为空串。 */
    val tags: String = "",

    /** 自由文本，可为空。 */
    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
