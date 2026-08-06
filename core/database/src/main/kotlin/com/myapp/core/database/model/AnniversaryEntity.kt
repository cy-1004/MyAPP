package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 纪念日（PRD 3.2）。
 *
 * 日期存 **epochDay**（1970-01-01 起的天数）而不是 epochMilli：纪念日只有「日」的语义，
 * 存毫秒会带上一个假的时刻，跨时区或夏令时时可能整体偏一天。
 *
 * [isLunar] 为真时，[date] 仍存**用户输入的那一天的公历日期**，农历月日在读取时换算得出。
 * 这样只需要一个日期字段，且用户改主意切换公历/农历时不用重新输入。
 */
@Entity(
    tableName = "anniversary",
    indices = [
        Index("date"),
        Index("uuid", unique = true),
    ],
)
data class AnniversaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    val title: String,

    /** epochDay。 */
    val date: Long,

    @ColumnInfo(name = "is_lunar")
    val isLunar: Boolean = false,

    /**
     * 重复类型："ONCE" 一次性 / "YEARLY" 每年 / "CUMULATIVE" 累计天数。
     * 存字符串而非枚举，后续加新类型（如每月）不用做数据库迁移。
     */
    @ColumnInfo(name = "repeat_type")
    val repeatType: String = "YEARLY",

    /** 提前几天提醒；0 表示当天。 */
    @ColumnInfo(name = "remind_days_before")
    val remindDaysBefore: Int = 1,

    val note: String? = null,

    /** 置顶：首页卡片与小组件默认盯的那一个（PRD 3.10 W4）。 */
    val pinned: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
