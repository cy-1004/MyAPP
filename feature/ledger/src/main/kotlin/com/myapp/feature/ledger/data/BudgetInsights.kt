package com.myapp.feature.ledger.data

import com.myapp.core.common.time.AppTime
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 预算视图的派生指标（PRD 3.6.2）。
 *
 * 全是纯函数，[BudgetInsightsTest] 钉死——这些数字用户是拿来做花钱决策的，
 * 算错了比不显示更糟：显示「日均还能花 ￥120」而实际只剩 ￥30，会直接让人超支。
 *
 * 两条口径约定：
 * - **周期天数按日历天算**，不按毫秒差：跨夏令时/闰秒时毫秒差会少一小时，
 *   整除后可能少一天，「还剩几天」这种给人看的数字必须和日历一致。
 * - **今天算在剩余天数里**：用户在今天花钱，今天的额度当然还能用。
 *   所以 `remainingDays` 最小是 1（周期最后一天），不会是 0。
 */
object BudgetInsights {

    /**
     * 周期进度。[cycleStart] / [cycleEndExclusive] 是 [com.myapp.core.common.time.BudgetCycle]
     * 给的 epochMilli 区间，[today] 默认取当天。
     *
     * today 落在周期外时（理论上不会：区间就是按 today 算出来的，但进程跨天存活会出现）
     * 会被夹到区间内，保证 elapsed/remaining 不出现负数。
     */
    fun cycleProgress(
        cycleStart: Long,
        cycleEndExclusive: Long,
        today: LocalDate = AppTime.today(),
    ): CycleProgress {
        val startDate = with(AppTime) { cycleStart.toLocalDate() }
        val lastDate = with(AppTime) { (cycleEndExclusive - 1).toLocalDate() }
        val totalDays = ChronoUnit.DAYS.between(startDate, lastDate).toInt() + 1
        val clamped = when {
            today < startDate -> startDate
            today > lastDate -> lastDate
            else -> today
        }
        val elapsedDays = ChronoUnit.DAYS.between(startDate, clamped).toInt() + 1
        return CycleProgress(
            totalDays = totalDays,
            elapsedDays = elapsedDays,
            remainingDays = totalDays - elapsedDays + 1,
        )
    }

    /**
     * 日均还能花多少（分）。剩余为负（已超支）时返回 0——
     * 「日均还能花 -￥50」没有任何指导意义，超支这件事由 [pace] 单独说。
     */
    fun dailyAvailableCents(remainingCents: Long, remainingDays: Int): Long {
        if (remainingCents <= 0L || remainingDays <= 0) return 0L
        return remainingCents / remainingDays
    }

    /**
     * 花钱节奏：把「已花」和「按天数匀速该花的」比一比。
     *
     * 理想值按**已过天数**算而不是剩余天数：周期第 1 天就该允许花掉 1/30，
     * 用剩余天数算会在周期初把所有人都判成超前。
     */
    fun pace(spentCents: Long, budgetCents: Long, progress: CycleProgress): Pace {
        if (budgetCents <= 0L || progress.totalDays <= 0) return Pace(0L, 0L)
        val ideal = budgetCents * progress.elapsedDays / progress.totalDays
        return Pace(idealSpentCents = ideal, diffCents = spentCents - ideal)
    }
}

/** 周期天数进度。[remainingDays] 含今天，最小为 1。 */
data class CycleProgress(
    val totalDays: Int,
    val elapsedDays: Int,
    val remainingDays: Int,
)

/**
 * 花钱节奏。[diffCents] > 0 表示比匀速多花了（超前），< 0 表示省下了。
 * 阈值定在预算的 5%：低于这个差距就是正常波动，不值得给用户一个「超前」的警示。
 */
data class Pace(
    val idealSpentCents: Long,
    val diffCents: Long,
) {
    fun isOnTrack(budgetCents: Long): Boolean {
        if (budgetCents <= 0L) return true
        val tolerance = budgetCents * ON_TRACK_TOLERANCE_PERCENT / 100
        return diffCents in -tolerance..tolerance
    }

    private companion object {
        const val ON_TRACK_TOLERANCE_PERCENT = 5
    }
}
