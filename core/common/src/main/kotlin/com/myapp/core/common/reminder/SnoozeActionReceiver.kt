package com.myapp.core.common.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.myapp.core.common.contract.ReminderScheduler
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.time.AppTime
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 稍后提醒的延后时长（分钟），供 [ReminderAlarmReceiver] 与本类共用（PRD 3.3）。 */
internal object SnoozeOptions {
    const val FIFTEEN_MINUTES = 15
    const val ONE_HOUR = 60
    const val TOMORROW = 24 * 60
}

internal object SnoozeExtras {
    const val DELAY_MINUTES = "snooze_delay_minutes"
}

/**
 * 通知上「15 分钟 / 1 小时 / 明天」三个稍后提醒按钮的落地点。
 *
 * 只做两件事：撤掉当前这条通知、把同一个 key 的闹钟重新调度到延后的时间点——
 * 复用 [ReminderScheduler.schedule] 的覆盖式重排语义，不需要另起一套调度逻辑。
 */
@AndroidEntryPoint
class SnoozeActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderExtras.KEY) ?: return
        val title = intent.getStringExtra(ReminderExtras.TITLE).orEmpty()
        val body = intent.getStringExtra(ReminderExtras.BODY).orEmpty()
        val delayMinutes = intent.getIntExtra(SnoozeExtras.DELAY_MINUTES, 0)
        if (delayMinutes <= 0) return

        NotificationManagerCompat.from(context).cancel(key.hashCode())

        // schedule() 内部要用 AlarmManager，不是纯内存操作，稳妥起见还是走 goAsync
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val triggerAt = AppTime.now() + delayMinutes * 60_000L
                reminderScheduler.schedule(key, triggerAt, title, body, snoozable = true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
