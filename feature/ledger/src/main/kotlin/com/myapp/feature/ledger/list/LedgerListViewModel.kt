package com.myapp.feature.ledger.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.BudgetCycle
import com.myapp.feature.ledger.data.Budget
import com.myapp.feature.ledger.data.BudgetCategoryRepository
import com.myapp.feature.ledger.data.BudgetRepository
import com.myapp.feature.ledger.data.LedgerPrefsStore
import com.myapp.feature.ledger.data.LedgerRepository
import com.myapp.feature.ledger.data.LedgerSaveEvents
import com.myapp.feature.ledger.data.Transaction
import com.myapp.feature.ledger.notification.AutoLedgerNotifier
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 保存完成事件。
 *
 * [savedAmountCents] 为本次记录的金额（分），[remainingCents] 是保存后本周期剩余预算，
 * 没设预算时为 null（UI 显示「已记录 ￥X」不报剩余）。[isOverBudget] 是总预算超支标记，
 * 超支时 UI 把「剩余」文案换成「已超支」。[categoryRemainingCents]/[categoryOverBudget]
 * 只在命中的分类设过预算上限时非空——PRD 3.6.2「命中分类预算时同时显示该分类剩余」。
 */
data class SavedEvent(
    val savedAmountCents: Long,
    val remainingCents: Long?,
    val isOverBudget: Boolean = false,
    val categoryRemainingCents: Long? = null,
    val categoryOverBudget: Boolean = false,
)

@HiltViewModel
class LedgerListViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
    private val budgetCategoryRepository: BudgetCategoryRepository,
    private val saveEvents: LedgerSaveEvents,
    private val prefs: LedgerPrefsStore,
    private val notifier: AutoLedgerNotifier,
) : ViewModel() {

    init {
        // 编辑页保存事件经单例 Channel 跨 entry 传递，与 SavedStateHandle 无关
        viewModelScope.launch {
            saveEvents.events.collect { saved -> onSaved(saved.amountCents, saved.categoryId) }
        }
    }

    /**
     * 列表数据流：交易 + 当前预算 + 未识别条数组合。预算带在 state 里，
     * 保存后 Snackbar 算「本期剩余」时直接读 state 不用再查一次。
     */
    val state: StateFlow<ListUiState> = combine(
        repository.observeAll(),
        budgetRepository.observeCurrent(),
        prefs.unrecognized,
    ) { transactions, budget, unrecognized ->
        ListUiState(
            transactions = transactions,
            budget = budget,
            unrecognizedCount = unrecognized.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ListUiState(),
    )

    /** 一键确认：不改任何字段，仅 PENDING → CONFIRMED，并撤掉对应通知。 */
    fun confirm(id: Long) {
        viewModelScope.launch {
            repository.confirm(id)
            notifier.cancel(id)
        }
    }

    private val _savedEvents = Channel<SavedEvent>(Channel.BUFFERED)
    val savedEvents: Flow<SavedEvent> = _savedEvents.receiveAsFlow()

    /** 收到编辑页传回的金额 + 分类后，查预算/分类上限 + 本期已花，算剩余并发出 Snackbar 事件。 */
    fun onSaved(amountCents: Long, categoryId: Long) {
        viewModelScope.launch {
            val budget = budgetRepository.getCurrent() ?: run {
                _savedEvents.send(SavedEvent(amountCents, null))
                return@launch
            }
            val cycle = BudgetCycle.currentCycleRange(budget.cycleStartDay)
            val spent = repository.sumExpenseInRange(cycle.first, cycle.last + 1)
            val remaining = budget.totalAmountCents - spent

            val cap = budgetCategoryRepository.observeCaps().first()[categoryId]
            var categoryRemaining: Long? = null
            var categoryOver = false
            if (cap != null) {
                val categorySpent = repository.observeCategoryExpenses(cycle.first, cycle.last + 1)
                    .first()
                    .firstOrNull { it.categoryId == categoryId }
                    ?.totalCents ?: 0L
                categoryRemaining = cap - categorySpent
                categoryOver = categorySpent > cap
            }

            _savedEvents.send(
                SavedEvent(
                    savedAmountCents = amountCents,
                    remainingCents = remaining,
                    isOverBudget = remaining < 0L,
                    categoryRemainingCents = categoryRemaining,
                    categoryOverBudget = categoryOver,
                ),
            )
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
    val unrecognizedCount: Int = 0,
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
