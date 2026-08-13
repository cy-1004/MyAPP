package com.myapp.feature.knowledge.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 点开哪个东西——知识源阅读页，还是笔记详情（PRD 3.8 降级到笔记时）。 */
sealed interface KnowledgeDailyDestination {
    data class Reader(val sourceId: Long) : KnowledgeDailyDestination
    data class Note(val noteId: Long) : KnowledgeDailyDestination
}

/**
 * 每日知识点通知 → 阅读页/笔记详情的进程内深链（PRD 3.8）。
 *
 * 与 [KnowledgeShareTarget]/`LedgerDeepLink`/`WidgetNavTarget` 同一套模式：
 * MainActivity 收到通知 PendingIntent 后写入这里，MyApp 收集后 navigate，消费后置 null。
 */
@Singleton
class KnowledgeDailyTarget @Inject constructor() {
    private val _target = MutableStateFlow<KnowledgeDailyDestination?>(null)
    val target: StateFlow<KnowledgeDailyDestination?> = _target.asStateFlow()

    fun open(destination: KnowledgeDailyDestination) {
        _target.value = destination
    }

    fun consume() {
        _target.value = null
    }
}
