package com.myapp.core.common.keepalive

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保活相关系统状态检测（PRD 9.3）。
 *
 * 把 PowerManager / AlarmManager / NotificationManager 的调用集中在这里，
 * 不散落到 ViewModel--一是便于单测替换，二是 ColorOS 的私有设置项检测
 * 以后可能要加 fallback 逻辑，集中维护。
 */
@Singleton
class KeepAliveStatusChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val listenerConnection: NotificationListenerConnection,
) {
    /** 电池优化白名单：true 表示已加入白名单，后台不被冻结。 */
    fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 通知权限（POST_NOTIFICATIONS，API 33+）。 */
    fun areNotificationsEnabled(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 精确闹钟权限（API 31+）。
     *
     * manifest 声明了 USE_EXACT_ALARM（安装即授予），这里通常恒 true--
     * 保留检测是为了将来若改用 SCHEDULE_EXACT_ALARM（需用户授权）时不用改 UI。
     */
    fun canScheduleExactAlarms(): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * 通知使用权**授权状态**（NotificationListenerService）。
     *
     * 注意：授权 ≠ 服务真的连上了。覆盖安装后这里仍返回 true 但通知收不到，
     * 判断「自动记账现在能不能工作」必须用 [isNotificationListenerConnected]。
     */
    fun isNotificationListenerEnabled(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    /**
     * 通知监听服务是否**真的处于连接状态**（见 [NotificationListenerConnection] 的说明）。
     *
     * 授权为 true 而这里为 false，就是覆盖安装丢绑定那个坑，
     * 解法是关掉再打开一次通知使用权（或等 requestRebind 生效）。
     */
    fun isNotificationListenerConnected(): Boolean = listenerConnection.connected.value

    /**
     * 是否为首次安装（非升级）。
     *
     * firstInstallTime 在安装后不变，lastUpdateTime 每次升级更新。
     * 两者相等 = 本次是首次安装（或卸载后重装）。
     * 用于「仅首次安装强制走保活向导」的判断。
     */
    fun isFirstInstall(): Boolean {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.firstInstallTime == info.lastUpdateTime
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /** 通知通道是否已启用（用户可能在系统设置里关了通知）。 */
    @Suppress("unused")
    fun areNotificationChannelsEnabled(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.notificationChannels.none { it.importance == NotificationManager.IMPORTANCE_NONE }
    }
}
