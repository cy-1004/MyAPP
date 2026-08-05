package com.myapp.core.common.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 时间工具。
 *
 * 约定：数据库一律存 epochMilli（UTC），只在展示层转成本地时间。
 * 这样跨时区、夏令时都不会出问题，也便于将来做同步。
 */
object AppTime {

    /** 可注入替换的时钟，便于测试与「模拟某一天」的调试。 */
    var zone: ZoneId = ZoneId.systemDefault()

    fun now(): Long = System.currentTimeMillis()

    fun today(): LocalDate = LocalDate.now(zone)

    fun Long.toLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

    fun Long.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()

    fun LocalDate.toEpochMilliAtStartOfDay(): Long =
        atStartOfDay(zone).toInstant().toEpochMilli()

    fun LocalDate.toEpochMilliAtEndOfDay(): Long =
        atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

    fun LocalDateTime.toEpochMilli(): Long =
        atZone(zone).toInstant().toEpochMilli()

    /** 今日 [起, 止) 毫秒区间，用于「今日待办」「今日支出」这类按天查询。 */
    fun todayRange(): LongRange {
        val start = today().toEpochMilliAtStartOfDay()
        val end = today().plusDays(1).toEpochMilliAtStartOfDay()
        return start until end
    }

    /** 距今天数：正数表示未来，负数表示过去。用于纪念日倒数。 */
    fun daysFromToday(date: LocalDate): Long =
        ChronoUnit.DAYS.between(today(), date)

    fun isToday(epochMilli: Long): Boolean = epochMilli.toLocalDate() == today()

    fun isOverdue(epochMilli: Long): Boolean = epochMilli < now()
}

object AppFormatters {
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
    val dateWithYear: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
}

/** 人类可读的相对时间，用于列表时间戳。 */
fun Long.asRelativeText(now: Long = AppTime.now()): String {
    val diff = Duration.ofMillis(now - this)
    return when {
        diff.toMinutes() < 1 -> "刚刚"
        diff.toMinutes() < 60 -> "${diff.toMinutes()} 分钟前"
        diff.toHours() < 24 -> "${diff.toHours()} 小时前"
        diff.toDays() < 7 -> "${diff.toDays()} 天前"
        else -> with(AppTime) { toLocalDate().format(AppFormatters.date) }
    }
}
