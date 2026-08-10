package com.myapp.feature.ledger.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.time.AppTime
import com.myapp.feature.ledger.data.Budget
import com.myapp.feature.ledger.data.BudgetCycle
import com.myapp.feature.ledger.data.BudgetRepository
import com.myapp.feature.ledger.data.LedgerRepository
import com.myapp.feature.ledger.data.LedgerSaveEvents
import com.myapp.feature.ledger.data.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 保存完成事件。
 *
 * [savedAmountCents] 为本次记录的金额（分），[remainingCents] 是保存后本周期剩余预算。
 * 没设预算时 remainingCents = null，UI 显示「已记录 ￥X」不报剩余。
 */
data class SavedEvent(val savedAmountCents: Long, val remainingCents: Long?)

@HiltViewModel
class LedgerListViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
    private val saveEvents: LedgerSaveEvents,
) : ViewModel() {

    init {
        // 编辑页保存事件经单例 Channel 跨 entry 传递，与 SavedStateHandle 无关
        viewModelScope.launch {
            saveEvents.events.collect { amountCents -> onSaved(amountCents) }
        }
    }

    /**
     * 列表数据流：交易 + 当前预算组合。预算也带在 state 里，
     * 这样保存后 Snackbar 算「本期剩余」时直接读 state 不用再查一次。
     */
    val state: StateFlow<ListUiState> = combine(
        repository.observeAll(),
        budgetRepository.observeCurrent(),
    ) { transactions, budget ->
        ListUiState(transactions = transactions, budget = budget)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    private val _savedEvents = Channel<SavedEvent>(Channel.BUFFERED)
    val savedEvents: Flow<SavedEvent> = _savedEvents.receiveAsFlow()

    /** 收到编辑页传回的金额后，查预算 + 本期已花，算剩余并发出 Snackbar 事件。 */
    fun onSaved(amountCents: Long) {
        viewModelScope.launch {
            val budget = budgetRepository.getCurrent() ?: run {
                _savedEvents.send(SavedEvent(amountCents, null))
                return@launch
            }
            val cycle = BudgetCycle.currentCycleRange(budget.cycleStartDay)
            val spent = repository.sumExpenseInRange(cycle.first, cycle.last + 1)
            val remaining = budget.totalAmountCents - spent
            _savedEvents.send(SavedEvent(amountCents, remaining))
        }
    }

    fun setBudget(cycleStartDay: Int, totalAmountCents: Long) {
        viewModelScope.launch {
            budgetRepository.setBudget(cycleStartDay, totalAmountCents)
        }
    }
}

/** 列表页 UI 状态。Phase 1 不分 loading/error：Room Flow 首帧 emptyList() 即空态。 */
data class ListUiState(
    val transactions: List<Transaction> = emptyList(),
    val budget: Budget? = null,
)

/** 把 epochMilli 按本地日期分组，保留倒序。 */
fun groupByDate(transactions: List<Transaction>): List<DateGroup> {
    if (transactions.isEmpty()) return emptyList()
    return transactions
        .groupBy { with(AppTime) { it.occurredAt.toLocalDate() } }
        .map { (date, items) -> DateGroup(date, items) }
        .sortedByDescending { it.date }
}

data class DateGroup(
    val date: java.time.LocalDate,
    val items: List<Transaction>,
)
