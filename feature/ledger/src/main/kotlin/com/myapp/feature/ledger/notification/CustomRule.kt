package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.TransactionDirection
import kotlinx.serialization.Serializable

/**
 * 用户自定义的支付通知解析规则（PRD 3.6.1 兜底：未识别 -> 保存为新规则）。
 *
 * 与 [builtinPaymentRules] 不同：自定义规则用「关键词锚定」而非直接写正则，
 * 普通用户也能编辑。 [toPaymentRule] 把关键词拼接成 [PaymentRule] 的正则。
 *
 * 三种结构（由 [merchantKeyword] 与 [merchantBeforeAmount] 决定）：
 * - 商户在金额前：`向 星巴克 付款 23.50` -> merchantKeyword="向", amountKeyword="付款", before=true
 * - 商户在金额后：`支付成功 ￥5.00 星巴克` -> amountKeyword="支付成功", merchantKeyword=null/任意, before=false
 * - 仅金额：`支出人民币30元` -> amountKeyword="支出人民币", merchantKeyword=null
 *
 * amountKeyword 为空表示通用兜底（同 builtin 的「¥ 符号」「X 元」规则）。
 * 正则金额部分用 possessive `?+` 防回溯（沿用支付宝规则踩过的坑，见 PaymentRule.kt）。
 */
@Serializable
data class CustomRule(
    val id: Long,
    val name: String,
    val channel: String?,
    val direction: String,
    val titleKeywords: List<String>,
    val amountKeyword: String,
    val merchantKeyword: String? = null,
    val merchantBeforeAmount: Boolean = false,
) {
    fun toPaymentRule(): PaymentRule {
        val amountPart = """\s*[¥￥]?(?<${PaymentRule.AMOUNT_GROUP}>[0-9]+(?:\.[0-9]{1,2})?+)\s*元?"""

        val regex = when {
            merchantKeyword != null && merchantBeforeAmount ->
                Regex(Regex.escape(merchantKeyword) + """(?<${PaymentRule.MERCHANT_GROUP}>.+?)""" +
                    Regex.escape(amountKeyword) + amountPart)
            merchantKeyword != null ->
                Regex(Regex.escape(amountKeyword) + amountPart +
                    """\s*(?<${PaymentRule.MERCHANT_GROUP}>.+)$""")
            else ->
                Regex(Regex.escape(amountKeyword) + amountPart)
        }

        return PaymentRule(
            channel = channel,
            direction = direction,
            titleKeywords = titleKeywords,
            textRegex = regex,
            merchantGroupName = if (merchantKeyword != null) PaymentRule.MERCHANT_GROUP else null,
        )
    }

    companion object {
        /** 方向下拉项（UI 用）。 */
        val DIRECTIONS = listOf(TransactionDirection.EXPENSE, TransactionDirection.INCOME)

        /** 渠道下拉项（UI 用）；null = 通用。与 PaymentWhitelist 的渠道常量对齐。 */
        val CHANNELS: List<Pair<String?, String>> = listOf(
            null to "通用",
            "WECHAT" to "微信",
            "ALIPAY" to "支付宝",
            "BANK" to "银行卡",
            "UNIONPAY" to "云闪付",
        )
    }
}
