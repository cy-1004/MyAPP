package com.myapp.feature.ledger.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * 编辑页保存成功事件（跨 NavBackStackEntry 传递）。
 *
 * 不用 previousBackStackEntry.savedStateHandle 传值：导航状态恢复 + 底部 tab
 * 的 saveState/restoreState 会让同一个 Route.Ledger 出现多个 entry 实例，
 * 列表页 VM 注入的 SavedStateHandle 与编辑页拿到的不是同一个对象，写了也观察不到
 * （真机实测：两个 handle 的 identity 不同，LaunchedEffect 永远收到 null）。
 * 改用进程内单例 Channel，与 entry 身份无关。
 */
@Singleton
class LedgerSaveEvents @Inject constructor() {
    private val _events = Channel<Long>(Channel.BUFFERED)
    val events: Flow<Long> = _events.receiveAsFlow()

    fun publish(amountCents: Long) {
        _events.trySend(amountCents)
    }
}
