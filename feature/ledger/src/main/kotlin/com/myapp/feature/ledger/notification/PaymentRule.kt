package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.TransactionDirection

/**
 * 一条支付通知解析规则。
 *
 * - [channel] 为 null 表示适用于所有渠道（通用兜底规则）
 * - [direction] 为 null 表示由 [PaymentParser] 按关键词推断（收款/收入→INCOME）
 * - [titleKeywords] 必须全部出现在通知标题里（空列表 = 不限标题）
 * - [textRegex] 从通知正文提取，用命名分组 `(?<amount>...)` 取金额、
 *   `(?<merchant>...)` 取商户名（可缺省）
 *
 * 规则按列表顺序尝试，第一条命中的生效。内置规则在代码里维护而非 assets JSON：
 * 规则只被本 feature 使用、需要随版本演进，写死便于单测与代码走查；
 * 「未识别 → 保存为新规则」的自定义规则机制（PRD 3.6.1 兜底）留到 Phase 3。
 */
data class PaymentRule(
    val channel: String?,
    val direction: String?,
    val titleKeywords: List<String>,
    val textRegex: Regex,
    val amountGroupName: String = AMOUNT_GROUP,
    val merchantGroupName: String? = null,
) {
    companion object {
        const val AMOUNT_GROUP = "amount"
        const val MERCHANT_GROUP = "merchant"
    }
}

/**
 * 内置规则集（PRD 3.6.1 示例）。
 *
 * 顺序：渠道专属规则在前、通用兜底在后。通用规则只认「¥/￥ 符号」或「元」结尾的金额，
 * 不匹配无金额文本，避免把无关通知也记成账。
 */
val builtinPaymentRules: List<PaymentRule> = listOf(
    // 微信支付凭证：向 星巴克 付款 23.50 元
    PaymentRule(
        channel = "WECHAT",
        direction = TransactionDirection.EXPENSE,
        titleKeywords = listOf("微信"),
        textRegex = Regex("""向(?<merchant>.+?)(?:付款|转账)\s*[¥￥]?(?<amount>[0-9]+(?:\.[0-9]{1,2})?)\s*元?"""),
        merchantGroupName = PaymentRule.MERCHANT_GROUP,
    ),
    // 微信收款/到账（关键词与金额之间可能有「转账」等间隔词）
    PaymentRule(
        channel = "WECHAT",
        direction = TransactionDirection.INCOME,
        titleKeywords = listOf("微信"),
        textRegex = Regex("""(?:收款|收入|到账|收到).{0,8}?[¥￥]?(?<amount>[0-9]+(?:\.[0-9]{1,2})?)\s*元?"""),
    ),
    // 支付宝：支付成功 ￥23.50 星巴克咖啡
    PaymentRule(
        channel = "ALIPAY",
        direction = TransactionDirection.EXPENSE,
        titleKeywords = listOf("支付宝"),
        // 金额用 possessive（?+）防回溯：否则 "支付成功 ¥5.00" 会把 "0" 偷给商户组
        textRegex = Regex("""支付成功\s*[¥￥]?(?<amount>[0-9]+(?:\.[0-9]{1,2})?+)\s*(?:元)?\s*(?<merchant>.+)$"""),
        merchantGroupName = PaymentRule.MERCHANT_GROUP,
    ),
    // 银行卡：您尾号1234卡8月5日支出人民币23.50元
    PaymentRule(
        channel = "BANK",
        direction = TransactionDirection.EXPENSE,
        titleKeywords = emptyList(),
        textRegex = Regex("""支出人民币(?<amount>[0-9]+(?:\.[0-9]{1,2})?)元"""),
    ),
    // 银行卡：收入人民币 X 元
    PaymentRule(
        channel = "BANK",
        direction = TransactionDirection.INCOME,
        titleKeywords = emptyList(),
        textRegex = Regex("""(?:收入|入账|到账)人民币?(?<amount>[0-9]+(?:\.[0-9]{1,2})?)元"""),
    ),
    // 通用兜底：带 ¥/￥ 符号的金额（渠道规则没命中时）
    PaymentRule(
        channel = null,
        direction = null,
        titleKeywords = emptyList(),
        textRegex = Regex("""[¥￥]\s*(?<amount>[0-9]+(?:\.[0-9]{1,2})?)"""),
    ),
    // 通用兜底：X 元 结尾的金额
    PaymentRule(
        channel = null,
        direction = null,
        titleKeywords = emptyList(),
        textRegex = Regex("""(?<amount>[0-9]+(?:\.[0-9]{1,2})?)\s*元"""),
    ),
)
