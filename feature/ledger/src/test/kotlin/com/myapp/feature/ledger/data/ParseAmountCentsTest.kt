package com.myapp.feature.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 金额解析回归（PRD 4.2 金额存「分」Long，禁用浮点）。
 *
 * 用户输入容错：前导零、缺整数位、缺小数位、多余小数点都要兜住；
 * 上限 1 亿元（1_000_000_00 分）防溢出；非法字符直接拒。
 */
class ParseAmountCentsTest {

    @Test
    fun `常规金额`() {
        assertEquals(2390L, parseAmountCents("23.90"))
        assertEquals(2300L, parseAmountCents("23.00"))
        assertEquals(230L, parseAmountCents("2.30"))
    }

    @Test
    fun `整数`() {
        assertEquals(2300L, parseAmountCents("23"))
        assertEquals(100L, parseAmountCents("1"))
    }

    @Test
    fun `前导零`() {
        assertEquals(2300L, parseAmountCents("023"))
        assertEquals(2350L, parseAmountCents("023.50"))
    }

    @Test
    fun `缺整数位`() {
        assertEquals(50L, parseAmountCents(".5"))
        assertEquals(50L, parseAmountCents(".50"))
    }

    @Test
    fun `缺小数位`() {
        assertEquals(2300L, parseAmountCents("23."))
    }

    @Test
    fun `一位小数 补零`() {
        // "23.5" -> 23 元 50 分
        assertEquals(2350L, parseAmountCents("23.5"))
    }

    @Test
    fun `空白字符容差`() {
        assertEquals(2300L, parseAmountCents("  23  "))
    }

    @Test
    fun `超过两位小数拒绝`() {
        assertNull(parseAmountCents("23.456"))
    }

    @Test
    fun `多个小数点拒绝`() {
        // 正则 ^\d*\.?\d{0,2}$ 直接拒
        assertNull(parseAmountCents("2.3.4"))
    }

    @Test
    fun `非数字字符拒绝`() {
        assertNull(parseAmountCents("abc"))
        assertNull(parseAmountCents("12元"))
        assertNull(parseAmountCents("-23"))
    }

    @Test
    fun `空字符串拒绝`() {
        assertNull(parseAmountCents(""))
        assertNull(parseAmountCents("   "))
    }

    @Test
    fun `零金额拒绝`() {
        assertNull(parseAmountCents("0"))
        assertNull(parseAmountCents("0.00"))
    }

    @Test
    fun `超过 1 亿元上限拒绝`() {
        // 1_000_000_00 分 = 1 亿元；刚好等于上限应允许，超出拒绝
        assertEquals(1_000_000_00L, parseAmountCents("1000000"))
        assertNull(parseAmountCents("1000000.01"))
    }
}
