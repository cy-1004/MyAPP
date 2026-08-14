package com.myapp.feature.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * AI 分析设置（PRD 3.14）。
 *
 * 这一页的重点不是开关本身，而是**开启前那次知情同意**：
 * 打开它意味着经期记录（含用户手写的文字）会离开这台设备，
 * 这件事必须让用户在点下去之前就看见，而不是写在某个「隐私说明」里等人去翻。
 */
@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var consentVisible by remember { mutableStateOf(false) }
    var keyDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI 分析") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "借助 DeepSeek 对经期记录做一段解读。经期的预测与提醒始终由本机算法负责，" +
                    "关掉 AI 或没网时功能照常。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )

            AppCard {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SwitchRow(
                        title = "启用 AI 分析",
                        subtitle = "默认关闭。开启即表示同意把经期记录发送到 DeepSeek",
                        checked = state.enabled,
                        // 关闭随时可以，开启必须先过一次说明
                        onCheckedChange = { want ->
                            if (want) consentVisible = true else viewModel.setEnabled(false)
                        },
                    )
                    SwitchRow(
                        title = "联网搜索",
                        subtitle = "让模型联网查资料，回答更贴近当下，但更慢、更贵",
                        checked = state.webSearch,
                        enabled = state.enabled,
                        onCheckedChange = viewModel::setWebSearch,
                    )
                }
            }

            AppCard {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Text("API Key", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (state.hasApiKey) {
                            "已保存（加密存放在本机，不会随备份上传）"
                        } else {
                            "还没填。到 DeepSeek 控制台创建一个，填进来才能用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        OutlinedButton(onClick = { keyDialogVisible = true }) {
                            Text(if (state.hasApiKey) "替换" else "填写")
                        }
                        if (state.hasApiKey) {
                            OutlinedButton(onClick = viewModel::clearApiKey) { Text("清空") }
                        }
                    }
                }
            }

            Text(
                text = "AI 生成的内容仅供参考，不能替代医生。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
        }
    }

    if (consentVisible) {
        AlertDialog(
            onDismissRequest = { consentVisible = false },
            title = { Text("开启前请确认") },
            text = {
                Text(
                    "开启后，点击「AI 分析」时会把你最近 6 次的经期记录、每日情况记录" +
                        "（包括你写下的文字）和经期备注发送到 DeepSeek 的服务器。\n\n" +
                        "不会发送姓名、账号或设备信息。分析只在你手动点击时发生，" +
                        "不会自动定时调用。随时可以在这里关掉。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEnabled(true)
                    consentVisible = false
                }) { Text("我知道了，开启") }
            },
            dismissButton = {
                TextButton(onClick = { consentVisible = false }) { Text("取消") }
            },
        )
    }

    if (keyDialogVisible) {
        ApiKeyDialog(
            onDismiss = { keyDialogVisible = false },
            onConfirm = {
                viewModel.saveApiKey(it)
                keyDialogVisible = false
            },
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ApiKeyDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("填写 API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("sk-…") },
                    singleLine = true,
                    // 密文显示：这东西等同于一把可以刷钱的钥匙，不该明晃晃亮在屏幕上
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "保存后会加密存放在本机，界面上不会再显示它的内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
