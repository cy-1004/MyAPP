package com.myapp.feature.todo.data

import com.myapp.core.common.time.AppTime
import java.time.LocalDateTime

/**
 * 重复规则（PRD 3.3）。
 *
 * 数据库里存字符串而不是枚举，是为了将来加新规则类型时不用做数据库迁移——
 * 解析失败一律当作「不重复」，不抛异常。宁可少生成一条，也不能因为一条脏数据崩掉。
 *
 * 目前支持的格式：
 *   - `DAILY`            每天
 *   - `WEEKLY:1,3,5`     每周一/三/五（ISO 星期，1=周一 … 7=周日）
 *   - `INTERVAL:14`      每 14 天
 */
object RepeatRule {

    const val NONE = ""
    const val DAILY = "DAILY"
    const val WEEKDAYS = "WEEKLY:1,2,3,4,5"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY_INTERVAL = "INTERVAL:30"

    /** 展示用文案。未知规则原样返回，至少让人看得见它存在。 */
    fun describe(rule: String?): String = when {
        rule.isNullOrBlank() -> "不重复"
        rule == DAILY -> "每天"
        rule == WEEKDAYS -> "工作日"
        rule.startsWith("WEEKLY:") -> {
            val days = rule.removePrefix("WEEKLY:").split(",").mapNotNull { it.trim().toIntOrNull() }
            if (days.isEmpty()) rule else "每周" + days.sorted().joinToString("、") { weekdayChar(it) }
        }
        rule.startsWith("INTERVAL:") -> {
            val n = rule.removePrefix("INTERVAL:").toIntOrNull()
            if (n == null || n <= 0) rule else "每 $n 天"
        }
        else -> rule
    }

    /**
     * 算出 [from] 之后的下一次截止时间，保持原有的时分。
     * 返回 null 表示规则无效或不重复——调用方据此决定不再生成下一条。
     */
    fun nextDueAt(rule: String?, from: Long): Long? {
        if (rule.isNullOrBlank()) return null
        val base: LocalDateTime = with(AppTime) { from.toLocalDateTime() }

        val next = when {
            rule == DAILY -> base.plusDays(1)

            rule.startsWith("WEEKLY:") -> {
                val days = rule.removePrefix("WEEKLY:")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }
                    .toSortedSet()
                if (days.isEmpty()) return null
                // 从次日起最多找 7 天，必然命中——集合非空且覆盖一周内的某一天
                (1..7).asSequence()
                    .map { base.plusDays(it.toLong()) }
                    .first { it.dayOfWeek.value in days }
            }

            rule.startsWith("INTERVAL:") -> {
                val n = rule.removePrefix("INTERVAL:").toIntOrNull()
                if (n == null || n <= 0) return null
                base.plusDays(n.toLong())
            }

            else -> return null
        }

        return with(AppTime) { next.toEpochMilli() }
    }

    private fun weekdayChar(isoDay: Int): String = when (isoDay) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        else -> "日"
    }
}
