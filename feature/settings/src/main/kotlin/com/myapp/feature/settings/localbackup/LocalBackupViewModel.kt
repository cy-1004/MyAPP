package com.myapp.feature.settings.localbackup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.settings.backup.LocalBackupRepository
import com.myapp.feature.settings.backup.LocalBackupSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一次性提示（成功/失败），消费后清空。 */
data class LocalBackupMessage(val text: String, val isError: Boolean)

data class LocalBackupUiState(
    /** 导出或导入进行中：期间禁用按钮，避免并发操作互相覆盖。 */
    val busy: Boolean = false,
    val busyLabel: String = "",
    val message: LocalBackupMessage? = null,
)

/**
 * 本地备份页 ViewModel（PRD 4.6）。
 *
 * 密码不落盘：本地备份的密码是「这个文件的密码」，用户可以每次用不同的，
 * 与云备份那个存进 [com.myapp.feature.settings.backup.SecretStore] 的长期密码不是一回事。
 */
@HiltViewModel
class LocalBackupViewModel @Inject constructor(
    private val repository: LocalBackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocalBackupUiState())
    val uiState: StateFlow<LocalBackupUiState> = _uiState.asStateFlow()

    fun suggestedFileName(): String = repository.suggestedFileName()

    fun export(uri: Uri, passphrase: String) {
        run("正在导出…", "导出失败") {
            val summary = repository.export(uri, passphrase)
            "已导出 ${summary.rowCount} 条记录、${summary.imageCount} 张图片（${formatSize(summary.sizeBytes)}）"
        }
    }

    fun import(uri: Uri, passphrase: String) {
        run("正在恢复…", "恢复失败") {
            val summary = repository.import(uri, passphrase)
            "已恢复 ${summary.rowCount} 条记录、${summary.imageCount} 张图片"
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun run(busyLabel: String, failureLabel: String, block: suspend () -> String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, busyLabel = busyLabel) }
            val message = runCatching { block() }.fold(
                onSuccess = { LocalBackupMessage(it, isError = false) },
                // 失败原因要原样透出：「密码不正确」和「文件被截断」用户的下一步动作完全不同
                onFailure = { LocalBackupMessage(it.message ?: failureLabel, isError = true) },
            )
            _uiState.update { it.copy(busy = false, busyLabel = "", message = message) }
        }
    }
}

/** 备份密码的最短长度。与云备份保持一致。 */
const val MIN_LOCAL_PASSPHRASE_LENGTH = 8

internal fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
