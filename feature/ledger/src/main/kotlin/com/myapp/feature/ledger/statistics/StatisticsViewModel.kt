package com.myapp.feature.ledger.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.time.AppTime
import com.myapp.feature.ledger.data.CategoryExpenseItem
import com.myapp.feature.ledger.data.LedgerRepository
import com.myapp.feature.ledger.data.MonthlyExpensePoint
import com.myapp.feature.ledger.data.StatisticsInsights
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** 趋势图展示最近几个自然月，含本月。6 个月够看出花钱趋势，太多柱子会挤成一团。 */
private const val TREND_MONTH_COUNT = 6

data class StatisticsUiState(
    val loaded: Boolean = false,
    val months: List<MonthlyExpensePoint> = emptyList(),
    val selectedMonth: LocalDate = AppTime.today().withDayOfMonth(1),
    val categories: List<CategoryExpenseItem> = emptyList(),
) {
    /** 趋势图 Y 轴刻度用：最高的那根柱子的金额，0 时兜底成 1 避免除零。 */
    val maxMonthCents: Long get() = (months.maxOfOrNull { it.totalCents } ?: 0L).coerceAtLeast(1L)
    val selectedMonthTotalCents: Long get() = categories.sumOf { it.totalCents }
    val canSelectNextMonth: Boolean get() = selectedMonth < AppTime.today().withDayOfMonth(1)
}

/**
 * 统计页（PRD 3.6.3）：月度支出趋势 + 选中月份的分类占比。
 *
 * 趋势用 flatMapLatest 独立订阅最近 6 个自然月（固定区间，不随选中月变化），
 * 分类占比单独用 selectedMonth 驱动查询——两者区间口径不同，硬凑一个 combine
 * 源会让「切月份」把趋势图也重新查一遍，没必要。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val ledgerRepository: LedgerRepository,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(AppTime.today().withDayOfMonth(1))

    val state: StateFlow<StatisticsUiState> = combine(
        trendFlow(),
        selectedMonth.flatMapLatest { month ->
            val range = StatisticsInsights.monthRangeMillis(month)
            ledgerRepository.observeCategoryExpenses(range.first, range.last + 1)
        },
        selectedMonth,
    ) { months, categories, selected ->
        StatisticsUiState(loaded = true, months = months, selectedMonth = selected, categories = categories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    private fun trendFlow() = run {
        val months = StatisticsInsights.lastMonths(TREND_MONTH_COUNT)
        val start = StatisticsInsights.monthRangeMillis(months.first()).first
        val endExclusive = StatisticsInsights.monthRangeMillis(months.last()).last + 1
        ledgerRepository.observeMonthlyExpenses(start, endExclusive)
            .map { byYearMonth -> StatisticsInsights.fillGaps(months, byYearMonth) }
    }

    fun selectMonth(month: LocalDate) {
        selectedMonth.value = month
    }

    fun selectPreviousMonth() = selectedMonth.update { it.minusMonths(1) }

    /** 不允许选到未来月份：还没发生的支出没有意义。 */
    fun selectNextMonth() = selectedMonth.update { current ->
        val next = current.plusMonths(1)
        if (next > AppTime.today().withDayOfMonth(1)) current else next
    }
}
