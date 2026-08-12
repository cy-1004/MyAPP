package com.myapp.feature.knowledge.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 系统分享菜单「分享到 MyAPP」→ 知识源新建页的进程内深链（PRD 3.7）。
 *
 * 浏览器/飞书的分享 Intent 只负责把 MainActivity 拉起来并带上链接文本，
 * 导航本身由 Compose 层完成：MainActivity 把 URL 写入这里，MyApp 收集后
 * navigate 到 KnowledgeSourceDetail(sharedUrl = url)。与 LedgerDeepLink/WidgetNavTarget
 * 同一套模式：StateFlow 保留最新值，冷启动时 MyApp 首次收集也能拿到；消费后置 null。
 */
@Singleton
class KnowledgeShareTarget @Inject constructor() {
    private val _target = MutableStateFlow<String?>(null)
    val target: StateFlow<String?> = _target.asStateFlow()

    fun share(url: String) {
        _target.value = url
    }

    fun consume() {
        _target.value = null
    }
}
