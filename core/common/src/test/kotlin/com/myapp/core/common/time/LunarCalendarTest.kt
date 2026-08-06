package com.myapp.core.common.time

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 农历表的正确性校验。
 *
 * 这个测试的价值在于：农历换算错了在 UI 上看不出来（日期照样显示，只是错的），
 * 只有拿已知的春节日期与闰月逐年比对才能发现。表一旦被改动，这里必须全绿。
 */
class LunarCalendarTest {

    /** 已知春节（正月初一）公历日期。 */
    private val springFestivals = mapOf(
        1949 to LocalDate.of(1949, 1, 29),
        1970 to LocalDate.of(1970, 2, 6),
        1980 to LocalDate.of(1980, 2, 16),
        1990 to LocalDate.of(1990, 1, 27),
        2000 to LocalDate.of(2000, 2, 5),
        2001 to LocalDate.of(2001, 1, 24),
        2002 to LocalDate.of(2002, 2, 12),
        2003 to LocalDate.of(2003, 2, 1),
        2004 to LocalDate.of(2004, 1, 22),
        2005 to LocalDate.of(2005, 2, 9),
        2006 to LocalDate.of(2006, 1, 29),
        2007 to LocalDate.of(2007, 2, 18),
        2008 to LocalDate.of(2008, 2, 7),
        2009 to LocalDate.of(2009, 1, 26),
        2010 to LocalDate.of(2010, 2, 14),
        2011 to LocalDate.of(2011, 2, 3),
        2012 to LocalDate.of(2012, 1, 23),
        2013 to LocalDate.of(2013, 2, 10),
        2014 to LocalDate.of(2014, 1, 31),
        2015 to LocalDate.of(2015, 2, 19),
        2016 to LocalDate.of(2016, 2, 8),
        2017 to LocalDate.of(2017, 1, 28),
        2018 to LocalDate.of(2018, 2, 16),
        2019 to LocalDate.of(2019, 2, 5),
        2020 to LocalDate.of(2020, 1, 25),
        2021 to LocalDate.of(2021, 2, 12),
        2022 to LocalDate.of(2022, 2, 1),
        2023 to LocalDate.of(2023, 1, 22),
        2024 to LocalDate.of(2024, 2, 10),
        2025 to LocalDate.of(2025, 1, 29),
        2026 to LocalDate.of(2026, 2, 17),
        2027 to LocalDate.of(2027, 2, 6),
        2028 to LocalDate.of(2028, 1, 26),
        2029 to LocalDate.of(2029, 2, 13),
        2030 to LocalDate.of(2030, 2, 3),
        2031 to LocalDate.of(2031, 1, 23),
        2032 to LocalDate.of(2032, 2, 11),
        2033 to LocalDate.of(2033, 1, 31),
        2034 to LocalDate.of(2034, 2, 19),
        2035 to LocalDate.of(2035, 2, 8),
    )

    /** 已知闰月：农历年 → 闰几月。 */
    private val knownLeapMonths = mapOf(
        2001 to 4,
        2004 to 2,
        2006 to 7,
        2009 to 5,
        2012 to 4,
        2014 to 9,
        2017 to 6,
        2020 to 4,
        2023 to 2,
        2025 to 6,
        2028 to 5,
        2031 to 3,
        2033 to 11,
    )

    @Test
    fun `农历新年换算为已知的公历日期`() {
        springFestivals.forEach { (year, solar) ->
            assertEquals("${year}年春节", solar, LunarCalendar.toSolar(year, 1, 1))
        }
    }

    @Test
    fun `已知春节当天反查为正月初一`() {
        springFestivals.forEach { (year, solar) ->
            val lunar = LunarCalendar.fromSolar(solar)
            assertEquals("${solar} 的农历年", year, lunar.year)
            assertEquals("${solar} 的农历月", 1, lunar.month)
            assertEquals("${solar} 的农历日", 1, lunar.day)
            assertEquals("${solar} 不应是闰月", false, lunar.isLeapMonth)
        }
    }

    @Test
    fun `闰月与已知记录一致`() {
        knownLeapMonths.forEach { (year, month) ->
            assertEquals("${year}年闰月", month, LunarCalendar.leapMonth(year))
        }
        // 未列出的年份不应凭空冒出闰月：闰月约每 19 年 7 次，密度校验
        val leapCount = (2000..2035).count { LunarCalendar.leapMonth(it) != 0 }
        assertEquals(knownLeapMonths.count { it.key in 2000..2035 }, leapCount)
    }

    @Test
    fun `几个已知的传统节日`() {
        // 2024 端午（五月初五）
        assertEquals(LocalDate.of(2024, 6, 10), LunarCalendar.toSolar(2024, 5, 5))
        // 2024 中秋（八月十五）
        assertEquals(LocalDate.of(2024, 9, 17), LunarCalendar.toSolar(2024, 8, 15))
        // 2025 中秋
        assertEquals(LocalDate.of(2025, 10, 6), LunarCalendar.toSolar(2025, 8, 15))
    }

    /**
     * 全量往返校验：1900-01-31 ~ 2100-12-31 每一天转成农历再转回来必须相等。
     * 这条能抓出逐月扣减逻辑里的任何错位（闰月顺序、月长判断）。
     */
    @Test
    fun `全范围公历农历往返一致`() {
        var date = LocalDate.of(1900, 1, 31)
        val end = LocalDate.of(2100, 12, 31)
        var count = 0
        while (!date.isAfter(end)) {
            val lunar = LunarCalendar.fromSolar(date)
            val back = LunarCalendar.toSolar(lunar.year, lunar.month, lunar.day, lunar.isLeapMonth)
            assertEquals("往返不一致：$date -> ${lunar.formatWithYear()}", date, back)
            date = date.plusDays(1)
            count++
        }
        assertTrue("覆盖天数异常：$count", count > 70_000)
    }

    @Test
    fun `农历日期格式化`() {
        assertEquals("正月初一", LunarCalendar.fromSolar(LocalDate.of(2024, 2, 10)).format())
        assertEquals("八月十五", LunarCalendar.fromSolar(LocalDate.of(2024, 9, 17)).format())
        // 2023 闰二月初一
        assertEquals("闰二月初一", LunarCalendar.fromSolar(LocalDate.of(2023, 3, 22)).format())
    }

    @Test
    fun `下一次农历生日 当年无闰月时退回平月`() {
        // 生日是 2023 年闰二月初八；2024 年没有闰二月，应落在平二月初八
        val from = LocalDate.of(2024, 1, 1)
        val next = LunarCalendar.nextSolarOccurrence(month = 2, day = 8, isLeapMonth = true, from = from)
        assertEquals(LunarCalendar.toSolar(2024, 2, 8, isLeapMonth = false), next)
    }

    @Test
    fun `下一次农历生日 三十落在小月时取当月最后一天`() {
        // 找一个当年该月只有 29 天的情况：换算结果的「日」不应超出该月长度
        val next = LunarCalendar.nextSolarOccurrence(
            month = 9,
            day = 30,
            isLeapMonth = false,
            from = LocalDate.of(2026, 1, 1),
        )
        requireNotNull(next)
        val lunar = LunarCalendar.fromSolar(next)
        assertEquals(9, lunar.month)
        assertEquals(LunarCalendar.monthDays(lunar.year, 9), lunar.day)
    }

    @Test
    fun `超出支持范围返回空而不是崩溃`() {
        assertNull(
            LunarCalendar.nextSolarOccurrence(1, 1, false, LocalDate.of(1899, 1, 1)),
        )
    }
}
