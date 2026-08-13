package com.myapp.feature.knowledge.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.time.AppTime
import com.myapp.core.datastore.AppPreferences
import com.myapp.feature.knowledge.data.KnowledgeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 每日 08:00 知识点推送闹钟（PRD 3.8）。
 *
 * 照抄 `WidgetAlarmReceiver` 的自续期模式：不用 WorkManager 周期任务
 * （ColorOS 冻结后台，PRD 9.3 已验证不可靠），改用 `AlarmManager` 精确闹钟，
 * 触发时先排下一天的闹钟再处理今天，链条不会因为某一次触发而断掉。
 *
 * 用 `setExactAndAllowWhileIdle`（而不是 `WidgetAlarmReceiver` 用的 `setExact`）：
 * 这条闹钟直接对应用户可见的通知时间点，精度要求比小组件的午夜刷新更高，
 * 与 `AlarmReminderScheduler` 保持同一个可靠性基准。
 */
@AndroidEntryPoint
class KnowledgeDailyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: KnowledgeRepository

    @Inject
    lateinit var preferences: AppPreferences

    @Inject
    lateinit var notifier: KnowledgeNotifier

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        scheduleNext(context)
        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                if (preferences.knowledgeDailyPushEnabled.first()) {
                    repository.pickDailyKnowledge()?.let { notifier.notify(it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 0x4B37
        private const val HOUR = 8
        private const val MINUTE = 0

        /** 排下一次 08:00 的精确闹钟：今天还没到就是今天，否则是明天。幂等：覆盖同一个 PendingIntent。 */
        fun scheduleNext(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = AppTime.now()
            val todayAt = AppTime.run { today().toEpochMilliAtTime(HOUR, MINUTE) }
            val next = if (todayAt > now) todayAt else AppTime.run { today().plusDays(1).toEpochMilliAtTime(HOUR, MINUTE) }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context))
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context))
        }

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, KnowledgeDailyReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
