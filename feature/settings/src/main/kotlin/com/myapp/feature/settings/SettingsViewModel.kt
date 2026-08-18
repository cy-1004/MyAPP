package com.myapp.feature.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.keepalive.KeepAliveStatusChecker
import com.myapp.core.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 设置页状态（PRD 3.6.1 设置入口）。
 *
 * 目前只有「自动记账」卡片：显示通知使用权是否已开启，
 * 点按跳转系统通知使用权设置页。状态在每次页面回到前台时刷新
 * （Screen 用 LifecycleResumeEffect 调 [refresh]）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val statusChecker: KeepAliveStatusChecker,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val notificationListenerEnabled: Boolean
        get() = statusChecker.isNotificationListenerEnabled()

    /**
     * 服务是否真的连上了。覆盖安装后 [notificationListenerEnabled] 仍为 true 但这里是 false，
     * 此时自动记账实际不工作，设置页要如实说出来而不是显示「已开启」（PRD 9.3）。
     */
    val notificationListenerConnected: Boolean
        get() = statusChecker.isNotificationListenerConnected()

    /**
     * 笔记通知栏常驻快捷入口开关（PRD 3.4）。
     *
     * 这里只写 DataStore，不直接挂通知--`:feature:settings` 不能依赖 `:feature:note`。
     * 真正挂/撤这条通知的是 `MainActivity`，它收集同一个 Flow。
     */
    val noteQuickEntryEnabled: Flow<Boolean> = preferences.noteQuickEntryEnabled

    fun setNoteQuickEntryEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setNoteQuickEntryEnabled(enabled) }
    }

    /** 跳转系统设置开启/关闭通知使用权。 */
    fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
