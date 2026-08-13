package com.myapp.feature.settings.cloudbackup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 云备份设置页（PRD 3.13）。
 *
 * 三种状态：未登录（登录 + 设置备份密码）、已登录（自动备份开关 + 立即备份 + 历史列表）、
 * 操作中（禁用按钮）。登录失效会自动退回未登录态，而不是静默失败。
 */
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CloudBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var restoreTarget by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("云备份") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = innerPadding.calculateTopPadding() + Spacing.md,
                bottom = innerPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!state.signedIn) {
                item(key = "signin") { SignInCard(busy = state.busy, onSignIn = viewModel::signIn) }
            } else {
                item(key = "status") {
                    StatusCard(
                        state = state,
                        onToggleAuto = viewModel::setAutoBackupEnabled,
                        onBackupNow = viewModel::backupNow,
                        onSignOut = viewModel::signOut,
                    )
                }
                item(key = "history_title") {
                    Text(
                        text = "备份历史",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
                    )
                }
                if (state.loadingHistory && state.backups.isEmpty()) {
                    item(key = "loading") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                            horizontalArrangement = Arrangement.Center,
                        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                    }
                } else if (state.backups.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "还没有任何备份，点「立即备份」上传第一份。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textSecondary,
                            modifier = Modifier.padding(Spacing.lg),
                        )
                    }
                } else {
                    items(state.backups, key = { it.id }) { record ->
                        BackupRow(
                            createdAt = formatCloudTime(record.createdAt),
                            subtitle = "${formatSize(record.sizeBytes)} · v${record.appVersion} · schema ${record.dbSchemaVersion}",
                            enabled = !state.busy,
                            onRestore = { restoreTarget = record.id },
                            onDelete = { viewModel.delete(record.id) },
                        )
                    }
                }
            }
        }
    }

    restoreTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("用这份备份覆盖本机数据？") },
            text = {
                Text(
                    "本机现有的全部数据会被清空，替换为这份备份的内容。" +
                        "此操作不可撤销，建议先「立即备份」一次当前数据。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restore(id)
                    restoreTarget = null
                }) { Text("覆盖恢复") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SignInCard(busy: Boolean, onSignIn: (String, String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passphrase by rememberSaveable { mutableStateOf("") }

    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("登录云备份账号", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "备份会加密后上传到你自己的腾讯云开发环境。云端只存密文，" +
                    "「备份密码」只保存在本机——忘了它，云端的备份就再也解不开。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textSecondary,
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("账号（昵称或邮箱）") },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("云账号密码") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text("备份密码（至少 8 位）") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSignIn(username, password, passphrase) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "登录中…" else "登录并启用") }
        }
    }
}

@Composable
private fun StatusCard(
    state: CloudBackupUiState,
    onToggleAuto: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onSignOut: () -> Unit,
) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("每日自动备份", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "每天上传一次全量快照，不是实时同步",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }
                Switch(
                    checked = state.autoBackupEnabled,
                    onCheckedChange = onToggleAuto,
                    enabled = !state.busy,
                )
            }

            HorizontalDivider()

            Text(
                text = if (state.lastSuccessAt > 0) {
                    "上次备份：${formatLocalTime(state.lastSuccessAt)}"
                } else {
                    "还没有成功备份过"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.lastError.isNotBlank()) {
                Text(
                    text = "上次失败：${state.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onBackupNow, enabled = !state.busy) {
                    Text(if (state.busy) "处理中…" else "立即备份")
                }
                TextButton(onClick = onSignOut, enabled = !state.busy) { Text("退出登录") }
            }
        }
    }
}

@Composable
private fun BackupRow(
    createdAt: String,
    subtitle: String,
    enabled: Boolean,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(createdAt, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
            TextButton(onClick = onRestore, enabled = enabled) { Text("恢复") }
            TextButton(onClick = onDelete, enabled = enabled) { Text("删除") }
        }
    }
}

private val displayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

private fun formatLocalTime(epochMillis: Long): String =
    displayFormatter.format(Instant.ofEpochMilli(epochMillis))

/**
 * 云端 `created_at` 是 Postgres 的 timestamptz，PostgREST 返回带时区偏移的
 * ISO 文本（如 `2026-08-13T04:21:33.123456+00:00`）——`Instant.parse` 只认 `Z` 结尾，
 * 这里必须用 [OffsetDateTime]。解析失败就原样展示，展示层不该为一个时间格式崩掉整页。
 */
private fun formatCloudTime(raw: String): String = runCatching {
    displayFormatter.format(OffsetDateTime.parse(raw).toInstant())
}.getOrElse { raw }
