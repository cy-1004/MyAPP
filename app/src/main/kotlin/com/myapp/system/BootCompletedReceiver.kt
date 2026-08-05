package com.myapp.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/**
 * 开机 / 应用更新后重建全部闹钟。
 *
 * 为什么必须有（PRD 9.3）：Android 在重启后会清空所有 AlarmManager 注册。
 * 若不重建，重启一次全部提醒就永久失效——而 ColorOS 的省电策略下重启很常见。
 *
 * TODO 注入 ReminderScheduler 并调用 rescheduleAll()，
 *      用 goAsync() + 协程处理，避免在主线程读数据库。
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                // val pendingResult = goAsync()
                // scope.launch { reminderScheduler.rescheduleAll(); pendingResult.finish() }
            }
        }
    }
}
