package com.myapp.feature.note.notify

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 常驻快捷入口点击 → 进程内导航深链（与 `WidgetNavTarget`/`KnowledgeDailyTarget` 同一套模式）。
 *
 * 通知的 PendingIntent 只负责把 MainActivity 拉起来，具体导航由 Compose 层完成：
 * MainActivity 认出 extra 后调 [open]，MyApp 收集到就 navigate 到新建笔记页。
 * StateFlow 保留最新值，冷启动时 Compose 还没起来也不会丢掉这次点击。
 */
@Singleton
class NoteQuickEntryTarget @Inject constructor() {
    private val _target = MutableStateFlow(false)
    val target: StateFlow<Boolean> = _target.asStateFlow()

    fun open() {
        _target.value = true
    }

    fun consume() {
        _target.value = false
    }
}
