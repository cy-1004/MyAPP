package com.myapp.feature.period.data

import java.time.LocalDate

/**
 * 一个周期里的分期（PRD 3.2「排卵期与黄体期」）。
 *
 * 全部由「下次预计开始日」倒推，所以**可靠性完全继承经期预测**：
 * [computePhases] 在预测不可靠时直接返回 null，UI 就什么都不画。
 * 画一个没依据的排卵日，比不画更糟——用户会拿它当避孕/备孕依据。
 */
data class CyclePhases(
    /** 排卵日（推算）。 */
    val ovulationDay: LocalDate,
    /** 易孕窗口：排卵日前 5 天 ~ 后 1 天。 */
    val fertileWindow: ClosedRange<LocalDate>,
    /** 黄体期：排卵日次日 ~ 下次开始前一天。 */
    val luteal: ClosedRange<LocalDate>,
    /** 卵泡期：本次经期结束次日 ~ 排卵日前一天。经期还没记结束时为 null。 */
    val follicular: ClosedRange<LocalDate>?,
)

/** 排卵日到下次月经的间隔。黄体期长度个体差异小，周期长短的波动几乎都发生在前半段。 */
private const val LUTEAL_PHASE_DAYS = 14L

/** 易孕窗口往前开的天数（精子存活期），往后 1 天（卵子存活期）。 */
private const val FERTILE_DAYS_BEFORE = 5L
private const val FERTILE_DAYS_AFTER = 1L

/**
 * 推算分期。
 *
 * **口径是「下次预计开始日 − 14 天」，不是「周期中点」**：中点法在周期偏长时会把
 * 排卵日算得离谱（比如 35 天周期会算到第 17.5 天，实际大约在第 21 天）。
 *
 * 返回 null 的两种情况，UI 都不该画分期：
 * - 没有预测（一条记录都没有）
 * - 预测不可靠（间隔样本 < 3，见 [PeriodState.reliable]）
 */
fun computePhases(state: PeriodState): CyclePhases? {
    val predictedStart = state.predictedStart ?: return null
    if (!state.reliable) return null

    val ovulation = predictedStart.minusDays(LUTEAL_PHASE_DAYS)
    val fertile = ovulation.minusDays(FERTILE_DAYS_BEFORE)..ovulation.plusDays(FERTILE_DAYS_AFTER)
    val luteal = ovulation.plusDays(1)..predictedStart.minusDays(1)

    // 卵泡期要有「本次经期结束日」才能算起点；经期还进行中（没点结束）时不画，
    // 而不是拿开始日凑一个——那会把经期本身也算进卵泡期
    val lastEnd = state.records.firstOrNull()?.endDate
    val follicular = lastEnd
        ?.plusDays(1)
        ?.takeIf { it.isBefore(ovulation) }
        ?.let { it..ovulation.minusDays(1) }

    return CyclePhases(
        ovulationDay = ovulation,
        fertileWindow = fertile,
        luteal = luteal,
        follicular = follicular,
    )
}

/** 某一天落在哪个分期。用于日历标记与状态卡文案。 */
enum class PhaseMark(val label: String) {
    Ovulation("排卵日"),
    Fertile("易孕期"),
    Luteal("黄体期"),
    Follicular("卵泡期"),
}

/**
 * 判定优先级：排卵日 > 易孕期 > 黄体期 > 卵泡期。
 *
 * 前两者天然重叠（排卵日在易孕窗口里），易孕窗口末尾又和黄体期头一天重叠——
 * 重叠时显示更具体的那个。
 */
fun phaseOf(date: LocalDate, phases: CyclePhases?): PhaseMark? {
    if (phases == null) return null
    return when {
        date == phases.ovulationDay -> PhaseMark.Ovulation
        date in phases.fertileWindow -> PhaseMark.Fertile
        date in phases.luteal -> PhaseMark.Luteal
        phases.follicular?.contains(date) == true -> PhaseMark.Follicular
        else -> null
    }
}
