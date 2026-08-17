package com.myapp.core.common.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.myapp.core.common.contract.ReminderScheduler
import com.myapp.core.common.contract.ReminderSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** 提醒广播的 Intent 载荷 key，[AlarmReminderScheduler] 和 [ReminderAlarmReceiver] 共用。 */
internal object ReminderExtras {
    const val KEY = "reminder_key"
    const val TITLE = "reminder_title"
    const val BODY = "reminder_body"
    const val SNOOZABLE = "reminder_snoozable"
}

/**
 * [ReminderScheduler] 的唯一实现：`AlarmManager.setExactAndAllowWhileIdle`。
 *
 * 不用 WorkManager——ColorOS 会冻结后台，WorkManager 的周期任务在这台机器上不可靠（PRD 9.3）。
 * 精确闹钟能穿透 Doze，是目前唯一验证过可靠的方案。
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    // 用 Provider 而不是直接注入 Set<ReminderSource>：各 feature 的 ReminderSource
    // 实现（如 TodoRepository）反过来又依赖 ReminderScheduler 本身去注册/取消闹钟，
    // 直接注入会在 Dagger 图里形成循环依赖。Provider 把取值推迟到 rescheduleAll()
    // 真正调用的那一刻，循环就断开了。
    private val sources: Provider<Set<@JvmSuppressWildcards ReminderSource>>,
) : ReminderScheduler {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(
        key: String,
        triggerAtMillis: Long,
        title: String,
        body: String,
        snoozable: Boolean,
    ) {
        val pendingIntent = buildPendingIntent(key, title, body, snoozable, create = true) ?: return
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    override fun cancel(key: String) {
        val pendingIntent = buildPendingIntent(key, title = "", body = "", snoozable = false, create = false)
            ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * 开机 / 应用更新后重建全部闹钟——Android 重启会清空全部 AlarmManager 注册（PRD 9.3）。
     * 从各 feature 的 [ReminderSource] 收集待提醒项，逐条重新调用 [schedule]。
     */
    override suspend fun rescheduleAll() {
        for (source in sources.get()) {
            for (reminder in source.pendingReminders()) {
                schedule(reminder.key, reminder.triggerAtMillis, reminder.title, reminder.body, reminder.snoozable)
            }
        }
    }

    /**
     * @param create false 时用于 [cancel]：`FLAG_NO_CREATE` 让系统只在存在同 key 的旧
     *   PendingIntent 时才返回，否则返回 null——避免凭空创建一个新的又立刻取消。
     */
    private fun buildPendingIntent(
        key: String,
        title: String,
        body: String,
        snoozable: Boolean,
        create: Boolean,
    ): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderExtras.KEY, key)
            putExtra(ReminderExtras.TITLE, title)
            putExtra(ReminderExtras.BODY, body)
            putExtra(ReminderExtras.SNOOZABLE, snoozable)
        }
        // key.hashCode() 是 requestCode 到 PendingIntent 的稳定映射；
        // FLAG_UPDATE_CURRENT 让同 key 重复注册直接覆盖旧的触发时间与文案。
        var flags = PendingIntent.FLAG_IMMUTABLE
        flags = flags or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, key.hashCode(), intent, flags)
    }
}
