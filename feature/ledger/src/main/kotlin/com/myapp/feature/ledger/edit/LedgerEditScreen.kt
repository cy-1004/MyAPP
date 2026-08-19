package com.myapp.feature.ledger.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.designsystem.component.bringIntoViewOnFocus
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.ledger.data.TransactionDirection
import com.myapp.feature.ledger.data.Category
import com.myapp.feature.ledger.ui.categoryColor
import java.time.Instant
import java.time.ZoneOffset

/**
 * 新建 / 编辑账目（PRD 3.6.3）。
 *
 * 新建与编辑同一页：字段一致，区别只在标题文案与是否有「删除」按钮。
 * 与 TodoEditScreen 同结构，区别在金额输入（衬线大字 + ￥ 前缀 + Decimal 键盘）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerEditScreen(
    onBack: () -> Unit,
    onSaved: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LedgerEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.results.collect { result ->
            when (result) {
                is LedgerEditResult.Saved -> onSaved(result.amountCents, result.categoryId)
                LedgerEditResult.Deleted -> onBack()
            }
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }

    // 新建时直接把光标放进金额框
    LaunchedEffect(loaded) {
        if (loaded && draft.isNew) amountFocus.requestFocus()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draft.isNew) "记一笔" else "编辑账目",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!draft.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.appColors.danger,
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = draft.canSave) {
                        Text("保存", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            AmountInput(
                amountText = draft.amountText,
                onValueChange = viewModel::updateAmount,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
                    .bringIntoViewOnFocus(),
            )

            SectionLabel("方向")
            ChipRow(
                options = listOf(TransactionDirection.EXPENSE, TransactionDirection.INCOME),
                selected = draft.direction,
                labelOf = { if (it == TransactionDirection.EXPENSE) "支出" else "收入" },
                onSelect = viewModel::updateDirection,
            )

            SectionLabel("分类")
            CategoryPicker(
                categories = categories,
                selectedId = draft.categoryId,
                onSelect = viewModel::updateCategory,
            )

            OutlinedTextField(
                value = draft.merchant,
                onValueChange = viewModel::updateMerchant,
                label = { Text("商户（可选）") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus(),
            )

            SectionLabel("日期")
            DateRow(
                occurredAt = draft.occurredAt,
                onQuickPick = viewModel::updateOccurredAt,
                onPickDate = { showDatePicker = true },
            )

            OutlinedTextField(
                value = draft.note,
                onValueChange = viewModel::updateNote,
                label = { Text("备注（可选）") },
                minLines = 2,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus(),
            )
        }
    }

    if (showDatePicker) {
        // DatePicker 用 UTC 零点毫秒，本地日期搬到 UTC 同一天，否则东八区会偏一天（PRD坑 #8）
        val initialUtcMillis = with(AppTime) {
            draft.occurredAt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            // 只换日期保留原时分秒，否则 00:00 会盖掉当前时间
                            val time = with(AppTime) {
                                draft.occurredAt.toLocalDateTime().toLocalTime()
                            }
                            viewModel.updateOccurredAt(
                                with(AppTime) { date.atTime(time).toEpochMilli() }
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountInput(
    amountText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 金额用衬线大字 + ￥ 前缀；与列表金额同字体（displaySmall = Noto Serif SC 32sp）
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "￥",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.appColors.textTertiary,
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = "0.00",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.appColors.textTertiary,
                )
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Medium),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.appColors.textSecondary,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}

/**
 * 分类选择器（PRD 3.6.1「支持自建子分类……一级够用时不强制展开」）。
 *
 * 顶级分类一律铺开显示，跟改版前的体验一致--没建子分类的人完全无感知。
 * 有子分类的顶级分类多一个展开箭头：**点分类本身直接选中它**（记到父类目下，
 * 3 步记账的流程不受影响），**点箭头才展开子分类行**，两个是独立的点击目标。
 * 同一时间只展开一组，展开另一组会收起上一组，避免子分类行无限往下堆。
 *
 * 编辑一笔已经选了子分类的账目时，自动展开它所在的组--不然用户会看到
 * 顶级分类里没有一个是选中状态，以为「记录不见了」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    val topLevel = remember(categories) { categories.filter { it.parentId == null }.sortedBy { it.sortOrder } }
    val childrenByParent = remember(categories) {
        categories.filter { it.parentId != null }
            .groupBy { it.parentId }
            .mapValues { (_, kids) -> kids.sortedBy { it.sortOrder } }
    }

    var expandedParentId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedId, categories) {
        val parentOfSelected = categories.firstOrNull { it.id == selectedId }?.parentId
        if (parentOfSelected != null) expandedParentId = parentOfSelected
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            topLevel.forEach { category ->
                val kids = childrenByParent[category.id].orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryChip(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                    )
                    if (kids.isNotEmpty()) {
                        val expanded = expandedParentId == category.id
                        IconButton(
                            onClick = { expandedParentId = if (expanded) null else category.id },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (expanded) {
                                    "收起「${category.name}」的子分类"
                                } else {
                                    "展开「${category.name}」的子分类"
                                },
                                tint = MaterialTheme.appColors.textTertiary,
                            )
                        }
                    }
                }
            }
        }

        val expandedChildren = expandedParentId?.let(childrenByParent::get).orEmpty()
        if (expandedChildren.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                expandedChildren.forEach { child ->
                    CategoryChip(
                        category = child,
                        selected = child.id == selectedId,
                        onClick = { onSelect(child.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(category: Category, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CategoryDot(category.color)
                Text(category.name, style = MaterialTheme.typography.labelLarge)
            }
        },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun CategoryDot(colorKey: String) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(categoryColor(colorKey)),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(
    occurredAt: Long,
    onQuickPick: (Long) -> Unit,
    onPickDate: () -> Unit,
) {
    val today = AppTime.today()
    val occurredDate = with(AppTime) { occurredAt.toLocalDate() }
    // 快选/选日期只换日期，时分秒沿用 draft 现有时间（默认是 AppTime.now()），否则会被盖成 00:00
    val occurredTime = with(AppTime) { occurredAt.toLocalDateTime().toLocalTime() }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FilterChip(
                selected = occurredDate == today,
                onClick = {
                    val newMillis = with(AppTime) { today.atTime(occurredTime).toEpochMilli() }
                    onQuickPick(newMillis)
                },
                label = { Text("今天", style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
            FilterChip(
                selected = occurredDate == today.minusDays(1),
                onClick = {
                    val newMillis = with(AppTime) {
                        today.minusDays(1).atTime(occurredTime).toEpochMilli()
                    }
                    onQuickPick(newMillis)
                },
                label = { Text("昨天", style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
            FilterChip(
                selected = false,
                onClick = onPickDate,
                label = { Text("选日期", style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
        }

        val dateText = with(AppTime) {
            occurredAt.toLocalDate().format(AppFormatters.dateWithYear)
        }
        Text(
            text = "$dateText ${occurredTime.format(AppFormatters.time)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
