package com.myapp.feature.ledger.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 预算预警通知 → 预算页的进程内深链（PRD 3.6.2）。
 *
 * 与 `KnowledgeShareTarget`/`KnowledgeDailyTarget`/`LedgerDeepLink` 同一套模式：
 * MainActivity 收到通知 PendingIntent 后写入这里，MyApp 收集后 navigate，消费后置 null。
 * 只是"打开预算页"这一个动作，不需要携带 id，用 Boolean 就够。
 */
@Singleton
class BudgetAlertTarget @Inject constructor() {
    private val _target = MutableStateFlow(false)
    val target: StateFlow<Boolean> = _target.asStateFlow()

    fun open() {
        _target.value = true
    }

    fun consume() {
        _target.value = false
    }
}
