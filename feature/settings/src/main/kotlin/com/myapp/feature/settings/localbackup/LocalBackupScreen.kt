package com.myapp.feature.settings.localbackup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 本地备份页（PRD 4.6）：导出加密备份文件到用户自己选的位置，或从备份文件覆盖恢复。
 *
 * 与云备份（3.13）的分工写在页面上方的说明里，一句话：**图片只有这条路能迁移**。
 *
 * 两条流程的步骤顺序是刻意错开的：
 * - 导出：先设密码 → 再选保存位置（SAF 的保存对话框结束后立刻开始写，中间不该再弹框）
 * - 导入：先选文件 → 再输密码 → 再确认覆盖（覆盖确认放最后，是用户点下去就真的执行的那一下）
 */
@Composable
fun LocalBackupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocalBackupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var exportPassphrase by remember { mutableStateOf<String?>(null) }
    var askExportPassphrase by remember { mutableStateOf(false) }
    // 导入分三步：选文件 → 输密码 → 确认覆盖。两个状态一起决定当前停在哪一步。
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importPassphrase by remember { mutableStateOf<String?>(null) }

    fun cancelImport() {
        importUri = null
        importPassphrase = null
    }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_TYPE),
    ) { uri: Uri? ->
        val passphrase = exportPassphrase
        exportPassphrase = null
        if (uri != null && passphrase != null) viewModel.export(uri, passphrase)
    }

    val openLauncher = rememberLauncherForActivityResult(
        // 不限类型：自定义扩展名的文件在部分文件管理器里 MIME 是 application/octet-stream，
        // 也有认成 */* 的，按类型过滤会让用户在选择器里根本看不到自己的备份文件
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> importUri = uri }

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
                title = { Text("本地备份") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AppCard {
                Text("导出全部数据", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "把数据库和笔记图片打包成一个加密文件，存到你选的位置（手机存储、U 盘、网盘都行）。" +
                        "云备份不含图片，换机要保住笔记里的插图，只能靠这条。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
                Button(
                    onClick = { askExportPassphrase = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出") }
            }

            AppCard {
                Text("从备份文件恢复", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "恢复会用备份文件的内容整体覆盖本机现有数据，包括笔记图片，且无法撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                    modifier = Modifier.padding(vertical = Spacing.xs),
                )
                OutlinedButton(
                    onClick = { openLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("选择备份文件") }
            }

            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        text = state.busyLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.md),
                    )
                }
            }

            Text(
                text = "备份密码只用来加密这个文件，App 不会保存它。忘了密码这份备份就再也打不开——" +
                    "没有找回途径，请记在密码管理器里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.textSecondary,
            )
        }
    }

    if (askExportPassphrase) {
        PassphraseDialog(
            title = "设置备份密码",
            confirmLabel = "选择保存位置",
            requireRepeat = true,
            onDismiss = { askExportPassphrase = false },
            onConfirm = { passphrase ->
                askExportPassphrase = false
                exportPassphrase = passphrase
                createLauncher.launch(viewModel.suggestedFileName())
            },
        )
    }

    val pendingUri = importUri
    val pendingPassphrase = importPassphrase
    if (pendingUri != null && pendingPassphrase == null) {
        PassphraseDialog(
            title = "输入这份备份的密码",
            confirmLabel = "下一步",
            requireRepeat = false,
            onDismiss = ::cancelImport,
            onConfirm = { importPassphrase = it },
        )
    } else if (pendingUri != null && pendingPassphrase != null) {
        AlertDialog(
            onDismissRequest = ::cancelImport,
            title = { Text("确认覆盖本机数据？") },
            text = { Text("现有的待办、纪念日、账目、笔记与图片都会被这份备份替换，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    cancelImport()
                    viewModel.import(pendingUri, pendingPassphrase)
                }) { Text("覆盖恢复") }
            },
            dismissButton = { TextButton(onClick = ::cancelImport) { Text("取消") } },
        )
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    confirmLabel: String,
    requireRepeat: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }

    val error = when {
        passphrase.length < MIN_LOCAL_PASSPHRASE_LENGTH ->
            "至少 $MIN_LOCAL_PASSPHRASE_LENGTH 位"
        requireRepeat && repeat != passphrase -> "两次输入不一致"
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("备份密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireRepeat) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text("再输一次") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (error != null && passphrase.isNotEmpty()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase) },
                enabled = error == null,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** SAF 保存时给的类型。自定义扩展名没有标准 MIME，用通用二进制。 */
private const val MIME_TYPE = "application/octet-stream"
