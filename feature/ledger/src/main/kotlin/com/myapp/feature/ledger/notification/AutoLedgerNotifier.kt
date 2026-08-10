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
import com.myapp.feature.ledger.data.TransactionDirection
import com.myapp.feature.ledger.ui.yuanWithSymbol
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID = "auto_ledger"

/**
 * 自动记账结果通知（PRD 3.6.1/3.6.2）。
 *
 * 监听服务解析成功落库后调用 [post]：App 在后台，以系统通知形式反馈
 * 「已记录 -￥X 商户，本期剩余 ￥Y」；点通知进确认页（MainActivity + extra，
 * 由 LedgerDeepLink 转发给导航层）。确认后 [cancel] 掉对应通知。
 *
 * 用字符串类名而不是 Class 引用构造 Intent：:feature:ledger 不依赖 :app，
 * 组件名是包名（com.myapp.MainActivity）与 applicationId 无关，debug/release 一致。
 */
@Singleton
class AutoLedgerNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun post(
        transactionId: Long,
        amountCents: Long,
        direction: String,
        merchant: String?,
        category: String?,
        remainingCents: Long?,
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()

        val sign = if (direction == TransactionDirection.INCOME) "+" else "-"
        val merchantPart = merchant?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        val remainingPart = remainingCents?.let { "，本期剩余 ${it.yuanWithSymbol()}" }.orEmpty()
        val text = "已记录 $sign${amountCents.yuanWithSymbol()}$merchantPart$remainingPart"

        val contentIntent = Intent().apply {
            setClassName(context, "com.myapp.MainActivity")
            putExtra(LedgerNotifierExtras.TRANSACTION_ID, transactionId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode(transactionId),
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_auto_ledger)
            .setContentTitle("自动记账")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(transactionId), notification)
        }
    }

    /** 条目被确认/删除后撤掉对应通知。 */
    fun cancel(transactionId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(transactionId))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // LOW：不打扰（不响铃不振动），用户可在系统设置里调高
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "自动记账", NotificationManager.IMPORTANCE_LOW).apply {
                description = "自动记账解析支付通知后的结果反馈"
            },
        )
    }

    private fun notificationId(transactionId: Long): Int =
        NOTIFICATION_ID_BASE + (transactionId % NOTIFICATION_ID_SPAN).toInt()

    private fun requestCode(transactionId: Long): Int = notificationId(transactionId)

    private companion object {
        const val NOTIFICATION_ID_BASE = 10_000
        const val NOTIFICATION_ID_SPAN = 1_000_000
    }
}

/** PendingIntent extra 键，MainActivity 读取后转发给 LedgerDeepLink。 */
object LedgerNotifierExtras {
    const val TRANSACTION_ID = "com.myapp.extra.ledger_tx_id"
}
