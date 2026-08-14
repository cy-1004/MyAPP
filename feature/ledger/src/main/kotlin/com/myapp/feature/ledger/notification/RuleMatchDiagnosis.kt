package com.myapp.feature.ledger.notification

/**
 * 规则没匹配上时，指出**卡在哪一步**（PRD 3.6.1 Phase 3 规则编辑体验）。
 *
 * 只说「未匹配」等于没说：规则匹配要连过三关（渠道 → 标题关键词 → 正文取金额），
 * 用户不知道是关键词写错了、还是标题根本不含那个词、还是金额格式没对上。
 * 这里按与 [PaymentParser.parse] 完全一致的顺序逐关复查，报出第一个失败的关卡。
 *
 * 纯函数，无 Android 依赖，可 JVM 单测。**改 [PaymentParser.parse] 的匹配顺序时
 * 必须同步改这里**，否则提示会指向错误的关卡。
 */
object RuleMatchDiagnosis {

    fun diagnose(rule: PaymentRule, channel: String?, title: String, text: String): String {
        val t = title.trim()
        val s = text.trim()

        if (t.isEmpty() && s.isEmpty()) {
            return "先在上面贴一条通知的标题和正文"
        }
        if (rule.channel != null && rule.channel != channel) {
            return "渠道对不上：规则限定「${rule.channel}」，试跑用的是「${channel ?: "通用"}」"
        }
        val missing = rule.titleKeywords.filter { !t.contains(it) }
        if (missing.isNotEmpty()) {
            return "标题里没有「${missing.joinToString("」「")}」——" +
                "实际标题是「$t」。删掉这个关键词，或改成标题里真有的字"
        }
        val match = rule.textRegex.find(s)
            ?: return "正文里没找到金额：确认「金额紧跟在这几个字后面」填的内容" +
                "在正文里确实出现、且紧挨着数字"

        val amountRaw = match.groups[rule.amountGroupName]?.value
        if (amountRaw.isNullOrBlank()) {
            return "匹配到了位置但没取到数字，检查金额写法（支持 23.50 / ¥23.5 / 23元）"
        }
        // 走到这里说明 parse 其实应该成功；能进来通常是金额超出上限被 parseAmountCents 拒了
        return "金额「$amountRaw」无法识别：可能超出上限或小数位过多"
    }
}
