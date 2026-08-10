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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.myapp.feature.ledger.ui.categoryColor
import com.myapp.feature.ledger.ui.categoryIcon
import com.myapp.feature.ledger.ui.yuanWithSymbol

/**
 * 记账列表（PRD 3.6.3）。
 *
 * 顶部齿轮进预算设置；FAB + 进新建；列表按日期分组显示，
 * 保存成功后 Snackbar 报「已记录 ￥X，本期剩余 ￥Y」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerListScreen(
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBudgetDialog by remember { mutableStateOf(false) }

    // onSaved 触发的 Snackbar 事件
    LaunchedEffect(Unit) {
        viewModel.savedEvents.collect { event ->
            val msg = if (event.remainingCents != null) {
                val remainingText = event.remainingCents.yuanWithSymbol()
                "已记录 ${event.savedAmountCents.yuanWithSymbol()}，本期剩余 $remainingText"
            } else {
                "已记录 ${event.savedAmountCents.yuanWithSymbol()}"
            }
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 底栏是 MyApp 的 Box 叠层（毛玻璃），Snackbar 默认贴 Scaffold 底部会被盖住，
        // 用 LocalBottomBarHeight 把它抬到底栏上方（与 FAB 同套路）
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalBottomBarHeight.current),
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("记账", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = { showBudgetDialog = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "预算设置")
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
        val groups = groupByDate(state.transactions)
        if (state.transactions.isEmpty() && state.unrecognizedCount == 0) {
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
                groups.forEach { group ->
                    item(key = "header-${group.date}") {
                        DateHeader(date = group.date, items = group.items)
                    }
                    items(items = group.items, key = { it.id }) { tx ->
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

    if (showBudgetDialog) {
        BudgetDialog(
            currentCycleStartDay = state.budget?.cycleStartDay ?: 10,
            currentTotalYuan = state.budget?.totalAmountCents?.let { formatCentsToYuan(it) } ?: "",
            onDismiss = { showBudgetDialog = false },
            onConfirm = { cycleStartDay, totalCents ->
                viewModel.setBudget(cycleStartDay, totalCents)
                showBudgetDialog = false
            },
        )
    }
}

@Composable
private fun DateHeader(date: java.time.LocalDate, items: List<Transaction>) {
    val today = AppTime.today()
    val label = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(AppFormatters.dateWithYear)
    }
    val dayExpense = items
        .filter { it.direction == TransactionDirection.EXPENSE }
        .sumOf { it.amountCents }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetDialog(
    currentCycleStartDay: Int,
    currentTotalYuan: String,
    onDismiss: () -> Unit,
    onConfirm: (cycleStartDay: Int, totalAmountCents: Long) -> Unit,
) {
    var dayText by remember { mutableStateOf(currentCycleStartDay.toString()) }
    var yuanText by remember { mutableStateOf(currentTotalYuan) }

    val dayValid = dayText.toIntOrNull()?.let { it in 1..28 } == true
    val cents = parseAmountCents(yuanText)
    val canConfirm = dayValid && cents != null && cents > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("本期预算") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = dayText,
                    onValueChange = { dayText = it.filter(Char::isDigit).take(2) },
                    label = { Text("发薪日（1-28）") },
                    singleLine = true,
                    isError = dayText.isNotEmpty() && !dayValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.small,
                )
                OutlinedTextField(
                    value = yuanText,
                    onValueChange = { yuanText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("本期预算（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(dayText.toInt(), cents!!) },
                enabled = canConfirm,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 分转元文本（编辑用），与 LedgerRepository 同实现。 */
private fun formatCentsToYuan(cents: Long): String {
    val yuan = cents / 100
    val fen = cents % 100
    return if (fen == 0L) "$yuan"
    else if (fen < 10L) "$yuan.0$fen"
    else "$yuan.$fen"
}
