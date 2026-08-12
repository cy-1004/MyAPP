package com.myapp.feature.ledger.data

import com.myapp.core.common.time.AppTime
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 统计页派生逻辑测试（PRD 3.6.3）。
 *
 * 趋势图断档会被用户当成 bug（"这个月是不是没记账数据丢了"），
 * 所以补零和月份对齐这两块单独钉死。
 */
class StatisticsInsightsTest {

    @After
    fun tearDown() {
        AppTime.zone = ZoneId.systemDefault()
    }

    @Test
    fun lastMonths_returnsAscendingIncludingCurrentMonth() {
        val today = LocalDate.of(2026, 8, 15)
        val months = StatisticsInsights.lastMonths(6, today)
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 8, 1),
            ),
            months,
        )
    }

    @Test
    fun lastMonths_crossesYearBoundary() {
        val today = LocalDate.of(2026, 1, 5)
        val months = StatisticsInsights.lastMonths(3, today)
        assertEquals(
            listOf(
                LocalDate.of(2025, 11, 1),
                LocalDate.of(2025, 12, 1),
                LocalDate.of(2026, 1, 1),
            ),
            months,
        )
    }

    @Test
    fun fillGaps_fillsMissingMonthsWithZero() {
        val months = listOf(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 8, 1),
        )
        // 7 月没有支出记录，DAO 分组结果里不会出现这个 key
        val byYearMonth = mapOf("2026-06" to 1000_00L, "2026-08" to 500_00L)
        val points = StatisticsInsights.fillGaps(months, byYearMonth)
        assertEquals(
            listOf(1000_00L, 0L, 500_00L),
            points.map { it.totalCents },
        )
    }

    @Test
    fun monthRangeMillis_coversWholeCalendarMonth() {
        AppTime.zone = ZoneOffset.UTC
        val range = StatisticsInsights.monthRangeMillis(LocalDate.of(2026, 2, 1))
        val startDate = LocalDate.ofEpochDay(range.first / 86_400_000L)
        val endDate = LocalDate.ofEpochDay((range.last + 1) / 86_400_000L)
        assertEquals(LocalDate.of(2026, 2, 1), startDate)
        assertEquals(LocalDate.of(2026, 3, 1), endDate)
    }
}
