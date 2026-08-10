package com.myapp.feature.widget.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 小组件点击要打开的页面。与 [WidgetScreens] 的常量对应。 */
object WidgetScreens {
    const val HOME = "home"
    const val LEDGER = "ledger"
    /** 快捷记一笔 = LedgerDetail(id=0)。 */
    const val LEDGER_NEW = "ledger_new"
    const val TODO = "todo"
    const val ANNIVERSARY = "anniversary"
}

/**
 * 小组件点击 → 进程内导航深链（与 LedgerDeepLink 同一套模式）。
 *
 * 小组件是外部输入边界，PendingIntent 只负责把 MainActivity 拉起来，
 * 具体导航由 Compose 层完成：MainActivity 把目标页面写入这里，
 * MyApp 收集后 navigate 对应 Route。StateFlow 保留最新值，冷启动不丢目标。
 */
@Singleton
class WidgetNavTarget @Inject constructor() {
    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target.asStateFlow()

    fun open(screen: String) {
        _target.value = screen
    }

    fun consume() {
        _target.value = null
    }
}
