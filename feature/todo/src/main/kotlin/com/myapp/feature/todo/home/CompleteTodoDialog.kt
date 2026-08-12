package com.myapp.feature.todo.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 完成 待办 的确认对话框（PRD 3.3）。
 *
 * 主页点击待办不再直接 toggle 完成 -- 先弹这个对话框让用户确认，避免误触；
 * 同时提供「完成备注」输入框（可选），记录「只做了一半」之类的完成情况。
 *
 * 状态管理参考 [BudgetDialog]：本地 `remember` 持有输入文本，
 * 确认时通过 [onConfirm] 把文本回传给调用方，由 ViewModel 决定怎么落库。
 */
@Composable
fun CompleteTodoDialog(
    todoTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标记为已完成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = todoTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.textSecondary,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("完成备注（可选）") },
                    minLines = 2,
                    maxLines = 4,
                    shape = MaterialTheme.shapes.small,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
