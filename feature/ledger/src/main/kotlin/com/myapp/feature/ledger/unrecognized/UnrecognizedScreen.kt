package com.myapp.feature.ledger.unrecognized

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.data.UnrecognizedItem
import com.myapp.feature.ledger.data.parseAmountCents

/**
 * 未识别支付通知（PRD 3.6.1 兜底）。
 *
 * 每条显示原文（标题 + 正文），可「补录」（填金额/商户落一条账目）或「忽略」。
 * 规则引擎改版后认不出来的旧原文也能在这里人工消化，不丢数据。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnrecognizedScreen(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UnrecognizedViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var recordingItem by remember { mutableStateOf<UnrecognizedItem?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("未识别支付通知") },
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
        if (items.isEmpty()) {
            EmptyState(
                text = "没有未识别的通知",
                modifier = Modifier.padding(innerPadding),
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
                items(items = items, key = { it.id }) { item ->
                    UnrecognizedRow(
                        item = item,
                        onRecord = { recordingItem = item },
                        onDismiss = { viewModel.dismiss(item.id) },
                        onSaveAsRule = { onNavigate(Route.RuleDetail(presetUnrecognizedId = item.id)) },
                    )
                }
            }
        }
    }

    recordingItem?.let { item ->
        RecordDialog(
            item = item,
            onDismiss = { recordingItem = null },
            onConfirm = { amountText, merchant ->
                viewModel.recordManual(item, amountText, merchant)
                recordingItem = null
            },
        )
    }
}

@Composable
private fun UnrecognizedRow(
    item: UnrecognizedItem,
    onRecord: () -> Unit,
    onDismiss: () -> Unit,
    onSaveAsRule: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            val timeText = with(AppTime) {
                item.occurredAt.toLocalDateTime().format(AppFormatters.dateTime)
            }
            Text(
                text = "${item.channel ?: "未知渠道"} · $timeText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textTertiary,
            )
            Spacer(modifier = Modifier.size(Spacing.xs))
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textSecondary,
            )
            Spacer(modifier = Modifier.size(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("忽略", color = MaterialTheme.appColors.textTertiary)
                }
                OutlinedButton(
                    onClick = onSaveAsRule,
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("存为规则")
                }
                OutlinedButton(
                    onClick = onRecord,
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .padding(start = Spacing.sm),
                ) {
                    Text("补录")
                }
            }
        }
    }
}

@Composable
private fun RecordDialog(
    item: UnrecognizedItem,
    onDismiss: () -> Unit,
    onConfirm: (amountText: String, merchant: String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    val canConfirm = parseAmountCents(amountText) != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补录一笔") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = MaterialTheme.shapes.small,
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("商户（可选）") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                Text(
                    text = "分类默认「未分类」，保存后可进记账列表修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(amountText, merchant) },
                enabled = canConfirm,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
