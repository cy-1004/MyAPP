package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.TransactionDirection
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则匹配失败诊断（PRD 3.6.1 Phase 3）。
 *
 * 只断言提示里出现了关键信息，不锁死整句文案——文案会改，
 * 要守住的是「指出了正确的那一关」。
 */
class RuleMatchDiagnosisTest {

    private fun rule(
        channel: String? = null,
        titleKeywords: List<String> = emptyList(),
        amountKeyword: String = "支付",
        merchantKeyword: String? = null,
    ) = CustomRule(
        id = 1,
        name = "测试",
        channel = channel,
        direction = TransactionDirection.EXPENSE,
        titleKeywords = titleKeywords,
        amountKeyword = amountKeyword,
        merchantKeyword = merchantKeyword,
    ).toPaymentRule()

    @Test
    fun emptyInputAsksForSample() {
        val hint = RuleMatchDiagnosis.diagnose(rule(), channel = null, title = "", text = "")
        assertTrue(hint, hint.contains("贴一条通知"))
    }

    @Test
    fun channelMismatchIsReported() {
        val hint = RuleMatchDiagnosis.diagnose(
            rule(channel = "ALIPAY"),
            channel = "WECHAT",
            title = "微信支付",
            text = "支付 5.00 元",
        )
        assertTrue(hint, hint.contains("渠道对不上"))
    }

    @Test
    fun missingTitleKeywordNamesTheKeywordAndActualTitle() {
        // 这正是真机上支付宝那条的情况：规则要求标题含「支付宝」，实际标题是「交易提醒」
        val hint = RuleMatchDiagnosis.diagnose(
            rule(titleKeywords = listOf("支付宝")),
            channel = "ALIPAY",
            title = "交易提醒",
            text = "你有一笔5.00元的支出",
        )
        assertTrue(hint, hint.contains("支付宝"))
        assertTrue(hint, hint.contains("交易提醒"))
    }

    @Test
    fun listsAllMissingTitleKeywords() {
        val hint = RuleMatchDiagnosis.diagnose(
            rule(titleKeywords = listOf("甲", "乙")),
            channel = null,
            title = "丙",
            text = "支付 5.00 元",
        )
        assertTrue(hint, hint.contains("甲"))
        assertTrue(hint, hint.contains("乙"))
    }

    @Test
    fun amountKeywordNotFoundIsReported() {
        val hint = RuleMatchDiagnosis.diagnose(
            rule(amountKeyword = "付款"),
            channel = null,
            title = "微信",
            text = "你有一笔5.00元的支出",
        )
        assertTrue(hint, hint.contains("没找到金额"))
    }

    @Test
    fun titleKeywordPassesWhenTitleActuallyContainsIt() {
        // 标题这一关过了就不该再报标题的错，应该往后走到金额那一关
        val hint = RuleMatchDiagnosis.diagnose(
            rule(titleKeywords = listOf("微信"), amountKeyword = "付款"),
            channel = null,
            title = "微信支付凭证",
            text = "没有金额的正文",
        )
        assertTrue(hint, hint.contains("没找到金额"))
    }
}
