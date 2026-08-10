package com.myapp.feature.ledger.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.ledger.data.Budget
import com.myapp.feature.ledger.data.BudgetRepository
import com.myapp.feature.ledger.data.LedgerRepository
import com.myapp.feature.ledger.data.TransactionDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LedgerCardViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {

    /**
     * 卡片状态：今日支出 + 当前预算 + 本期已花 + 待确认笔数。
     *
     * 用 flatMapLatest 切换：没设预算时只订阅 today + pending；
     * 设了预算后再多订阅 cycleSpent（依赖 budget.cycleStartDay）。
     * 切换预算时立刻取消上一个订阅，不会两套数据打架。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<LedgerCardState> = budgetRepository.observeCurrent()
        .flatMapLatest { budget ->
            val todaySpending = ledgerRepository.observeToday().map { txs ->
                txs.filter { it.direction == TransactionDirection.EXPENSE }
                    .sumOf { it.amountCents }
            }
            val pending = ledgerRepository.observePendingCount()
            if (budget == null) {
                combine(todaySpending, pending) { today, p ->
                    LedgerCardState(todaySpendingCents = today, budget = null, cycleSpentCents = 0L, pendingCount = p)
                }
            } else {
                val cycleSpent = ledgerRepository.observeCurrentCycleSpending(budget.cycleStartDay)
                combine(todaySpending, cycleSpent, pending) { today, spent, p ->
                    LedgerCardState(
                        todaySpendingCents = today,
                        budget = budget,
                        cycleSpentCents = spent,
                        pendingCount = p,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LedgerCardState(),
        )
}

data class LedgerCardState(
    val todaySpendingCents: Long = 0L,
    val budget: Budget? = null,
    val cycleSpentCents: Long = 0L,
    val pendingCount: Int = 0,
) {
    /** 0~1 的进度比例。没设预算时返回 0（UI 走「未设预算」分支，不查这个值）。 */
    val progress: Float
        get() = if (budget == null || budget.totalAmountCents == 0L) 0f
                else (cycleSpentCents.toFloat() / budget.totalAmountCents).coerceIn(0f, 1f)
}
