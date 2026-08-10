package com.myapp.core.common.time

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 纪念日的日期计算（PRD 3.2）。
 *
 * 从 :feature:anniversary 提取到 core：桌面小组件 W4 也要算「下一次是哪天」，
 * 而 feature 之间禁止互相依赖（PRD 4.7.1）。纯日期数学，零依赖，JVM 可测。
 */

/** 累计型纪念日的下一个里程碑。 */
data class Milestone(val date: LocalDate, val label: String)

object AnniversaryCalculator {

    /**
     * 每年重复的下一次公历日期。
     *
     * 农历要走换算：农历生日对应的公历日期每年都不同，直接把年份换掉是错的。
     * 换算失败（超出 1900~2100）时退回公历同月日，保证 UI 永远有个合理结果。
     */
    fun nextYearlyDate(origin: LocalDate, today: LocalDate, isLunar: Boolean): LocalDate {
        if (isLunar && LunarCalendar.isSupported(origin)) {
            val lunar = LunarCalendar.fromSolar(origin)
            LunarCalendar.nextSolarOccurrence(
                month = lunar.month,
                day = lunar.day,
                isLeapMonth = lunar.isLeapMonth,
                from = today,
            )?.let { return it }
        }
        // withYear 会把 2 月 29 日自动收敛到 2 月 28 日，正是想要的行为
        val thisYear = origin.withYear(today.year)
        return if (thisYear.isBefore(today)) origin.withYear(today.year + 1) else thisYear
    }

    /**
     * 累计型的下一个里程碑：**整百天与周年取更近的那个**。
     *
     * 只倒数整百天会漏掉「三周年」这种更有意义的节点，
     * 只倒数周年又会错过「在一起 1000 天」，两者都要。
     */
    fun nextMilestone(origin: LocalDate, today: LocalDate, elapsed: Long): Milestone {
        // 当天算第 1 天，所以第 N 天对应 origin.plusDays(N - 1)
        val currentDayNumber = elapsed + 1
        val nextHundred = (currentDayNumber / 100 + 1) * 100
        val hundredDate = origin.plusDays(nextHundred - 1)

        val passedYears = ChronoUnit.YEARS.between(origin, today)
        val nextYears = passedYears + 1
        val yearDate = origin.plusYears(nextYears)

        return if (!hundredDate.isAfter(yearDate)) {
            Milestone(hundredDate, "$nextHundred 天")
        } else {
            Milestone(yearDate, "$nextYears 周年")
        }
    }
}
