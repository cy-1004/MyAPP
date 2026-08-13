package com.myapp.feature.ledger.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.myapp.feature.ledger.R
import com.myapp.feature.ledger.data.AlertKind
import com.myapp.feature.ledger.ui.yuanWithSymbol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "budget_alert"
private const val NOTIFICATION_ID = 20_101

/** 通知 PendingIntent extra 键，[BudgetAlertNotifier] 与 MainActivity 共用。 */
object BudgetAlertNotifierExtras {
    const val OPEN_BUDGET = "com.myapp.extra.open_budget"
}

/**
 * 预算预警通知（PRD 3.6.2：80%/100% 各一次性通知）。
 *
 * 结构照抄 `AutoLedgerNotifier`（前置检查 + `runCatching` 包 `notify()`），
 * `IMPORTANCE_DEFAULT` 比 `auto_ledger` 的 `IMPORTANCE_LOW` 高一档——
 * 这是用户主动该知道的阈值事件，不是每笔记账都有的路由确认。
 */
@Singleton
class BudgetAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun post(kind: AlertKind, spentCents: Long, budgetCents: Long) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()

        val (title, text) = when (kind) {
            AlertKind.REACHED_80 -> {
                val remaining = (budgetCents - spentCents).coerceAtLeast(0L)
                "本期预算已用 80%" to "剩余 ${remaining.yuanWithSymbol()}"
            }
            AlertKind.REACHED_100 -> {
                val overspent = spentCents - budgetCents
                "本期预算已用完" to "已超支 ${overspent.yuanWithSymbol()}"
            }
        }

        val contentIntent = Intent().apply {
            setClassName(context, "com.myapp.MainActivity")
            putExtra(BudgetAlertNotifierExtras.OPEN_BUDGET, true)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_auto_ledger)
            .setContentTitle(title)
            .setContentText(text)
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
            NotificationChannel(CHANNEL_ID, "预算预警", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "本期预算消耗达到 80%/100% 时提醒"
            },
        )
    }
}
