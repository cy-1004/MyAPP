package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.TransactionDirection
import com.myapp.feature.ledger.data.parseAmountCents

/** 解析结果。[Failed] 表示没有任何规则命中，原文进未识别队列。 */
sealed interface PaymentParseResult {
    data class Success(
        val amountCents: Long,
        val direction: String,
        val merchant: String?,
    ) : PaymentParseResult

    data object Failed : PaymentParseResult
}

/**
 * 支付通知解析引擎（PRD 3.6.1 规则引擎）。纯函数，无 Android 依赖，可 JVM 单测。
 *
 * 流程：按顺序尝试 [PaymentRule]，第一条「渠道匹配 + 标题关键词全命中 + 正则提取到金额」
 * 的规则生效。金额文本经 [parseAmountCents] 转分（同手工记账同一套容错与上限）。
 */
object PaymentParser {

    fun parse(
        channel: String?,
        title: String?,
        text: String?,
        rules: List<PaymentRule> = builtinPaymentRules,
    ): PaymentParseResult {
        val t = title?.trim().orEmpty()
        val s = text?.trim().orEmpty()
        if (t.isEmpty() && s.isEmpty()) return PaymentParseResult.Failed

        for (rule in rules) {
            if (rule.channel != null && rule.channel != channel) continue
            if (rule.titleKeywords.any { !t.contains(it) }) continue
            val match = rule.textRegex.find(s) ?: continue

            val amountRaw = match.groups[rule.amountGroupName]?.value
                ?.replace(Regex("""[¥￥元\s]"""), "")
            val amount = amountRaw?.let { parseAmountCents(it) } ?: continue

            val merchant = rule.merchantGroupName?.let { group ->
                match.groups[group]?.value?.let(::cleanMerchant)?.ifBlank { null }
            }
            val direction = rule.direction ?: inferDirection(t, s)
            return PaymentParseResult.Success(amount, direction, merchant)
        }
        return PaymentParseResult.Failed
    }

    /**
     * 疑似支付通知预过滤：只处理含金额特征或支付动词的通知。
     *
     * 白名单按包名过滤后，微信仍有大量普通聊天通知（如「唉」「吃饭了吗」），
     * 不预过滤会把它们全部送进未识别队列污染列表。聊天消息极少含「¥」或「数字+元」，
     * 支付通知（凭证/扣款/到账）必含其一，此判定漏判率极低。
     */
    fun isLikelyPayment(title: String, text: String): Boolean {
        val content = title + text
        if (content.contains('¥') || content.contains('￥')) return true
        if (amountPattern.containsMatchIn(content)) return true
        return paymentVerbs.any { content.contains(it) }
    }

    /** 「5」「17.9」「5元」等金额特征；不含符号写法的通知（纯「微信支付凭证」）靠动词兜底。 */
    private val amountPattern = Regex("""\d+(?:\.\d{1,2})?\s*元""")
    private val paymentVerbs =
        listOf("支付", "付款", "收款", "到账", "转账", "入账", "退款", "支出", "消费", "扣款", "凭证", "交易")

    /** 商户名清理：去引号类字符、折叠空白。 */
    private fun cleanMerchant(raw: String): String =
        raw.replace(Regex("""[「」『』"“”'‘’]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val incomeKeywords = listOf("收款", "收入", "到账", "入账", "退款", "收到")
    private val expenseKeywords = listOf("付款", "支付", "支出", "消费", "扣款", "转出")

    /** 方向推断：收入关键词优先（「收款到账」既有收款也有到账）；都没有默认支出。 */
    private fun inferDirection(title: String, text: String): String {
        val all = title + text
        return when {
            incomeKeywords.any { all.contains(it) } -> TransactionDirection.INCOME
            expenseKeywords.any { all.contains(it) } -> TransactionDirection.EXPENSE
            else -> TransactionDirection.EXPENSE
        }
    }
}
