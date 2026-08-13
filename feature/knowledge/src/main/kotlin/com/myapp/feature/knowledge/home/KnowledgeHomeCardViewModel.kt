package com.myapp.feature.knowledge.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.contract.KnowledgeItem
import com.myapp.feature.knowledge.data.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 今日知识点首页卡片（PRD 3.8）。
 *
 * [pick] 是手动维护的 [MutableStateFlow] 而不是直接 `stateIn` 一个 repository Flow：
 * [KnowledgeRepository.pickDailyKnowledge] 底下没有单一的 DAO Flow 可订阅（它综合了
 * 知识池、复习状态、笔记降级三处数据），"已掌握/再看看"之后要立刻换下一条，
 * 用手动 reload 比拼一条跨表 Flow 简单直接。
 */
@HiltViewModel
class KnowledgeHomeCardViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
) : ViewModel() {

    private val _pick = MutableStateFlow<KnowledgeItem?>(null)
    val pick: StateFlow<KnowledgeItem?> = _pick.asStateFlow()

    init {
        reload()
    }

    fun mastered(item: KnowledgeItem) = respond(item, mastered = true)

    fun snoozed(item: KnowledgeItem) = respond(item, mastered = false)

    /**
     * 卡片重新可见时调一次（见 [com.myapp.feature.knowledge.home.KnowledgeHomeCard] 里的
     * `LifecycleResumeEffect`）：用户可能刚从知识源列表页把某条加进了知识池再返回首页，
     * ViewModel 本身跟首页这个 NavBackStackEntry 同生命周期，不会因为切一趟列表页就重建，
     * 不主动刷新的话卡片会一直停在旧的那条。
     */
    fun refresh() = reload()

    /** 笔记降级（[KnowledgeItem.isNoteFallback]）不落库：笔记不是知识池成员，没有复习进度可言。 */
    private fun respond(item: KnowledgeItem, mastered: Boolean) {
        viewModelScope.launch {
            if (!item.isNoteFallback) repository.recordFeedback(item.sourceId, mastered)
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch { _pick.value = repository.pickDailyKnowledge() }
    }
}
