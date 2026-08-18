package com.myapp.feature.note.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.myapp.core.common.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "note_quick_entry"
private const val NOTIFICATION_ID = 30_001

/** 通知 PendingIntent extra 键，[NoteQuickEntryNotifier] 与 MainActivity 共用。 */
object NoteQuickEntryExtras {
    const val OPEN_NEW_NOTE = "com.myapp.extra.note_quick_entry"
}

/**
 * 笔记的通知栏常驻快捷入口（PRD 3.4）：一条不会自动消失的通知，点一下直接开新笔记编辑页。
 *
 * 挂/撤由 `:app` 层驱动（MainActivity 收集 `AppPreferences.noteQuickEntryEnabled`、
 * BootCompletedReceiver 开机读一次），这里只管「怎么挂」。
 *
 * 几个刻意的选择：
 * - **渠道 importance 用 MIN**：常驻通知每天都在，出声或抬头显示会变成骚扰。
 *   MIN 让它安静地待在通知栏底部，不占状态栏图标位。
 * - **不用前台服务**：只是个入口，没有要保活的后台工作，起 FGS 反而多一份被系统杀的理由
 *   和一条「正在运行」的系统提示。代价是 Android 14+ 用户可以手动划掉它——
 *   划掉不改开关，下次启动/开机会自己回来（与 M7 闹钟自愈同一个思路）。
 * - **`runCatching` 包 notify()**：没授通知权限时静默跳过，照抄 `KnowledgeNotifier`/`AutoLedgerNotifier`。
 */
@Singleton
class NoteQuickEntryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 幂等：重复调用只会覆盖同一条通知，不会堆出第二条。 */
    fun show() {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()

        val contentIntent = Intent().apply {
            setClassName(context, "com.myapp.MainActivity")
            putExtra(NoteQuickEntryExtras.OPEN_NEW_NOTE, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("记点什么")
            .setContentText("点这里立刻新建一条笔记")
            .setOngoing(true)
            // 常驻通知没有「发生时刻」，显示时间戳只会让人以为刚发生了什么事
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "笔记快捷入口", NotificationManager.IMPORTANCE_MIN).apply {
                description = "通知栏常驻的新建笔记入口，不出声不震动"
                setShowBadge(false)
            },
        )
    }
}
