package com.myapp.feature.period.ui

import com.myapp.core.common.time.AppFormatters
import com.myapp.feature.period.data.PeriodRecord
import com.myapp.feature.period.data.PeriodState
import com.myapp.feature.period.data.PeriodStatus
import com.myapp.feature.period.data.computePhases
import com.myapp.feature.period.data.phaseOf

/** 首页卡片与详情页共用的状态主文案。 */
fun PeriodStatus.headline(): String = when (this) {
    is PeriodStatus.Ongoing -> "经期第 $day 天"
    is PeriodStatus.Waiting -> when {
        daysUntil > 0 -> "距下次约 $daysUntil 天"
        daysUntil == 0L -> "预计就在今天"
        // 负数不叫「距下次 -3 天」，那是纯粹的机器口吻
        else -> "已推迟 ${-daysUntil} 天"
    }
    PeriodStatus.NoData -> "还没有记录"
}

/** 副标题：把预测的依据说清楚，样本不足时明确降级措辞（PRD 3.2）。 */
fun PeriodState.explanation(): String = when (status) {
    is PeriodStatus.NoData -> "记一次开始，之后就能预测下次了"
    else -> buildString {
        append("平均周期 $avgCycleDays 天")
        avgDurationDays?.let { append(" · 平均持续 $it 天") }
        if (!reliable) append(" · 样本不足，仅供参考")
    }
}

/**
 * 今天处于哪个分期（PRD 3.2）。
 *
 * 经期中不说分期——那时候「第几天」本身就是最确切的信息，
 * 再叠一句推算出来的分期只会稀释它。不可靠/算不出时返回 null，调用方整行不显示。
 */
fun PeriodState.todayPhaseText(today: java.time.LocalDate): String? {
    if (status is PeriodStatus.Ongoing) return null
    val phase = phaseOf(today, computePhases(this)) ?: return null
    return "推算处于${phase.label}"
}

fun PeriodRecord.rangeText(): String {
    val start = startDate.format(AppFormatters.dateWithYear)
    val end = endDate?.format(AppFormatters.date)
    return if (end == null) "$start 起 · 进行中" else "$start — $end"
}

fun PeriodRecord.durationText(): String = durationDays?.let { "$it 天" } ?: "进行中"
