package com.myapp.feature.ledger.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.time.BudgetCycle
import com.myapp.feature.ledger.data.Budget
import com.myapp.feature.ledger.data.BudgetHistoryInsights
import com.myapp.feature.ledger.data.BudgetInsights
import com.myapp.feature.ledger.data.BudgetRepository
import com.myapp.feature.ledger.data.CategoryExpenseItem
import com.myapp.feature.ledger.data.CyclePerformance
import com.myapp.feature.ledger.data.CycleProgress
import com.myapp.feature.ledger.data.LedgerRepository
import com.myapp.feature.ledger.data.Pace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 预算视图 UI 状态。
 *
 * [budget] 为 null = 还没设预算，此时只展示引导，不算任何派生指标
 * （没有分母的「剩余」「日均可用」都是假的）。
 */
data class BudgetUiState(
    val loaded: Boolean = false,
    val budget: Budget? = null,
    val cycleStart: Long = 0L,
    val cycleEndExclusive: Long = 0L,
    val spentCents: Long = 0L,
    val progress: CycleProgress = CycleProgress(0, 0, 0),
    val pace: Pace = Pace(0L, 0L),
    val categories: List<CategoryExpenseItem> = emptyList(),
) {
    val remainingCents: Long get() = (budget?.totalAmountCents ?: 0L) - spentCents

    /** 已花比例，用于进度条。超支时截到 1f，条满即可，不需要画出 130%。 */
    val spentFraction: Float
        get() {
            val total = budget?.totalAmountCents ?: return 0f
            if (total <= 0L) return 0f
            return (spentCents.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        }

    val dailyAvailableCents: Long
        get() = BudgetInsights.dailyAvailableCents(remainingCents, progress.remainingDays)

    val isOnTrack: Boolean get() = pace.isOnTrack(budget?.totalAmountCents ?: 0L)
}

/**
 * 预算视图 VM（PRD 3.6.2）。
 *
 * 数据流：当前预算 → 算出本期区间 → 该区间的支出总额与分类明细。
 * 用 [flatMapLatest] 而不是把区间写死：改了发薪日以后，下游的区间查询要跟着换，
 * 直接 combine 会一直用第一次算出来的区间（首页卡片踩过同样的坑）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    val state: StateFlow<BudgetUiState> = budgetRepository.observeCurrent()
        .flatMapLatest { budget ->
            if (budget == null) {
                flowOf(BudgetUiState(loaded = true, budget = null))
            } else {
                val cycle = BudgetCycle.currentCycleRange(budget.cycleStartDay)
                val start = cycle.first
                val endExclusive = cycle.last + 1
                combine(
                    ledgerRepository.observeExpenseSumInRange(start, endExclusive),
                    ledgerRepository.observeCategoryExpenses(start, endExclusive),
                ) { spent, categories ->
                    val progress = BudgetInsights.cycleProgress(start, endExclusive)
                    BudgetUiState(
                        loaded = true,
                        budget = budget,
                        cycleStart = start,
                        cycleEndExclusive = endExclusive,
                        spentCents = spent,
                        progress = progress,
                        pace = BudgetInsights.pace(spent, budget.totalAmountCents, progress),
                        categories = categories,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BudgetUiState(),
        )

    /**
     * 近 [HISTORY_CYCLE_COUNT] 期的达成情况（PRD 3.6.2）。
     *
     * 单独一条订阅，不并进 [state]：它要按 12 个区间各查一次支出，
     * 而 [state] 只关心本期，混在一起会让「改了发薪日」这种事把 13 组查询全部重跑。
     *
     * 发薪日取**当前**预算的（`observeAll` 正序，最后一行就是当前生效的那份）。
     */
    val history: StateFlow<List<CyclePerformance>> = budgetRepository.observeAll()
        .flatMapLatest { budgets ->
            val current = budgets.lastOrNull()
            if (current == null) {
                flowOf(emptyList())
            } else {
                val ranges = BudgetCycle.recentCycleRanges(
                    cycleStartDay = current.cycleStartDay,
                    count = HISTORY_CYCLE_COUNT,
                )
                combine(
                    ranges.map { ledgerRepository.observeExpenseSumInRange(it.first, it.last + 1) },
                ) { sums ->
                    BudgetHistoryInsights.performances(ranges, sums.toList(), budgets)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun setBudget(cycleStartDay: Int, totalAmountCents: Long) {
        viewModelScope.launch {
            budgetRepository.setBudget(cycleStartDay, totalAmountCents)
        }
    }

    private companion object {
        /** PRD 3.6.2 写的就是近 12 期。 */
        const val HISTORY_CYCLE_COUNT = 12
    }
}
