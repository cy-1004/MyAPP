package com.myapp.feature.ledger.data

/**
 * 历史周期达成情况（PRD 3.6.2「近 12 期柱状图，超支周期标红」）。
 *
 * 纯函数，[com.myapp.feature.ledger.data.BudgetHistoryInsightsTest] 钉死。
 * 和 [BudgetInsights] 分开是因为口径不同：那边算的是**本期**的实时指标，
 * 这边算的是**已经过去的期**的结果，唯一的难点是「那一期当时的预算是多少」。
 */
object BudgetHistoryInsights {

    /**
     * 某一期该用哪份预算：取**该期结束前最后一次设定**的那份。
     *
     * 为什么按「期末」而不是「期初」：用户在一期中途把预算从 3000 改成 5000，
     * 心里的目标就是 5000，回头看这一期的达成情况当然要跟 5000 比。
     * 按期初取会拿到一个用户自己都已经放弃了的数字。
     *
     * 该期开始前一份预算都没有 → 返回 null（那时候用户压根没设过预算，
     * 这一期不存在「达成 / 超支」，不能拿现在的预算去追认）。
     *
     * @param budgets 全部预算版本，需按 [Budget.effectiveFrom] 正序。
     */
    fun budgetForCycle(budgets: List<Budget>, cycleEndExclusive: Long): Budget? =
        budgets.lastOrNull { it.effectiveFrom < cycleEndExclusive }

    /**
     * 把「周期区间 + 该期支出」对上预算，组装成可以直接画柱子的结果。
     *
     * [ranges] 与 [spentCents] 必须一一对应且等长（调用方是按同一组区间去查的支出）。
     */
    fun performances(
        ranges: List<LongRange>,
        spentCents: List<Long>,
        budgets: List<Budget>,
    ): List<CyclePerformance> {
        require(ranges.size == spentCents.size) {
            "ranges(${ranges.size}) 与 spentCents(${spentCents.size}) 必须等长"
        }
        return ranges.mapIndexed { i, range ->
            val endExclusive = range.last + 1
            CyclePerformance(
                start = range.first,
                endExclusive = endExclusive,
                budgetCents = budgetForCycle(budgets, endExclusive)?.totalAmountCents,
                spentCents = spentCents[i],
            )
        }
    }
}

/**
 * 一期的达成情况。[budgetCents] 为 null = 这一期还没设过预算，
 * 只有支出没有目标，不参与超支判定。
 */
data class CyclePerformance(
    val start: Long,
    val endExclusive: Long,
    val budgetCents: Long?,
    val spentCents: Long,
) {
    /** 花光算达成不算超支，只有真的超过才标红。 */
    val isOverBudget: Boolean
        get() = budgetCents != null && spentCents > budgetCents

    /**
     * 柱子高度用的比例。分母由调用方给（一组柱子要用同一个分母才可比），
     * 分母 <= 0 时返回 0f，避免除零。
     */
    fun fractionOf(maxCents: Long): Float {
        if (maxCents <= 0L) return 0f
        return (spentCents.toFloat() / maxCents.toFloat()).coerceIn(0f, 1f)
    }
}
