package com.myapp.feature.ledger.budget

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.time.AppTime
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.ledger.data.CyclePerformance
import com.myapp.feature.ledger.ui.BudgetDialog
import com.myapp.feature.ledger.ui.CategoryExpenseRow
import com.myapp.feature.ledger.ui.ProgressTrack
import com.myapp.feature.ledger.ui.cycleRangeText
import com.myapp.feature.ledger.ui.formatCentsToYuan
import com.myapp.feature.ledger.ui.progressColor
import com.myapp.feature.ledger.ui.yuanWithSymbol

/**
 * 预算视图（PRD 3.6.2）。
 *
 * 回答四个问题，顺序就是用户在意的顺序：
 * 1. 本期还剩多少钱（大数字 + 进度条）
 * 2. 按天摊还能花多少（日均可用--比「剩余」更能指导今天的决策）
 * 3. 花得算快还是算慢（与匀速理想值比，容差 5% 内算正常）
 * 4. 钱花在哪了（分类排行，占比条用分类自己的颜色）
 *
 * 没设预算时只显示引导，不显示任何派生指标：没有分母的「剩余」是假数字。
 */
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("预算", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDialog = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "编辑预算")
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
            if (state.loaded && state.budget == null) {
                item(key = "empty") {
                    EmptyState(
                        text = "还没设预算。设一个之后这里会显示本期剩余、日均可用和花钱节奏。",
                        actionLabel = "设置预算",
                        onAction = { showDialog = true },
                    )
                }
            }

            if (state.budget != null) {
                item(key = "overview") { OverviewCard(state) }
                item(key = "pace") { PaceCard(state) }
                if (history.isNotEmpty()) {
                    item(key = "history") { HistoryCard(history) }
                }
                item(key = "categories-header") {
                    Text(
                        text = "本期支出去向",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.appColors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
                if (state.categories.isEmpty()) {
                    item(key = "categories-empty") {
                        EmptyState(text = "本期还没有支出记录")
                    }
                } else {
                    items(items = state.categories, key = { it.categoryId }) { item ->
                        CategoryExpenseRow(item = item, totalCents = state.spentCents)
                    }
                }
            }
        }
    }

    if (showDialog) {
        BudgetDialog(
            currentCycleStartDay = state.budget?.cycleStartDay ?: DEFAULT_CYCLE_START_DAY,
            currentTotalYuan = state.budget?.totalAmountCents?.let { formatCentsToYuan(it) } ?: "",
            onDismiss = { showDialog = false },
            onConfirm = { cycleStartDay, totalCents ->
                viewModel.setBudget(cycleStartDay, totalCents)
                showDialog = false
            },
        )
    }
}

/** 首屏卡片：剩余大数字 + 进度条 + 周期区间 + 日均可用。 */
@Composable
private fun OverviewCard(state: BudgetUiState) {
    val budget = state.budget ?: return
    val overspent = state.remainingCents < 0L
    AppCard {
        Text(
            text = if (overspent) "本期已超支" else "本期剩余",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.appColors.textSecondary,
        )
        Text(
            // 超支时展示超出的绝对值，标题已经说明是「超支」，再带负号是重复否定
            text = (if (overspent) -state.remainingCents else state.remainingCents).yuanWithSymbol(),
            style = MaterialTheme.typography.displaySmall,
            color = if (overspent) MaterialTheme.appColors.danger else MaterialTheme.colorScheme.onSurface,
        )

        Box(modifier = Modifier.padding(vertical = Spacing.sm)) {
            ProgressTrack(
                fraction = state.spentFraction,
                color = progressColor(state.spentFraction, overspent),
            )
        }

        Text(
            text = "已花 ${state.spentCents.yuanWithSymbol()} / 预算 ${budget.totalAmountCents.yuanWithSymbol()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = cycleRangeText(state.cycleStart, state.cycleEndExclusive) +
                " · 还剩 ${state.progress.remainingDays} 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textSecondary,
        )
        Text(
            text = if (overspent) {
                "已经超了，剩下 ${state.progress.remainingDays} 天先别再花了"
            } else {
                "日均还能花 ${state.dailyAvailableCents.yuanWithSymbol()}"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (overspent) MaterialTheme.appColors.danger else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** 节奏卡片：与「按天匀速」的理想值比较。 */
@Composable
private fun PaceCard(state: BudgetUiState) {
    val diff = state.pace.diffCents
    val (title, color) = when {
        state.isOnTrack -> "节奏正常" to MaterialTheme.appColors.success
        diff > 0L -> "花得偏快" to MaterialTheme.appColors.warning
        else -> "花得偏省" to MaterialTheme.appColors.success
    }
    AppCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Text(
            text = buildString {
                append("按天匀速到今天该花 ${state.pace.idealSpentCents.yuanWithSymbol()}，")
                append("实际 ${state.spentCents.yuanWithSymbol()}")
                if (!state.isOnTrack) {
                    val abs = if (diff < 0L) -diff else diff
                    append(if (diff > 0L) "，多花了 ${abs.yuanWithSymbol()}" else "，省下 ${abs.yuanWithSymbol()}")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "周期第 ${state.progress.elapsedDays}/${state.progress.totalDays} 天",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textSecondary,
        )
    }
}

/**
 * 历史达成：近 12 期支出柱状图，超支的那期标红（PRD 3.6.2）。
 *
 * 三条口径写在这里，改之前先想清楚：
 * - **柱高的分母是这 12 期里的最大值**，不是各期自己的预算。用各自预算当分母的话，
 *   预算调过的期之间高度就不可比了，而这张图的用途就是横向比。
 * - **没设过预算的期画灰柱**，不判超支——那时候用户根本没有目标，
 *   拿现在的预算去追认是伪造历史。
 * - 花光算达成不算超支，只有真的超过才红。
 */
@Composable
private fun HistoryCard(history: List<CyclePerformance>) {
    val maxCents = (history.maxOfOrNull { it.spentCents } ?: 0L).coerceAtLeast(1L)
    val overCount = history.count { it.isOverBudget }
    AppCard {
        Text(
            text = "近${history.size}期达成情况",
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
            history.forEach { cycle ->
                val barColor = when {
                    cycle.isOverBudget -> MaterialTheme.appColors.danger
                    cycle.budgetCents == null -> MaterialTheme.appColors.textTertiary.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.primary
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .height(HISTORY_BAR_MAX_HEIGHT)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(HISTORY_BAR_WIDTH)
                                .height(HISTORY_BAR_MAX_HEIGHT * cycle.fractionOf(maxCents))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barColor),
                        )
                    }
                    Text(
                        // 12 根柱子并排，「8月」两个字会挤在一起，只留月份数字
                        text = AppTime.run { cycle.start.toLocalDate() }.monthValue.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.textTertiary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
        Text(
            text = if (overCount > 0) "有 $overCount 期超支（红柱）" else "还没有超支的周期",
            style = MaterialTheme.typography.bodySmall,
            color = if (overCount > 0) MaterialTheme.appColors.danger else MaterialTheme.appColors.success,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Text(
            text = "灰柱 = 那一期还没设预算，只有支出没有目标，不算超支",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textTertiary,
        )
    }
}

private val HISTORY_BAR_MAX_HEIGHT = 72.dp
private val HISTORY_BAR_WIDTH = 10.dp

/** 没设过预算时对话框的默认发薪日，与首页卡片/列表页同一个默认值。 */
private const val DEFAULT_CYCLE_START_DAY = 10
