package com.myapp.feature.ledger.notification

/**
 * 支付类 App 白名单（PRD 3.6.1：只处理白名单包名）。
 *
 * 包名到渠道的映射同时决定规则引擎用哪套规则。
 * 加新银行 App：在 [CHANNELS] 里加一行即可，规则引擎的 BANK 规则自动覆盖。
 */
object PaymentWhitelist {

    /** 包名 → 渠道。渠道与 TransactionEntity.channel 列的值一致。 */
    val CHANNELS: Map<String, String> = mapOf(
        "com.tencent.mm" to "WECHAT",              // 微信
        "com.eg.android.AlipayGphone" to "ALIPAY", // 支付宝
        "com.unionpay" to "UNIONPAY",              // 云闪付
        "com.icbc" to "BANK",                      // 工商银行
        "com.chinamworld.main" to "BANK",          // 建设银行
        "com.android.bankabc" to "BANK",           // 农业银行
        "com.chinamworld.bocmbci" to "BANK",       // 中国银行
        "cmb.pb" to "BANK",                        // 招商银行
        "com.bankcomm.Bankcomm" to "BANK",         // 交通银行
        "com.yitong.mbank.psbc" to "BANK",         // 邮储银行
        "cn.com.cmbc.newmbank" to "BANK",          // 民生银行
    )

    fun isPaymentApp(packageName: String): Boolean = packageName in CHANNELS

    fun channelOf(packageName: String): String? = CHANNELS[packageName]
}
