package com.myapp.feature.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.myapp.core.common.time.AppTime
import com.myapp.feature.widget.anniversary.AnniversaryCountdownWidget
import com.myapp.feature.widget.overview.TodayOverviewWidget
import com.myapp.feature.widget.todayexpense.TodayExpenseWidget
import com.myapp.feature.widget.todaytodo.TodayTodoWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 午夜跨天刷新小组件的精确闹钟（PRD 3.10 刷新策略）。
 *
 * 「今日」的查询边界是本地日期，跨天后不刷界面就停留在昨天。
 * Glance 的 updatePeriodMillis 是 WorkManager 周期任务，ColorOS 上不保证准时
 * （PRD 9.3 已记录），所以用 AlarmManager.setExact 设每天 0:05 的闹钟，
 * 触发时刷新全部小组件并自续下一夜。
 *
 * 兜底链：数据变更主动刷新（最快）→ 30 分钟周期（WorkManager）→ 每日跨天（本闹钟）。
 */
class WidgetAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 自续期：先安排下一夜的闹钟，再刷新（updateAll 只是 WorkManager 入队，非阻塞）。
        // updateAll 是 suspend（Glance 1.1），onReceive 不是协程环境，用 goAsync 保活。
        scheduleMidnightAlarm(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                TodayOverviewWidget().updateAll(context)
                TodayExpenseWidget().updateAll(context)
                TodayTodoWidget().updateAll(context)
                AnniversaryCountdownWidget().updateAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 0x57A14

        /** 设置明天 0:05 的精确闹钟。幂等：重复调用覆盖同一个 PendingIntent。 */
        fun scheduleMidnightAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WidgetAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val nextMidnight = with(AppTime) { today().plusDays(1).toEpochMilliAtTime(0, 5) }
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextMidnight, pending)
        }
    }
}
