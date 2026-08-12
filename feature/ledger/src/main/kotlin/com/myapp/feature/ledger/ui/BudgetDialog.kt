package com.myapp.feature.ledger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.feature.ledger.data.parseAmountCents

/**
 * 预算设置对话框（发薪日 + 本期预算金额）。
 *
 * 从记账列表页提取到这里，因为预算视图页也要用同一个入口--
 * 两处各写一份的话，改校验规则（比如发薪日上限 28）必然会漏改一处。
 *
 * 发薪日上限 28 见 `BudgetCycle` 的注释：29~31 号在部分月份不存在。
 */
@Composable
internal fun BudgetDialog(
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

/** 分转元文本（编辑框回填用），与 LedgerRepository 内部实现同口径。 */
internal fun formatCentsToYuan(cents: Long): String {
    val yuan = cents / 100
    val fen = cents % 100
    return if (fen == 0L) "$yuan"
    else if (fen < 10L) "$yuan.0$fen"
    else "$yuan.$fen"
}
