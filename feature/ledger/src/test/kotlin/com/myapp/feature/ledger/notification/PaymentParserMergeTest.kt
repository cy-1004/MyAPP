package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自定义规则 + 内置规则的合并优先级测试（PRD 3.6.1 Phase 3）。
 *
 * 合并逻辑在 RuleRepository.activeRules：`custom.map(toPaymentRule) + builtin.filterNot(disabled)`。
 * 这里直接构造同样的合并结果喂给 PaymentParser，验证：
 * 1. 自定义规则在前命中（用户改的优先级更高）
 * 2. 被停用的内置规则不会兜底命中
 * 3. 全部规则都停用 -> Failed
 */
class PaymentParserMergeTest {

    private fun merge(custom: List<CustomRule>, disabled: Set<String>): List<PaymentRule> =
        custom.map { it.toPaymentRule() } + builtinPaymentRules.filterNot { it.builtinId in disabled }

    @Test
    fun customRuleWinsOverBuiltin() {
        // 内置 GENERIC_SYMBOL 会被「¥23.50」命中；自定义规则在前，应优先命中且使用自定义的方向
        val custom = CustomRule(
            id = 1L,
            name = "我的微信支出",
            channel = "WECHAT",
            direction = TransactionDirection.INCOME, // 故意与内置相反，验证自定义优先
            titleKeywords = emptyList(),
            amountKeyword = "收款",
            merchantKeyword = null,
            merchantBeforeAmount = false,
        )
        val rules = merge(listOf(custom), emptySet())
        val r = PaymentParser.parse("WECHAT", "", "收款 ¥23.50", rules)
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(TransactionDirection.INCOME, r.direction)
    }

    @Test
    fun disabledBuiltinSkipped_fallsToNextBuiltin() {
        // 停用 GENERIC_SYMBOL，只剩 GENERIC_YUAN 兜底；只认「X 元」结尾
        val r = PaymentParser.parse(
            channel = "WECHAT",
            title = "",
            text = "消费 ¥23.50",
            rules = merge(emptyList(), setOf(BuiltinIds.GENERIC_SYMBOL)),
        )
        // GENERIC_SYMBOL 被停用，GENERIC_YUAN 要求「元」结尾，没有 -> Failed
        assertTrue("停用 ¥ 符号规则后应无法匹配", r is PaymentParseResult.Failed)
    }

    @Test
    fun disabledBuiltinSkipped_butYuanSuffixStillMatches() {
        // 停用 GENERIC_SYMBOL，「X 元」结尾仍能被 GENERIC_YUAN 兜住
        val r = PaymentParser.parse(
            channel = "WECHAT",
            title = "",
            text = "消费 23.50 元",
            rules = merge(emptyList(), setOf(BuiltinIds.GENERIC_SYMBOL)),
        )
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(2350L, r.amountCents)
    }

    @Test
    fun allBuiltinsDisabled_noCustom_fails() {
        // 全部 7 条内置规则都停用、没有自定义 -> 任何文本都 Failed
        val allDisabled = builtinPaymentRules.mapNotNull { it.builtinId }.toSet()
        val rules = merge(emptyList(), allDisabled)
        val r = PaymentParser.parse("WECHAT", "", "向 星巴克 付款 23.50 元", rules)
        assertTrue(r is PaymentParseResult.Failed)
    }

    @Test
    fun customRuleFillsGapWhenBuiltinDisabled() {
        // 停用所有内置 + 加一条能匹配的自定义 -> 自定义命中
        val custom = CustomRule(
            id = 1L,
            name = "我的微信",
            channel = "WECHAT",
            direction = TransactionDirection.EXPENSE,
            titleKeywords = emptyList(),
            amountKeyword = "付款",
            merchantKeyword = "向",
            merchantBeforeAmount = true,
        )
        val allDisabled = builtinPaymentRules.mapNotNull { it.builtinId }.toSet()
        val rules = merge(listOf(custom), allDisabled)
        val r = PaymentParser.parse("WECHAT", "", "向 瑞幸 付款 18.50", rules)
        assertTrue(r is PaymentParseResult.Success)
        r as PaymentParseResult.Success
        assertEquals(1850L, r.amountCents)
        assertEquals("瑞幸", r.merchant)
    }
}
