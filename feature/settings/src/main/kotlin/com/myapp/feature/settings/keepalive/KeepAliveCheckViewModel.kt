package com.myapp.feature.settings.keepalive

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.keepalive.KeepAliveStatusChecker
import com.myapp.core.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 保活自检向导的 ViewModel（PRD 9.3）。
 *
 * 检测项分四类，UI 渲染方式不同（见 [KeepAliveCheckItem.Category]）。
 * 跳转系统设置由 VM 用 ApplicationContext startActivity--
 * ColorOS 私有设置页用 resolveActivity + try/catch 兜底到应用详情页。
 */
@HiltViewModel
class KeepAliveCheckViewModel @Inject constructor(
    private val statusChecker: KeepAliveStatusChecker,
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _items = MutableStateFlow<List<KeepAliveCheckItem>>(emptyList())
    val items: StateFlow<List<KeepAliveCheckItem>> = _items.asStateFlow()

    /**
     * 从 DataStore 加载的手动项勾选状态。
     *
     * 手动项代表用户在系统设置里做的物理操作，不会因为离开 App 就消失，
     * 所以勾选状态也要持久化--否则每次复查都得重新勾。
     */
    private val _manualDone = MutableStateFlow<Set<String>>(emptySet())

    private val _completed = Channel<Unit>(Channel.BUFFERED)
    val completed = _completed.receiveAsFlow()

    init {
        viewModelScope.launch {
            appPreferences.keepAliveManualDone.collect { done ->
                _manualDone.value = done
                rebuildItems()
            }
        }
    }

    /**
     * 重新检测自动项（用户从系统设置返回后调用）。
     * 手动项状态从 [_manualDone] 取，不重置。
     */
    fun refresh() {
        rebuildItems()
    }

    private fun rebuildItems() {
        val done = _manualDone.value
        _items.value = buildList {
            add(autoBattery())
            add(autoNotification())
            add(readonlyExactAlarm())
            add(autoNotificationListener())
            add(manualAutostart().copy(manualDone = KeepAliveCheckIds.AUTOSTART in done))
            add(manualBackground().copy(manualDone = KeepAliveCheckIds.BACKGROUND in done))
            add(manualLockTask().copy(manualDone = KeepAliveCheckIds.LOCK_TASK in done))
        }
    }

    /** 手动项勾选/取消勾选。立即更新 UI + 持久化到 DataStore。 */
    fun markManualDone(id: String, checked: Boolean) {
        _manualDone.value = if (checked) _manualDone.value + id else _manualDone.value - id
        rebuildItems()
        viewModelScope.launch {
            appPreferences.setKeepAliveManualDone(id, checked)
        }
    }

    /** 跳转系统设置页（自动检测项）。 */
    fun openSystemSettings(id: String) {
        val intent = when (id) {
            KeepAliveCheckIds.BATTERY ->
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            KeepAliveCheckIds.NOTIFICATION ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            KeepAliveCheckIds.NOTIFICATION_LISTENER ->
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            else -> return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * 尝试直达 ColorOS 私有设置页（自启动/后台活动）。
     * resolveActivity 为 null 或 startActivity 抛异常时兜底到应用详情页。
     */
    fun tryOpenColorOsSetting(id: String) {
        val privateIntent = when (id) {
            KeepAliveCheckIds.AUTOSTART, KeepAliveCheckIds.BACKGROUND -> Intent().apply {
                component = ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                )
            }
            else -> {
                openAppDetails()
                return
            }
        }
        if (privateIntent.resolveActivity(context.packageManager) != null) {
            privateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(privateIntent) }.onFailure { openAppDetails() }
        } else {
            openAppDetails()
        }
    }

    private fun openAppDetails() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** 完成向导：写偏好 + 发事件让 Screen 导航回 Home。 */
    fun complete() {
        viewModelScope.launch {
            appPreferences.setKeepAliveChecked(true)
            _completed.send(Unit)
        }
    }

    // ---- 检测项构建 ----

    private fun autoBattery() = KeepAliveCheckItem(
        id = KeepAliveCheckIds.BATTERY,
        title = "电池优化白名单",
        description = "后台不被冻结，提醒闹钟才能稳定触发",
        category = KeepAliveCheckItem.Category.AUTO,
        status = if (statusChecker.isBatteryOptimizationIgnored()) {
            KeepAliveCheckItem.Status.PASSED
        } else {
            KeepAliveCheckItem.Status.NOT_PASSED
        },
        actionLabel = "去设置",
    )

    private fun autoNotification() = KeepAliveCheckItem(
        id = KeepAliveCheckIds.NOTIFICATION,
        title = "通知权限",
        description = "提醒闹钟触发后需要通知权限才能弹通知",
        category = KeepAliveCheckItem.Category.AUTO,
        status = if (statusChecker.areNotificationsEnabled()) {
            KeepAliveCheckItem.Status.PASSED
        } else {
            KeepAliveCheckItem.Status.NOT_PASSED
        },
        actionLabel = "请求权限",
    )

    private fun readonlyExactAlarm() = KeepAliveCheckItem(
        id = KeepAliveCheckIds.EXACT_ALARM,
        title = "精确闹钟",
        description = "已声明 USE_EXACT_ALARM，安装即授予",
        category = KeepAliveCheckItem.Category.READONLY,
        status = if (statusChecker.canScheduleExactAlarms()) {
            KeepAliveCheckItem.Status.PASSED
        } else {
            KeepAliveCheckItem.Status.NOT_PASSED
        },
    )

    private fun autoNotificationListener() = KeepAliveCheckItem(
        id = KeepAliveCheckIds.NOTIFICATION_LISTENER,
        title = "通知使用权",
        description = "支付通知自动记账需要在此开启监听能力",
        category = KeepAliveCheckItem.Category.AUTO,
        status = if (statusChecker.isNotificationListenerEnabled()) {
            KeepAliveCheckItem.Status.PASSED
        } else {
            KeepAliveCheckItem.Status.NOT_PASSED
        },
        actionLabel = "去开启",
    )

    private fun manualAutostart() = KeepAliveCheckItem(
        id = KeepAliveCheckIds.AUTOSTART,
        title = "允许自启动",
        description = "重启后闹钟不重建、通知监听不恢复",
        category = KeepAliveCheckItem.Category.MANUAL,
        status = KeepAliveCheckItem.Status.PENDING_MANUAL,
        pathHint = "设置 → 应用管理 → MyAPP → 耗电管理 → 允许自启动",
        actionLabel = "我已设置",
        canTryDirectOpen = true,
    )

    private fun manualBackground() = KeepAliveCheckItem(
        id = KeepAliveCheckIds.BACKGROUND,
        title = "允许后台活动",
        description = "WorkManager 周期任务被杀，RSS/知识池不更新",
        category = KeepAliveCheckItem.Category.MANUAL,
        status = KeepAliveCheckItem.Status.PENDING_MANUAL,
        pathHint = "设置 → 应用管理 → MyAPP → 耗电管理 → 允许后台活动",
        actionLabel = "我已设置",
        canTryDirectOpen = true,
    )

    private fun manualLockTask() = KeepAliveCheckItem(
        id = KeepAliveCheckIds.LOCK_TASK,
        title = "锁定后台任务",
        description = "手动清理时被误杀；在最近任务界面下拉卡片锁定",
        category = KeepAliveCheckItem.Category.MANUAL,
        status = KeepAliveCheckItem.Status.PENDING_MANUAL,
        pathHint = "最近任务界面 → 下拉 MyAPP 卡片 → 出现锁图标",
        actionLabel = "我已设置",
        canTryDirectOpen = false,
    )
}
