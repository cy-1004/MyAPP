package com.myapp.core.common.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.myapp.core.common.R

private const val CHANNEL_ID = "reminders"

/**
 * 精确闹钟触发后的落地点。只做一件事：把 Intent 里的文案渲染成一条通知。
 *
 * 不在这里读数据库——[AlarmReminderScheduler.schedule] 在注册时已经把 title/body 存进了
 * PendingIntent 的 extra，触发时不需要任何 IO，符合 BroadcastReceiver 必须快进快出的约束。
 * 不用 @AndroidEntryPoint：不需要注入任何依赖。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderExtras.KEY) ?: return
        val title = intent.getStringExtra(ReminderExtras.TITLE).orEmpty()
        val body = intent.getStringExtra(ReminderExtras.BODY).orEmpty()

        ensureChannel(context)

        val contentIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = contentIntent?.let {
            android.app.PendingIntent.getActivity(
                context,
                key.hashCode(),
                it,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // 通知使用权限（POST_NOTIFICATIONS）用户可能没授予——不崩，静默跳过即可，
        // 闹钟本身已经触发，这不影响下一次调度。
        runCatching {
            NotificationManagerCompat.from(context).notify(key.hashCode(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "待办 / 纪念日 / 经期提醒"
            },
        )
    }
}
