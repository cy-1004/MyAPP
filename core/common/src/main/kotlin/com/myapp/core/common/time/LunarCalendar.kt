package com.myapp.core.common.time

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 农历换算（PRD 3.2：生日常用农历，且**不联网**）。
 *
 * 为什么自己实现而不引三方库：农历换算的本质就是一张查表 + 几十行位运算，
 * 引一个库进来反而多一份供应链风险和体积。表的正确性由 LunarCalendarTest
 * 用 1900~2100 的已知春节日期与闰月逐年校验。
 *
 * 数据结构说明——[LUNAR_INFO] 每年一个 20 位整数：
 *   - bit 16      ：闰月是否为 30 天（1 = 30 天，0 = 29 天）
 *   - bit 15..4   ：正月到十二月，每月 1 位，1 = 30 天，0 = 29 天
 *   - bit 3..0    ：该年闰几月，0 = 不闰
 */
object LunarCalendar {

    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2100

    /** 1900 年正月初一对应的公历日期，全部换算以此为原点。 */
    private val BASE_DATE: LocalDate = LocalDate.of(1900, 1, 31)

    private val LUNAR_INFO = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252, // 2090-2099
        0x0d520, // 2100
    )

    /** 该农历年闰几月；0 表示当年不闰。 */
    fun leapMonth(year: Int): Int = info(year) and 0xf

    /** 闰月的天数；当年不闰时为 0。 */
    fun leapMonthDays(year: Int): Int = when {
        leapMonth(year) == 0 -> 0
        info(year) and 0x10000 != 0 -> 30
        else -> 29
    }

    /** 平月天数，[month] 取 1..12。 */
    fun monthDays(year: Int, month: Int): Int =
        if (info(year) and (0x10000 shr month) != 0) 30 else 29

    /** 整个农历年的天数（含闰月）。 */
    fun yearDays(year: Int): Int {
        var sum = 348 // 12 个月先按 29 天算
        var mask = 0x8000
        while (mask > 0x8) {
            if (info(year) and mask != 0) sum++
            mask = mask shr 1
        }
        return sum + leapMonthDays(year)
    }

    /** 公历 → 农历。超出 [MIN_YEAR]~[MAX_YEAR] 会抛异常，调用方应先用 [isSupported] 判断。 */
    fun fromSolar(date: LocalDate): LunarDate {
        require(isSupported(date)) { "农历换算仅支持 $MIN_YEAR-01-31 ~ $MAX_YEAR 年末，传入 $date" }

        var offset = ChronoUnit.DAYS.between(BASE_DATE, date).toInt()

        var year = MIN_YEAR
        while (true) {
            val days = yearDays(year)
            if (offset < days) break
            offset -= days
            year++
        }

        // 按「正月…leap 月、闰 leap 月、leap+1 月…十二月」的真实顺序逐月扣减
        val leap = leapMonth(year)
        var month = 1
        var isLeap = false
        while (true) {
            val days = if (isLeap) leapMonthDays(year) else monthDays(year, month)
            if (offset < days) break
            offset -= days
            when {
                isLeap -> { isLeap = false; month++ }
                month == leap -> isLeap = true
                else -> month++
            }
        }

        return LunarDate(year = year, month = month, day = offset + 1, isLeapMonth = isLeap)
    }

    /** 农历 → 公历。 */
    fun toSolar(year: Int, month: Int, day: Int, isLeapMonth: Boolean = false): LocalDate {
        require(year in MIN_YEAR..MAX_YEAR) { "农历年份超出支持范围：$year" }
        require(month in 1..12) { "农历月份非法：$month" }
        require(!isLeapMonth || leapMonth(year) == month) { "${year}年没有闰${month}月" }

        var offset = 0
        for (y in MIN_YEAR until year) offset += yearDays(y)

        val leap = leapMonth(year)
        var m = 1
        var inLeap = false
        while (m != month || inLeap != isLeapMonth) {
            offset += if (inLeap) leapMonthDays(year) else monthDays(year, m)
            when {
                inLeap -> { inLeap = false; m++ }
                m == leap -> inLeap = true
                else -> m++
            }
        }

        return BASE_DATE.plusDays((offset + day - 1).toLong())
    }

    /**
     * 从 [from] 起（含当天）该农历月日的下一次公历日期。
     *
     * 两个必须容错的情况，否则农历生日每隔几年就会「消失」：
     *   - 当年没有对应的闰月 → 退回同月份的平月；
     *   - 该月只有 29 天而生日是三十 → 落到当月最后一天。
     */
    fun nextSolarOccurrence(
        month: Int,
        day: Int,
        isLeapMonth: Boolean,
        from: LocalDate,
    ): LocalDate? {
        if (!isSupported(from)) return null
        var year = fromSolar(from).year
        while (year <= MAX_YEAR) {
            // 当年无此闰月就退回平月——用平月过生日是民间通行做法
            val useLeap = isLeapMonth && leapMonth(year) == month
            val length = if (useLeap) leapMonthDays(year) else monthDays(year, month)
            val solar = toSolar(year, month, day.coerceAtMost(length), useLeap)
            if (!solar.isBefore(from)) return solar
            year++
        }
        return null
    }

    fun isSupported(date: LocalDate): Boolean =
        !date.isBefore(BASE_DATE) && date.year <= MAX_YEAR

    private fun info(year: Int): Int {
        require(year in MIN_YEAR..MAX_YEAR) { "农历年份超出支持范围：$year" }
        return LUNAR_INFO[year - MIN_YEAR]
    }
}

/** 一个农历日期。[isLeapMonth] 为真表示这是闰月，如「闰四月初八」。 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
) {
    /** 如「闰四月初八」，不含年份——纪念日展示里年份没有意义。 */
    fun format(): String = buildString {
        if (isLeapMonth) append("闰")
        append(MONTH_NAMES[month - 1])
        append("月")
        append(dayName(day))
    }

    fun formatWithYear(): String = "${year}年${format()}"

    private companion object {
        val MONTH_NAMES = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        val DIGITS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九", "十")

        fun dayName(day: Int): String = when {
            day <= 10 -> "初${DIGITS[day - 1]}"
            day < 20 -> "十${DIGITS[day - 11]}"
            day == 20 -> "二十"
            day < 30 -> "廿${DIGITS[day - 21]}"
            else -> "三十"
        }
    }
}
