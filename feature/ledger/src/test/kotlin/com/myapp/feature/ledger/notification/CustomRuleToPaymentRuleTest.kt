package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CustomRule -> PaymentRule -> PaymentParser.parse 的端到端验证（PRD 3.6.1 Phase 3）。
 *
 * 内置规则用直接写好的正则，自定义规则用关键词拼正则。本测试确认拼出来的正则
 * 与内置规则同口径：金额单位是「分」、商户名能提取、方向按用户选择走。
 */
class CustomRuleToPaymentRuleTest {

    private fun parseWith(rule: CustomRule, channel: String?, text: String): PaymentParseResult {
        return PaymentParser.parse(channel, "", text, listOf(rule.toPaymentRule()))
    }

    @Test
    fun merchantBeforeAmount_weChatVoucherStyle() {
        // 模拟微信凭证：「向 星巴克 付款 23.50」
        val rule = CustomRule(
            id = 1L,
            name = "微信凭证",
            channel = "WECHAT",
            direction = TransactionDirection.EXPENSE,
            titleKeywords = emptyList(),
            amountKeyword = "付款",
            merchantKeyword = "向",
            merchantBeforeAmount = true,
        )
        val r = parseWith(rule, "WECHAT", "向 星巴克 付款 23.50")
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(2350L, r.amountCents)
        assertEquals(TransactionDirection.EXPENSE, r.direction)
        assertEquals("星巴克", r.merchant)
    }

    @Test
    fun merchantAfterAmount_alipayStyle() {
        // 模拟支付宝：「支付成功 ￥5.00 星巴克」
        // merchantBeforeAmount=false 时 merchantKeyword 仅作为「启用商户提取」的开关，
        // 值本身不参与正则（商户在金额后时无锚定词）。
        val rule = CustomRule(
            id = 2L,
            name = "支付宝",
            channel = "ALIPAY",
            direction = TransactionDirection.EXPENSE,
            titleKeywords = emptyList(),
            amountKeyword = "支付成功",
            merchantKeyword = "（任意值，仅作开关）",
            merchantBeforeAmount = false,
        )
        val r = parseWith(rule, "ALIPAY", "支付成功 ￥5.00 星巴克")
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(500L, r.amountCents)
        assertEquals("星巴克", r.merchant)
    }

    @Test
    fun amountOnly_bankCardStyle() {
        // 模拟银行卡：「支出人民币30元」
        val rule = CustomRule(
            id = 3L,
            name = "银行卡",
            channel = "BANK",
            direction = TransactionDirection.EXPENSE,
            titleKeywords = emptyList(),
            amountKeyword = "支出人民币",
            merchantKeyword = null,
            merchantBeforeAmount = false,
        )
        val r = parseWith(rule, "BANK", "支出人民币30元")
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(3000L, r.amountCents)
        assertNull(r.merchant)
    }

    @Test
    fun incomeDirection_preservedFromRule() {
        val rule = CustomRule(
            id = 4L,
            name = "收款",
            channel = "WECHAT",
            direction = TransactionDirection.INCOME,
            titleKeywords = emptyList(),
            amountKeyword = "收款",
            merchantKeyword = null,
            merchantBeforeAmount = false,
        )
        val r = parseWith(rule, "WECHAT", "收款 50.00 元")
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(TransactionDirection.INCOME, r.direction)
    }

    @Test
    fun channelMismatch_fails() {
        // 渠道不符：规则限定 WECHAT，传 ALIPAY 应不命中
        val rule = CustomRule(
            id = 5L,
            name = "微信专用",
            channel = "WECHAT",
            direction = TransactionDirection.EXPENSE,
            titleKeywords = emptyList(),
            amountKeyword = "付款",
            merchantKeyword = null,
            merchantBeforeAmount = false,
        )
        val r = parseWith(rule, "ALIPAY", "付款 23.50")
        assertTrue(r is PaymentParseResult.Failed)
    }

    @Test
    fun amountKeywordWithYuanSuffix_matches() {
        // 金额关键词后接「元」也应匹配（amountPart 容忍可选「元」结尾）
        val rule = CustomRule(
            id = 6L,
            name = "通用元",
            channel = null,
            direction = TransactionDirection.EXPENSE,
            titleKeywords = emptyList(),
            amountKeyword = "消费",
            merchantKeyword = null,
            merchantBeforeAmount = false,
        )
        val r = parseWith(rule, "UNIONPAY", "消费 88.88 元")
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(8888L, r.amountCents)
    }
}
