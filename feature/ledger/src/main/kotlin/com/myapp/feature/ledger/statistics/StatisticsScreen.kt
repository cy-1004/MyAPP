package com.myapp.feature.ledger.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.ledger.data.MonthlyExpensePoint
import com.myapp.feature.ledger.ui.CategoryExpenseRow
import com.myapp.feature.ledger.ui.monthLabelText
import com.myapp.feature.ledger.ui.monthTitleText
import com.myapp.feature.ledger.ui.yuanWithSymbol

/** 趋势图柱子的最大高度，实际高度按月份支出占最高月的比例缩放。 */
private val TREND_BAR_MAX_HEIGHT = 120.dp

/**
 * 统计页（PRD 3.6.3）：月度支出趋势 + 选中月份的分类占比。
 *
 * 趋势用自然月（不是预算周期），点一根柱子切换下面的分类明细看那个月钱花在哪了；
 * 未来月份不可选，没发生的支出没有意义。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("统计", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = Spacing.sm,
                bottom = LocalBottomBarHeight.current + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!state.loaded) return@LazyColumn

            item(key = "trend") {
                TrendCard(
                    months = state.months,
                    selectedMonth = state.selectedMonth,
                    onSelectMonth = viewModel::selectMonth,
                )
            }

            item(key = "month-selector") {
                MonthSelector(
                    selectedMonth = state.selectedMonth,
                    totalCents = state.selectedMonthTotalCents,
                    canSelectNext = state.canSelectNextMonth,
                    onPrevious = viewModel::selectPreviousMonth,
                    onNext = viewModel::selectNextMonth,
                )
            }

            if (state.categories.isEmpty()) {
                item(key = "categories-empty") {
                    EmptyState(text = "这个月还没有支出记录")
                }
            } else {
                items(items = state.categories, key = { it.categoryId }) { item ->
                    CategoryExpenseRow(item = item, totalCents = state.selectedMonthTotalCents)
                }
            }
        }
    }
}

/** 近 6 个月支出趋势：柱状图，点柱子切换下方分类明细看的月份。 */
@Composable
private fun TrendCard(
    months: List<MonthlyExpensePoint>,
    selectedMonth: LocalDate,
    onSelectMonth: (LocalDate) -> Unit,
) {
    val maxCents = (months.maxOfOrNull { it.totalCents } ?: 0L).coerceAtLeast(1L)
    AppCard {
        Text(
            text = "近${months.size}个月支出趋势",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.appColors.textSecondary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.lg),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            months.forEach { point ->
                val isSelected = point.month == selectedMonth
                val fraction = point.totalCents.toFloat() / maxCents.toFloat()
                val barColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.appColors.textTertiary.copy(alpha = 0.35f)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMonth(point.month) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .height(TREND_BAR_MAX_HEIGHT)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(BAR_WIDTH)
                                .height(TREND_BAR_MAX_HEIGHT * fraction.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(barColor),
                        )
                    }
                    Text(
                        text = point.month.monthLabelText(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.appColors.textTertiary
                        },
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}

/** 分类明细的月份选择器：‹ 2026年8月 ›，未来月份不可点。 */
@Composable
private fun MonthSelector(
    selectedMonth: LocalDate,
    totalCents: Long,
    canSelectNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = selectedMonth.monthTitleText(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "支出 ${totalCents.yuanWithSymbol()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
        }
        IconButton(onClick = onNext, enabled = canSelectNext) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "下个月",
                tint = if (canSelectNext) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.appColors.textTertiary.copy(alpha = 0.4f)
                },
            )
        }
    }
}

private val BAR_WIDTH: Dp = 20.dp
