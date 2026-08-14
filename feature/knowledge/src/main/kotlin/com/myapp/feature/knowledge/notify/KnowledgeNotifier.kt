package com.myapp.feature.knowledge.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.myapp.core.common.R
import com.myapp.core.common.contract.KnowledgeItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "knowledge_daily"
private const val NOTIFICATION_ID = 20_001

/** 通知 PendingIntent extra 键，[KnowledgeNotifier] 与 MainActivity 共用。 */
object KnowledgeNotifierExtras {
    const val SOURCE_ID = "com.myapp.extra.knowledge_daily_source_id"
    const val IS_NOTE = "com.myapp.extra.knowledge_daily_is_note"

    /**
     * 来源类型（[com.myapp.core.common.contract.KnowledgeItemKind] 的 name）。
     *
     * 改版后 SOURCE_ID 存的可能是面试题 id、知识源 id 或笔记 id，是三个不同的 id 空间，
     * 光靠 [IS_NOTE] 已经分不清了。[IS_NOTE] 保留是为了兼容改版前发出、
     * 用户第二天才点开的通知（那些 PendingIntent 里没有这个 extra）。
     */
    const val KIND = "com.myapp.extra.knowledge_daily_kind"
}

/**
 * 每日知识点推送通知（PRD 3.8）。
 *
 * 前置检查 + `runCatching` 包 `notify()` 这套做法照抄 `AutoLedgerNotifier`——
 * 已经在真机验证过的模式，用户没授予通知权限时静默跳过，不影响闹钟本身的调度。
 */
@Singleton
class KnowledgeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notify(item: KnowledgeItem) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()

        val title = "今日知识点 · ${item.sourceName}"
        val text = item.title.ifBlank { item.summary }.let { "$it\n${item.summary}" }

        val contentIntent = Intent().apply {
            setClassName(context, "com.myapp.MainActivity")
            putExtra(KnowledgeNotifierExtras.SOURCE_ID, item.sourceId)
            putExtra(KnowledgeNotifierExtras.IS_NOTE, item.isNoteFallback)
            putExtra(KnowledgeNotifierExtras.KIND, item.kind.name)
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
            .setContentTitle(title)
            .setContentText(item.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "每日知识点", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每天推送一条知识池内容或笔记摘录"
            },
        )
    }
}
