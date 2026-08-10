package com.myapp.feature.ledger.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统通知 → 确认页的进程内深链。
 *
 * 自动记账的 PendingIntent 只负责把 MainActivity 拉起来并带上账目 id，
 * 导航本身由 Compose 层完成：MainActivity 把 id 写入这里，MyApp 收集后
 * navigate 到 LedgerDetail(id)。StateFlow 保留最新值，冷启动时 MyApp 首次
 * 收集也能拿到（不丢目标）；消费后置 null。
 */
@Singleton
class LedgerDeepLink @Inject constructor() {
    private val _target = MutableStateFlow<Long?>(null)
    val target: StateFlow<Long?> = _target.asStateFlow()

    fun openTransaction(id: Long) {
        _target.value = id
    }

    fun consume() {
        _target.value = null
    }
}
