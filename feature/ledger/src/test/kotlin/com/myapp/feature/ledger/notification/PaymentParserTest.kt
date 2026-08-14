package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.TransactionDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 支付通知解析（PRD 3.6.1 规则引擎）。覆盖 PRD 示例与常见变体、
 * 收入识别、渠道过滤、通用兜底、金额容错、失败路径。
 */
class PaymentParserTest {

    private fun parse(channel: String?, title: String, text: String): PaymentParseResult =
        PaymentParser.parse(channel, title, text)

    private fun assertSuccess(
        result: PaymentParseResult,
        amountCents: Long,
        direction: String,
        merchant: String?,
    ) {
        assertTrue("预期解析成功，实际 $result", result is PaymentParseResult.Success)
        result as PaymentParseResult.Success
        assertEquals(amountCents, result.amountCents)
        assertEquals(direction, result.direction)
        assertEquals(merchant, result.merchant)
    }

    private fun assertFailed(result: PaymentParseResult) {
        assertTrue("预期解析失败，实际 $result", result is PaymentParseResult.Failed)
    }

    // ---- 微信 ----

    @Test
    fun wechat_pay_voucher_prdExample() {
        // PRD 3.6.1：微信支付凭证 - 向 星巴克 付款 23.50 元
        val r = parse("WECHAT", "微信支付凭证", "向 星巴克 付款 23.50 元")
        assertSuccess(r, 2350, TransactionDirection.EXPENSE, "星巴克")
    }

    @Test
    fun wechat_pay_withSymbol() {
        val r = parse("WECHAT", "微信支付", "向 瑞幸咖啡 付款 ¥12.00")
        assertSuccess(r, 1200, TransactionDirection.EXPENSE, "瑞幸咖啡")
    }

    @Test
    fun wechat_pay_merchantWithQuotes() {
        val r = parse("WECHAT", "微信支付凭证", "向「星巴克」付款 23.50 元")
        assertSuccess(r, 2350, TransactionDirection.EXPENSE, "星巴克")
    }

    @Test
    fun wechat_scanPay_voucher() {
        // 真机验证：扫码支付凭证无「向」字，商户紧跟金额之后
        val r = parse("WECHAT", "微信支付凭证", "已支付 ¥17.90 幸福便利店")
        assertSuccess(r, 1790, TransactionDirection.EXPENSE, "幸福便利店")
    }

    @Test
    fun wechat_scanPay_noMerchant_fallsToGeneric() {
        // 没有商户名时落到通用规则，商户为 null（同支付宝 alipay_pay_noMerchant_fallsToGeneric）
        val r = parse("WECHAT", "微信支付凭证", "已支付 ¥5.00")
        assertSuccess(r, 500, TransactionDirection.EXPENSE, null)
    }

    @Test
    fun wechat_income_incomeArrived() {
        val r = parse("WECHAT", "微信支付", "收款到账 ¥23.50")
        assertSuccess(r, 2350, TransactionDirection.INCOME, null)
    }

    @Test
    fun wechat_income_receivedTransfer() {
        val r = parse("WECHAT", "微信支付", "收到转账 ￥100.00 元")
        assertSuccess(r, 10000, TransactionDirection.INCOME, null)
    }

    // ---- 支付宝 ----

    @Test
    fun alipay_pay_success_prdExample() {
        // PRD 3.6.1：支付成功 ￥23.50 星巴克咖啡
        val r = parse("ALIPAY", "支付宝", "支付成功 ￥23.50 星巴克咖啡")
        assertSuccess(r, 2350, TransactionDirection.EXPENSE, "星巴克咖啡")
    }

    @Test
    fun alipay_pay_noMerchant_fallsToGeneric() {
        // 支付宝规则要求商户名，没有时落到通用规则，商户为 null
        val r = parse("ALIPAY", "支付宝", "支付成功 ¥5.00")
        assertSuccess(r, 500, TransactionDirection.EXPENSE, null)
    }

    @Test
    fun alipay_income_collectSuccess() {
        val r = parse("ALIPAY", "支付宝", "收款成功 ￥50.00")
        assertSuccess(r, 5000, TransactionDirection.INCOME, null)
    }

    @Test
    fun alipay_tradeNotice_realDeviceSample() {
        // 2026-08-14 真机实测的真实通知：标题是「交易提醒」，不含「支付宝」三个字
        val r = parse("ALIPAY", "交易提醒", "你有一笔5.00元的支出，点击领取2个支付宝积分。")
        assertSuccess(r, 500, TransactionDirection.EXPENSE, null)
    }

    @Test
    fun alipay_tradeNotice_income() {
        val r = parse("ALIPAY", "交易提醒", "你有一笔128.00元的收入，点击查看详情。")
        assertSuccess(r, 12800, TransactionDirection.INCOME, null)
    }

    @Test
    fun alipay_tradeNotice_notStolenByTrailingDigits() {
        // 正文里「2个支付宝积分」的 2 不能被当成金额：金额锚定在「你有一笔」之后
        val r = parse("ALIPAY", "交易提醒", "你有一笔0.01元的支出，点击领取2个支付宝积分。")
        assertSuccess(r, 1, TransactionDirection.EXPENSE, null)
    }

    // ---- 银行卡 ----

    @Test
    fun bank_expense_prdExample() {
        // PRD 3.6.1：您尾号1234卡8月5日支出人民币23.50元
        val r = parse("BANK", "工商银行", "您尾号1234卡8月5日支出人民币23.50元")
        assertSuccess(r, 2350, TransactionDirection.EXPENSE, null)
    }

    @Test
    fun bank_income() {
        val r = parse("BANK", "招商银行", "您尾号5678卡8月5日收入人民币100.00元")
        assertSuccess(r, 10000, TransactionDirection.INCOME, null)
    }

    @Test
    fun bank_amountWithoutDecimal() {
        val r = parse("BANK", "建设银行", "支出人民币30元")
        assertSuccess(r, 3000, TransactionDirection.EXPENSE, null)
    }

    // ---- 通用兜底与渠道过滤 ----

    @Test
    fun generic_symbolAmount() {
        val r = parse("UNIONPAY", "云闪付", "消费 ￥66.60")
        assertSuccess(r, 6660, TransactionDirection.EXPENSE, null)
    }

    @Test
    fun generic_yuanSuffix() {
        val r = parse("WECHAT", "微信", "扣款 3.50 元")
        assertSuccess(r, 350, TransactionDirection.EXPENSE, null)
    }

    @Test
    fun channelMismatch_specificRuleSkipped() {
        // BANK 渠道的文本不匹配支付宝规则，通用规则兜住
        val r = parse("BANK", "银行", "支付成功 ￥23.50 星巴克咖啡")
        assertSuccess(r, 2350, TransactionDirection.EXPENSE, null)
    }

    // ---- 失败路径 ----

    @Test
    fun noAmount_fails() {
        val r = parse("WECHAT", "微信支付", "您有一笔新的交易")
        assertFailed(r)
    }

    @Test
    fun emptyText_fails() {
        assertFailed(parse("WECHAT", "微信支付", ""))
        assertFailed(parse(null, "", ""))
    }

    @Test
    fun amountOverOneHundredMillion_fails() {
        val r = parse("WECHAT", "微信支付", "向 星巴克 付款 ¥999999999.00")
        assertFailed(r)
    }

    @Test
    fun zeroAmount_fails() {
        val r = parse("WECHAT", "微信支付", "向 星巴克 付款 0.00 元")
        assertFailed(r)
    }

    // ---- 疑似支付预过滤 ----

    @Test
    fun likelyPayment_wechatVoucher_positive() {
        // 真实微信支付凭证（扫码支付，无「向」字），金额符号命中
        assertTrue(PaymentParser.isLikelyPayment("微信支付凭证", "已支付 ¥17.90 幸福便利店"))
    }

    @Test
    fun likelyPayment_bankText_positive() {
        // 银行短信式通知：无符号，靠「数字+元」+ 动词
        assertTrue(PaymentParser.isLikelyPayment("中国银行", "您尾号1234卡支出人民币86.50元"))
    }

    @Test
    fun likelyPayment_plainVoucher_positive() {
        // 无金额无符号，仅凭「支付凭证」动词兜底
        assertTrue(PaymentParser.isLikelyPayment("微信支付凭证", "支付成功"))
    }

    @Test
    fun likelyPayment_chatMessage_negative() {
        // 微信普通聊天通知：不得误判为支付（真机踩坑：置顶联系人消息进未识别队列）
        assertTrue(!PaymentParser.isLikelyPayment("皮皮宝宝", "唉"))
        assertTrue(!PaymentParser.isLikelyPayment("皮皮宝宝", "[2条]皮皮宝宝: 每次都是上厕所出来玩一下手机"))
    }

    @Test
    fun likelyPayment_ambiguousWord_negative() {
        // 「元」作为名词出现（如「一元复始」）不误判；「5元」金额特征才命中
        assertTrue(!PaymentParser.isLikelyPayment("同事", "明天是一元复始的日子"))
        assertTrue(PaymentParser.isLikelyPayment("同事", "转你5元买咖啡"))
    }
}
