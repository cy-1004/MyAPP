package com.myapp.core.common.keepalive

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NotificationListenerService 的**真实连接状态**（PRD 9.3）。
 *
 * 为什么需要这个：`NotificationManagerCompat.getEnabledListenerPackages` 只能回答
 * 「用户授权了吗」，回答不了「系统真的把服务连上了吗」。这两件事会长期不一致——
 * 覆盖安装（`adb install -r` 或应用商店更新）之后，ColorOS 会断开已有绑定却不重连，
 * 系统设置里的开关仍然显示「已开启」，而 App 一条通知都收不到。
 * 这个坑在 2026-08-14 实测过：`dumpsys notification` 里服务在
 * 「All notification listeners enabled」列表内，却不在「Live notification listeners」内，
 * 自动记账因此静默失效了好几天，未识别队列都是空的（通知压根没送到）。
 *
 * 状态由服务自己在 [android.service.notification.NotificationListenerService.onListenerConnected]
 * / `onListenerDisconnected` 里写入，是唯一可信的来源。
 *
 * 放在 :core:common 而不是 :feature:ledger：写入方是记账模块的服务，
 * 读取方是设置模块的保活自检页，两个 feature 不能互相依赖（PRD 4.7.1）。
 */
@Singleton
class NotificationListenerConnection @Inject constructor() {

    private val _connected = MutableStateFlow(false)

    /** true = 系统已绑定服务，通知能送达。 */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun onConnected() {
        _connected.value = true
    }

    fun onDisconnected() {
        _connected.value = false
    }
}
