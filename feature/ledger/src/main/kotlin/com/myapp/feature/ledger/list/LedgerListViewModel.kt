package com.myapp.feature.ledger.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
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
import java.time.LocalDate
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
     * 账目分页流（PRD 4.5）。`cachedIn` 不能省：没有它，每次重订阅都会
     * 从第一页重新加载，滚动位置和已加载的页全丢。
     */
    val transactions: Flow<PagingData<Transaction>> =
        repository.pagedTransactions().cachedIn(viewModelScope)

    /**
     * 列表的「非分页」状态：预算 + 未识别条数 + 每天的支出合计。
     *
     * 每天的合计单独走一条聚合查询，不从分页数据里算--
     * 一天的条目可能跨在两页之间，光看当前页加不出那天的总额（详见 Repository 那条注释）。
     */
    val state: StateFlow<ListUiState> = combine(
        budgetRepository.observeCurrent(),
        prefs.unrecognized,
        repository.observeDailyExpenseTotals(),
    ) { budget, unrecognized, dailyExpense ->
        ListUiState(
            budget = budget,
            unrecognizedCount = unrecognized.size,
            dailyExpenseCents = dailyExpense,
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

/**
 * 列表页的「非分页」状态。账目本身走 [LedgerListViewModel.transactions] 那条
 * `PagingData` 流，不放在这里。
 *
 * [dailyExpenseCents] 是每个本地日期的支出合计（分），日期表头直接查这个 map。
 */
data class ListUiState(
    val budget: Budget? = null,
    val unrecognizedCount: Int = 0,
    val dailyExpenseCents: Map<LocalDate, Long> = emptyMap(),
)

/** 一笔账目的本地发生日期。分页列表靠它判断要不要在前面插一个日期表头。 */
fun Transaction.localDate(): LocalDate = with(AppTime) { occurredAt.toLocalDate() }
