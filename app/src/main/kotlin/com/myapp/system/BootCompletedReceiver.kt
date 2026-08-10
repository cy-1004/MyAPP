package com.myapp.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.myapp.core.common.contract.ReminderScheduler
import com.myapp.core.common.di.ApplicationScope
import com.myapp.feature.widget.WidgetAlarmReceiver
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新后重建全部闹钟。
 *
 * 为什么必须有（PRD 9.3）：Android 在重启后会清空所有 AlarmManager 注册。
 * 若不重建，重启一次全部提醒就永久失效——而 ColorOS 的省电策略下重启很常见。
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                // goAsync() 延长 receiver 生命周期到协程完成为止——
                // rescheduleAll() 要读数据库，不能在 onReceive 的主线程同步执行。
                val pendingResult = goAsync()
                applicationScope.launch {
                    try {
                        reminderScheduler.rescheduleAll()
                        // 小组件午夜跨天刷新的精确闹钟，重启后要重建（PRD 3.10）
                        WidgetAlarmReceiver.scheduleMidnightAlarm(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
