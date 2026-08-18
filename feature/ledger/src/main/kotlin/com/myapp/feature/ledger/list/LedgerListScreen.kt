package com.myapp.feature.ledger.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.data.Transaction
import com.myapp.feature.ledger.data.TransactionDirection
import com.myapp.feature.ledger.data.TransactionStatus
import com.myapp.feature.ledger.data.parseAmountCents
import com.myapp.feature.ledger.ui.SaveFeedbackOverlay
import com.myapp.feature.ledger.ui.categoryColor
import com.myapp.feature.ledger.ui.categoryIcon
import com.myapp.feature.ledger.ui.yuanWithSymbol

/**
 * 记账列表（PRD 3.6.3）。
 *
 * 顶部齿轮进预算设置；FAB + 进新建；列表分页展示、按日期插表头。
 * 保存成功后弹即时提示浮层（[SaveFeedbackOverlay]，PRD 3.6.2）：
 * 金额从 0 滚动 + 预算进度条推进 + 本期/该分类剩余。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerListScreen(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transactions = viewModel.transactions.collectAsLazyPagingItems()
    // 记一笔之后的即时提示浮层（PRD 3.6.2）。
    // 原来是一条纯文字 Snackbar，交接文档把它标记为体验降级--
    // PRD 要的是金额从 0 滚动 + 进度条推进，见 SaveFeedbackOverlay
    var saveFeedback by remember { mutableStateOf<SavedEvent?>(null) }
    LaunchedEffect(Unit) {
        viewModel.savedEvents.collect { event -> saveFeedback = event }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 底栏是 MyApp 的 Box 叠层（毛玻璃），贴 Scaffold 底部的东西会被盖住，
        // 用 LocalBottomBarHeight 把提示浮层抬到底栏上方（与 FAB 同套路）
        snackbarHost = {
            SaveFeedbackOverlay(
                event = saveFeedback,
                budgetTotalCents = state.budget?.totalAmountCents,
                onDismiss = { saveFeedback = null },
                modifier = Modifier.padding(bottom = LocalBottomBarHeight.current),
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("记账", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // 分类管理在设置页也有入口，这里放一份是因为改分类多半是记账时才想起来的
                    IconButton(onClick = { onNavigate(Route.Statistics) }) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "统计")
                    }
                    IconButton(onClick = { onNavigate(Route.CategoryList) }) {
                        Icon(Icons.Outlined.Category, contentDescription = "分类管理")
                    }
                    IconButton(onClick = { onNavigate(Route.Budget) }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "预算")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Route.LedgerDetail()) },
                modifier = Modifier.padding(bottom = LocalBottomBarHeight.current),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "记一笔")
            }
        },
    ) { innerPadding ->
        // 空态要等首帧加载完再显示，否则进页面会闪一下「还没有记账记录」
        val loaded = transactions.loadState.refresh !is LoadState.Loading
        if (loaded && transactions.itemCount == 0 && state.unrecognizedCount == 0) {
            EmptyState(
                text = "还没有记账记录",
                modifier = Modifier.padding(innerPadding),
                actionLabel = "记一笔",
                onAction = { onNavigate(Route.LedgerDetail()) },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = Spacing.xl,
                    end = Spacing.xl,
                    top = Spacing.sm,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (state.unrecognizedCount > 0) {
                    item(key = "unrecognized") {
                        UnrecognizedCard(
                            count = state.unrecognizedCount,
                            onClick = { onNavigate(Route.LedgerUnrecognized) },
                        )
                    }
                }
                // 日期表头不再靠「先分组再遍历」--分页拿不到完整的组。
                // 改成逐条判断：这一条的日期跟前一条不同，就在它前面画一个表头。
                // 列表本来就按时间倒序，同一天必然连续，这个判断是充分的。
                items(
                    count = transactions.itemCount,
                    key = transactions.itemKey { it.id },
                ) { index ->
                    // enablePlaceholders = false，理论上不会是 null；判空只为不崩
                    val tx = transactions[index] ?: return@items
                    val date = tx.localDate()
                    val previousDate = if (index == 0) null else transactions.peek(index - 1)?.localDate()
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        if (date != previousDate) {
                            DateHeader(date = date, dayExpenseCents = state.dailyExpenseCents[date] ?: 0L)
                        }
                        TransactionRow(
                            transaction = tx,
                            onClick = { onNavigate(Route.LedgerDetail(tx.id)) },
                            onConfirm = { viewModel.confirm(tx.id) },
                        )
                    }
                }
            }
        }
    }

}

/**
 * 日期表头。
 *
 * [dayExpenseCents] 由调用方从一条按天聚合的查询里取，**不是**把当前页的条目加起来--
 * 分页之后一天的账目可能跨在两页之间，只加当前页会少算（详见 LedgerRepository 那条注释）。
 */
@Composable
private fun DateHeader(date: java.time.LocalDate, dayExpenseCents: Long) {
    val today = AppTime.today()
    val label = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(AppFormatters.dateWithYear)
    }
    val dayExpense = dayExpenseCents
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.appColors.textSecondary,
        )
        if (dayExpense > 0L) {
            Text(
                text = "支出 ${dayExpense.yuanWithSymbol()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textTertiary,
            )
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
    onConfirm: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isPending = transaction.status == TransactionStatus.PENDING
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        val color = categoryColor(transaction.categoryColor)
        val icon = categoryIcon(transaction.categoryIcon)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = transaction.merchant?.takeIf { it.isNotBlank() }
                        ?: transaction.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isPending) {
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text(
                        text = "待确认",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.appColors.warning,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.appColors.warning.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            val timeText = with(AppTime) {
                transaction.occurredAt.toLocalDateTime().toLocalTime()
                    .format(AppFormatters.time)
            }
            Text(
                text = if (transaction.note.isNullOrBlank()) {
                    timeText
                } else {
                    "$timeText · ${transaction.note}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textTertiary,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            val amountText = transaction.amountCents.yuanWithSymbol()
            val isExpense = transaction.direction == TransactionDirection.EXPENSE
            Text(
                text = if (isExpense) "-$amountText" else "+$amountText",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                color = if (isExpense) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.appColors.success
                },
            )
            if (isPending && onConfirm != null) {
                Spacer(modifier = Modifier.size(Spacing.xs))
                OutlinedButton(
                    onClick = onConfirm,
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = 0.dp),
                    modifier = Modifier.height(28.dp),
                ) {
                    Text("确认", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun UnrecognizedCard(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.appColors.warning.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "有 $count 条未识别的支付通知",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.warning,
            )
            Text(
                text = "查看并补录 →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.warning,
            )
        }
    }
}

