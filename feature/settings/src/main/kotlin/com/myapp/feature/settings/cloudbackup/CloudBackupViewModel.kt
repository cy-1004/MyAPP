package com.myapp.feature.settings.cloudbackup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.datastore.AppPreferences
import com.myapp.feature.settings.backup.CloudBackupRepository
import com.myapp.feature.settings.backup.CloudBackupScheduler
import com.myapp.feature.settings.backup.cloud.BackupRecord
import com.myapp.feature.settings.backup.cloud.CloudBackupException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 云备份页的一次性提示（成功/失败），消费后清空。 */
data class CloudBackupMessage(val text: String, val isError: Boolean)

data class CloudBackupUiState(
    val signedIn: Boolean = false,
    val hasPassphrase: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val lastSuccessAt: Long = 0,
    val lastError: String = "",
    val backups: List<BackupRecord> = emptyList(),
    val loadingHistory: Boolean = false,
    /** 正在备份或恢复，期间禁用所有按钮避免并发操作。 */
    val busy: Boolean = false,
    val message: CloudBackupMessage? = null,
)

/**
 * 云备份设置页 ViewModel（PRD 3.13）。
 *
 * 手动「立即备份」直接调用 repository 而不是丢给 WorkManager：
 * 用户正盯着屏幕等结果，需要即时的成功/失败反馈；
 * 后台的每日任务才走 Worker。两者最终调的是同一个 `backupNow()`。
 */
@HiltViewModel
class CloudBackupViewModel @Inject constructor(
    private val repository: CloudBackupRepository,
    private val scheduler: CloudBackupScheduler,
    private val preferences: AppPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudBackupUiState())
    val uiState: StateFlow<CloudBackupUiState> = _uiState.asStateFlow()

    init {
        refreshLocalState()
        viewModelScope.launch {
            preferences.cloudBackupEnabled.collect { enabled ->
                _uiState.update { it.copy(autoBackupEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            preferences.cloudBackupLastSuccessAt.collect { at ->
                _uiState.update { it.copy(lastSuccessAt = at) }
            }
        }
        viewModelScope.launch {
            preferences.cloudBackupLastError.collect { error ->
                _uiState.update { it.copy(lastError = error) }
            }
        }
        if (repository.isSignedIn) loadHistory()
    }

    private fun refreshLocalState() {
        _uiState.update {
            it.copy(signedIn = repository.isSignedIn, hasPassphrase = repository.hasPassphrase)
        }
    }

    fun signIn(username: String, password: String, passphrase: String) {
        if (username.isBlank() || password.isBlank()) {
            showMessage("请填写账号和密码", isError = true)
            return
        }
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            showMessage("备份密码至少 $MIN_PASSPHRASE_LENGTH 位", isError = true)
            return
        }
        launchBusy(failureLabel = "登录失败") {
            repository.signIn(username.trim(), password)
            repository.setPassphrase(passphrase)
            refreshLocalState()
            showMessage("登录成功", isError = false)
            loadHistory()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            scheduler.cancelDaily()
            refreshLocalState()
            _uiState.update { it.copy(backups = emptyList()) }
            showMessage("已退出云备份", isError = false)
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setCloudBackupEnabled(enabled)
            if (enabled) scheduler.scheduleDaily() else scheduler.cancelDaily()
        }
    }

    fun backupNow() {
        launchBusy(failureLabel = "备份失败") {
            val summary = repository.backupNow()
            showMessage("备份完成：${summary.rowCount} 条记录，${formatSize(summary.sizeBytes)}", isError = false)
            loadHistory()
        }
    }

    fun restore(id: Long) {
        launchBusy(failureLabel = "恢复失败") {
            val rows = repository.restore(id)
            showMessage("已恢复 $rows 条记录", isError = false)
        }
    }

    fun delete(id: Long) {
        launchBusy(failureLabel = "删除失败") {
            repository.delete(id)
            loadHistory()
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingHistory = true) }
            runCatching { repository.listBackups() }
                .onSuccess { list -> _uiState.update { it.copy(backups = list) } }
                .onFailure { e -> handleFailure(e, "读取备份列表失败") }
            _uiState.update { it.copy(loadingHistory = false) }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private fun launchBusy(failureLabel: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            runCatching { block() }.onFailure { handleFailure(it, failureLabel) }
            _uiState.update { it.copy(busy = false) }
        }
    }

    /** 登录失效要让 UI 退回未登录态，不能只弹个错就完事（PRD 3.13）。 */
    private fun handleFailure(e: Throwable, label: String) {
        if (e is CloudBackupException && e.needsReauth) {
            _uiState.update { it.copy(signedIn = false, backups = emptyList()) }
        }
        showMessage(e.message ?: label, isError = true)
    }

    private fun showMessage(text: String, isError: Boolean) {
        _uiState.update { it.copy(message = CloudBackupMessage(text, isError)) }
    }

    private companion object {
        const val MIN_PASSPHRASE_LENGTH = 8
    }
}

/** 字节数转可读大小。备份是压缩后的密文，一般在 KB~MB 量级。 */
internal fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
