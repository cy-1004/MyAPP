package com.myapp.feature.period.data

import java.security.MessageDigest
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 把本地的经期数据组织成给模型看的文本（PRD 3.14）。
 *
 * 纯函数，没有任何 IO——**发出去的内容长什么样必须能被测试钉死**。
 * 这是全 App 唯一一处把用户手写的私密文字送出设备的地方，
 * 「到底发了什么」不能靠读一遍调用链去推断。
 *
 * 数据范围是用户明确选的「全部」：周期统计 + 每日记录的标签与自由文本 + 经期备注。
 * 但**只发需要分析的那一段**（近 [MAX_RECORDS] 次经期及其覆盖的日记录），不是整库，
 * 也不带姓名、设备号、账号等任何身份信息——模型不需要知道这是谁。
 */
object PeriodAiPrompt {

    /** 最多发几次经期记录。6 个周期足够看出节律，再多只是徒增费用与暴露面。 */
    const val MAX_RECORDS = 6

    /**
     * 系统指令。约束的重点有三个：不做诊断、不给药、承认本地算法才是预测的主体。
     *
     * 「不给病名与用药建议」不是客套——一个自用记录 App 给出「你可能是子宫内膜异位症」
     * 会造成真实伤害：要么把人吓坏，要么让人拿它当结论而延误就医。
     */
    val INSTRUCTIONS = """
        你是一个女性健康记录 App 里的解读助手，面向的是记录者本人。
        请依据用户提供的经期记录，给出一段简明、平和、可读的中文解读，包含：
        1. 节律是否稳定（周期长度、经期天数的波动情况）；
        2. 每日记录里是否有值得留意的地方；
        3. 对下一个周期的时间判断；
        4. 一两条具体可执行的日常建议（作息、饮食、运动之类）。

        硬性要求：
        - 你不是医生，不做诊断。不要给出任何具体病名、药名或用药方案。
        - 确实有需要留意的情况时，只说「建议找医生看看」，不要描述可能是什么病。
        - 不要重复罗列用户已经给你的原始数据，直接给解读。
        - 用户看到的是一段纯文本，不要用 Markdown 标题或表格，可以分段。
        - 总长度控制在 400 字以内。
    """.trimIndent()

    /**
     * 组装正文。[today] 单独传而不是内部取，是为了让测试拿到确定输出。
     *
     * 注意 [today] **不参与** [fingerprint]：它每天都在变，若算进指纹，
     * 「数据没变就不重复调用」这条会在每天零点自动失效，等于没有缓存。
     */
    fun buildInput(
        state: PeriodState,
        dayLogs: Map<LocalDate, PeriodDayLog>,
        today: LocalDate,
    ): String {
        val records = state.records.take(MAX_RECORDS)
        return buildString {
            appendLine("今天是 $today。")
            appendLine()
            appendLine("【周期统计】")
            appendLine("平均周期 ${state.avgCycleDays} 天，参与平均的间隔有 ${state.cycleSamples} 个。")
            state.avgDurationDays?.let { appendLine("平均持续 $it 天。") }
            if (!state.reliable) {
                // 明确告诉模型样本不足，否则它会顺着一个不可靠的平均值讲得很笃定
                appendLine("注意：样本量不足 3 个间隔，以上统计仅供参考，不要据此下确定结论。")
            }
            state.predictedStart?.let { appendLine("本地算法推算的下次开始日期是 $it。") }
            appendLine()

            appendLine("【最近 ${records.size} 次经期】")
            if (records.isEmpty()) {
                appendLine("（没有记录）")
            } else {
                records.forEachIndexed { index, record ->
                    append("- 开始 ${record.startDate}")
                    append(record.endDate?.let { "，结束 $it，共 ${record.durationDays} 天" } ?: "，尚未结束")
                    // 与上一次（列表按时间倒序，下一项才是更早的那次）的间隔
                    records.getOrNull(index + 1)?.let { previous ->
                        val gap = ChronoUnit.DAYS.between(previous.startDate, record.startDate)
                        append("，距上次开始 $gap 天")
                    }
                    record.note?.takeIf { it.isNotBlank() }?.let { append("，备注：$it") }
                    appendLine()
                }
            }
            appendLine()

            appendLine("【每日记录】")
            val relevant = relevantLogs(records, dayLogs)
            if (relevant.isEmpty()) {
                appendLine("（这段时间没有每日记录）")
            } else {
                relevant.forEach { log ->
                    append("- ${log.date}：")
                    append(log.tags.joinToString("、") { it.label }.ifEmpty { "（无标签）" })
                    if (log.note.isNotBlank()) append("；${log.note.trim()}")
                    appendLine()
                }
            }
        }.trim()
    }

    /**
     * 输入数据的指纹。数据没变就不重复调用（PRD 3.14）——既省钱，
     * 也避免把同样的私密内容一遍遍发出去。
     *
     * 只覆盖会影响结论的字段。用 SHA-256 而不是 `hashCode()`：
     * 后者只有 32 位且不保证跨版本稳定，撞一次的后果是「明明改了记录却拿到旧结论」。
     */
    fun fingerprint(state: PeriodState, dayLogs: Map<LocalDate, PeriodDayLog>): String {
        val records = state.records.take(MAX_RECORDS)
        val raw = buildString {
            append(state.avgCycleDays).append('|')
            append(state.avgDurationDays).append('|')
            append(state.reliable).append('|')
            append(state.predictedStart).append('|')
            records.forEach { append(it.startDate).append(',').append(it.endDate).append(',').append(it.note).append(';') }
            append('|')
            relevantLogs(records, dayLogs).forEach {
                append(it.date).append(',').append(DayLogTag.join(it.tags)).append(',').append(it.note).append(';')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 与这几次经期相关的日记录：从最早那次的开始日算起。
     *
     * 不发更早的记录——它们对「最近的节律」没有解释力，白发一遍隐私。
     */
    private fun relevantLogs(
        records: List<PeriodRecord>,
        dayLogs: Map<LocalDate, PeriodDayLog>,
    ): List<PeriodDayLog> {
        val since = records.lastOrNull()?.startDate ?: return emptyList()
        return dayLogs.values
            .filter { !it.date.isBefore(since) && !it.isEmpty }
            .sortedBy { it.date }
    }
}
