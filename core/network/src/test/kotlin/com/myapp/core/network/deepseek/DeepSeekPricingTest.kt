package com.myapp.core.network.deepseek

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 峰谷时段判定（PRD 3.14）。
 *
 * 这套边界只能靠测试守住：UI 上「置灰」与「不置灰」差一个小时，
 * 肉眼看不出来是错的，只会表现为「白天某个时候莫名其妙能点/不能点」。
 */
class DeepSeekPricingTest {

    private fun utc(text: String) = Instant.parse(text)

    @Test
    fun `峰价窗口左闭右开`() {
        // 01:00 整已经进入峰价
        assertTrue(DeepSeekPricing.isPeak(utc("2026-08-20T01:00:00Z")))
        assertTrue(DeepSeekPricing.isPeak(utc("2026-08-20T03:59:59Z")))
        // 04:00 整已经回到谷价
        assertFalse(DeepSeekPricing.isPeak(utc("2026-08-20T04:00:00Z")))
    }

    @Test
    fun `两个峰价窗口之间的 04 到 06 点是谷价`() {
        assertFalse(DeepSeekPricing.isPeak(utc("2026-08-20T05:30:00Z")))
        assertTrue(DeepSeekPricing.isPeak(utc("2026-08-20T06:00:00Z")))
    }

    @Test
    fun `第二个窗口 10 点结束`() {
        assertTrue(DeepSeekPricing.isPeak(utc("2026-08-20T09:59:59Z")))
        assertFalse(DeepSeekPricing.isPeak(utc("2026-08-20T10:00:00Z")))
    }

    @Test
    fun `深夜与傍晚都是谷价`() {
        assertFalse(DeepSeekPricing.isPeak(utc("2026-08-20T00:30:00Z")))
        assertFalse(DeepSeekPricing.isPeak(utc("2026-08-20T18:00:00Z")))
        assertFalse(DeepSeekPricing.isPeak(utc("2026-08-20T23:59:59Z")))
    }

    @Test
    fun `北京时间的白天大半落在峰价里`() {
        // UTC+8：北京 10:00 = UTC 02:00（峰）、北京 16:00 = UTC 08:00（峰）
        assertTrue(DeepSeekPricing.isPeak(utc("2026-08-20T02:00:00Z")))
        assertTrue(DeepSeekPricing.isPeak(utc("2026-08-20T08:00:00Z")))
        // 北京 20:00 = UTC 12:00（谷）——晚上用是便宜的
        assertFalse(DeepSeekPricing.isPeak(utc("2026-08-20T12:00:00Z")))
    }

    @Test
    fun `谷价时段问下一次谷价起点就是当下`() {
        val now = utc("2026-08-20T12:34:56Z")
        assertEquals(now, DeepSeekPricing.nextOffPeakStart(now))
    }

    @Test
    fun `峰价时段给出所在窗口的结束时刻`() {
        assertEquals(
            utc("2026-08-20T04:00:00Z"),
            DeepSeekPricing.nextOffPeakStart(utc("2026-08-20T02:15:30Z")),
        )
        assertEquals(
            utc("2026-08-20T10:00:00Z"),
            DeepSeekPricing.nextOffPeakStart(utc("2026-08-20T09:00:00Z")),
        )
    }

    @Test
    fun `窗口起点那一刻给出的是整个窗口的结束`() {
        // 别把「刚进峰价」算成「马上就出去了」
        assertEquals(
            utc("2026-08-20T04:00:00Z"),
            DeepSeekPricing.nextOffPeakStart(utc("2026-08-20T01:00:00Z")),
        )
    }
}
