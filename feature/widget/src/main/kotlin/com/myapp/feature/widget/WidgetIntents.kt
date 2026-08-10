package com.myapp.feature.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.myapp.feature.widget.data.WidgetScreens

/**
 * 小组件 → App 的启动意图（PRD 3.10）。
 *
 * MainActivity 读 [EXTRA_SCREEN] 后经 WidgetNavTarget 导航到对应页面；
 * 冷启动时目标必须随 Intent 走，不能只靠进程内 StateFlow。
 *
 * 不用 `Intent(context, MainActivity::class.java)`：小组件模块不能依赖 :app
 * （PRD 4.7.1），按类名启动解耦。data 用 `myapp-widget://` scheme 区分不同
 * 目标页面，避免固定 requestCode 下 extras 互相覆盖。
 */
object WidgetIntents {
    const val EXTRA_SCREEN = "com.myapp.extra.widget.screen"

    fun openScreenAction(context: Context, screen: String): Action {
        // 按类名启动而不是 Intent(context, MainActivity::class.java)：widget 不能依赖 :app
        // 注意不能用 Intent(context, "com.myapp.MainActivity") —— 那是 action 构造器
        val intent = Intent().setClassName(context, "com.myapp.MainActivity")
        intent.data = Uri.parse("myapp-widget://open/$screen")
        intent.putExtra(EXTRA_SCREEN, screen)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return actionStartActivity(intent)
    }

    /** 快捷记一笔。 */
    fun openLedgerNew(context: Context): Action = openScreenAction(context, WidgetScreens.LEDGER_NEW)
}
