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
 * - [builtinId] 仅内置规则带稳定 id（如 "builtin.wechat.voucher"），用于停用状态持久化；
 *   自定义规则为 null（自定义规则用 [CustomRule.id] 区分）
 *
 * 规则按列表顺序尝试，第一条命中的生效。内置规则在代码里维护而非 assets JSON：
 * 规则只被本 feature 使用、需要随版本演进，写死便于单测与代码走查；
 * 自定义规则机制见 [CustomRule]（Phase 3）。
 */
data class PaymentRule(
    val channel: String?,
    val direction: String?,
    val titleKeywords: List<String>,
    val textRegex: Regex,
    val amountGroupName: String = AMOUNT_GROUP,
    val merchantGroupName: String? = null,
    val builtinId: String? = null,
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
        builtinId = BuiltinIds.WECHAT_VOUCHER,
    ),
    // 微信扫码支付凭证：已支付 ¥17.90 幸福便利店（无「向」字，商户在金额之后）
    PaymentRule(
        channel = "WECHAT",
        direction = TransactionDirection.EXPENSE,
        titleKeywords = listOf("微信"),
        // possessive（?+）防回溯：否则 "已支付 ¥5.00" 会把 "0" 偷给商户组，同支付宝规则
        textRegex = Regex("""已支付\s*[¥￥]?(?<amount>[0-9]+(?:\.[0-9]{1,2})?+)\s*(?:元)?\s*(?<merchant>.+)$"""),
        merchantGroupName = PaymentRule.MERCHANT_GROUP,
        builtinId = BuiltinIds.WECHAT_SCAN_VOUCHER,
    ),
    // 微信收款/到账（关键词与金额之间可能有「转账」等间隔词）
    PaymentRule(
        channel = "WECHAT",
        direction = TransactionDirection.INCOME,
        titleKeywords = listOf("微信"),
        textRegex = Regex("""(?:收款|收入|到账|收到).{0,8}?[¥￥]?(?<amount>[0-9]+(?:\.[0-9]{1,2})?)\s*元?"""),
        builtinId = BuiltinIds.WECHAT_INCOME,
    ),
    // 支付宝：支付成功 ￥23.50 星巴克咖啡
    PaymentRule(
        channel = "ALIPAY",
        direction = TransactionDirection.EXPENSE,
        titleKeywords = listOf("支付宝"),
        // 金额用 possessive（?+）防回溯：否则 "支付成功 ¥5.00" 会把 "0" 偷给商户组
        textRegex = Regex("""支付成功\s*[¥￥]?(?<amount>[0-9]+(?:\.[0-9]{1,2})?+)\s*(?:元)?\s*(?<merchant>.+)$"""),
        merchantGroupName = PaymentRule.MERCHANT_GROUP,
        builtinId = BuiltinIds.ALIPAY_EXPENSE,
    ),
    // 支付宝「交易提醒」：你有一笔5.00元的支出，点击领取2个支付宝积分。
    // 注意标题是「交易提醒」而**不含「支付宝」**，所以上面那条支付宝规则接不住它
    // （2026-08-14 实测的真实通知）。这条不设 titleKeywords，靠 channel=ALIPAY 限定。
    // 方向由「支出/收入」决定，交给 inferDirection 推断，不写死 direction。
    // 这条必须排在通用兜底之前：兜底的「X 元」规则也能匹配到金额，
    // 但会把「2个支付宝积分」这类无关数字暴露在回溯风险里，且渠道语义丢失。
    PaymentRule(
        channel = "ALIPAY",
        direction = null,
        titleKeywords = emptyList(),
        textRegex = Regex("""你有一笔\s*[¥￥]?(?<amount>[0-9]+(?:\.[0-9]{1,2})?)\s*元"""),
        builtinId = BuiltinIds.ALIPAY_TRADE_NOTICE,
    ),
    // 银行卡：您尾号1234卡8月5日支出人民币23.50元
    PaymentRule(
        channel = "BANK",
        direction = TransactionDirection.EXPENSE,
        titleKeywords = emptyList(),
        textRegex = Regex("""支出人民币(?<amount>[0-9]+(?:\.[0-9]{1,2})?)元"""),
        builtinId = BuiltinIds.BANK_EXPENSE,
    ),
    // 银行卡：收入人民币 X 元
    PaymentRule(
        channel = "BANK",
        direction = TransactionDirection.INCOME,
        titleKeywords = emptyList(),
        textRegex = Regex("""(?:收入|入账|到账)人民币?(?<amount>[0-9]+(?:\.[0-9]{1,2})?)元"""),
        builtinId = BuiltinIds.BANK_INCOME,
    ),
    // 通用兜底：带 ¥/￥ 符号的金额（渠道规则没命中时）
    PaymentRule(
        channel = null,
        direction = null,
        titleKeywords = emptyList(),
        textRegex = Regex("""[¥￥]\s*(?<amount>[0-9]+(?:\.[0-9]{1,2})?)"""),
        builtinId = BuiltinIds.GENERIC_SYMBOL,
    ),
    // 通用兜底：X 元 结尾的金额
    PaymentRule(
        channel = null,
        direction = null,
        titleKeywords = emptyList(),
        textRegex = Regex("""(?<amount>[0-9]+(?:\.[0-9]{1,2})?)\s*元"""),
        builtinId = BuiltinIds.GENERIC_YUAN,
    ),
)

/** 内置规则稳定 id（用于停用状态持久化；不怕规则重排）。 */
object BuiltinIds {
    const val WECHAT_VOUCHER = "builtin.wechat.voucher"
    const val WECHAT_SCAN_VOUCHER = "builtin.wechat.scan_voucher"
    const val WECHAT_INCOME = "builtin.wechat.income"
    const val ALIPAY_EXPENSE = "builtin.alipay.expense"
    const val ALIPAY_TRADE_NOTICE = "builtin.alipay.trade_notice"
    const val BANK_EXPENSE = "builtin.bank.expense"
    const val BANK_INCOME = "builtin.bank.income"
    const val GENERIC_SYMBOL = "builtin.generic.symbol"
    const val GENERIC_YUAN = "builtin.generic.yuan"
}

/**
 * 内置规则的 UI 展示标签（builtinId -> 中文名 + 简短说明）。
 * 顺序与 [builtinPaymentRules] 一致；UI 直接读这个列表渲染开关项。
 */
val builtinPaymentRuleLabels: List<Pair<String, String>> = listOf(
    BuiltinIds.WECHAT_VOUCHER to "微信付款凭证（向 X 付款 Y 元）",
    BuiltinIds.WECHAT_SCAN_VOUCHER to "微信扫码支付凭证（已支付 ¥X 商户）",
    BuiltinIds.WECHAT_INCOME to "微信收款/到账",
    BuiltinIds.ALIPAY_EXPENSE to "支付宝支付成功（支付成功 ¥X 商户）",
    BuiltinIds.ALIPAY_TRADE_NOTICE to "支付宝交易提醒（你有一笔 X 元的支出）",
    BuiltinIds.BANK_EXPENSE to "银行卡支出人民币",
    BuiltinIds.BANK_INCOME to "银行卡收入人民币",
    BuiltinIds.GENERIC_SYMBOL to "通用：¥/￥ 符号金额",
    BuiltinIds.GENERIC_YUAN to "通用：X 元 结尾金额",
)
