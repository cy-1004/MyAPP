package com.myapp.feature.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.security.SecretStore
import com.myapp.core.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiSettingsState(
    val enabled: Boolean = false,
    val webSearch: Boolean = true,
    val hasApiKey: Boolean = false,
)

/**
 * AI 设置（PRD 3.14）。
 *
 * Key 存在 [SecretStore]（Android Keystore 加密）而不是 DataStore：
 * DataStore 是明文文件，凭证不该躺在那里。**这个 ViewModel 从不把 key 的值读出来给 UI**，
 * 只暴露「有没有」——填过之后界面显示的是掩码，想换就重填一次，
 * 不提供「看一眼当前 key」的入口（那等于给任何拿到手机的人一个复制按钮）。
 */
@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val secrets: SecretStore,
) : ViewModel() {

    // 订阅 revision 而不是自己维护一个布尔：写入方也可能是别处（比如以后的导入配置）
    private val hasApiKey = secrets.revision.map { secrets[SecretStore.KEY_DEEPSEEK_API_KEY] != null }

    val state: StateFlow<AiSettingsState> = combine(
        preferences.aiEnabled,
        preferences.aiWebSearchEnabled,
        hasApiKey,
    ) { enabled, webSearch, keyPresent ->
        AiSettingsState(enabled = enabled, webSearch = webSearch, hasApiKey = keyPresent)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiSettingsState(hasApiKey = secrets[SecretStore.KEY_DEEPSEEK_API_KEY] != null),
    )

    /** 开启前必须先弹知情同意，这个方法只负责落值——弹窗由 UI 保证（PRD 3.14）。 */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAiEnabled(enabled)
            // 关掉就把上次的分析结果一并清掉：留着一段用私密数据换来的文字，
            // 与用户「我不想再用这个功能了」的意思相悖
            if (!enabled) preferences.clearPeriodAiResult()
        }
    }

    fun setWebSearch(enabled: Boolean) {
        viewModelScope.launch { preferences.setAiWebSearchEnabled(enabled) }
    }

    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        secrets[SecretStore.KEY_DEEPSEEK_API_KEY] = trimmed
    }

    fun clearApiKey() {
        secrets[SecretStore.KEY_DEEPSEEK_API_KEY] = null
        // 没有 key 就没法再分析，缓存的结论也一并清掉，避免「已经清空了却还看得到」
        viewModelScope.launch { preferences.clearPeriodAiResult() }
    }
}
