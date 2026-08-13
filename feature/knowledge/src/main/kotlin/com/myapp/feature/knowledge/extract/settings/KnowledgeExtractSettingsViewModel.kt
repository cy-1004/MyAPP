package com.myapp.feature.knowledge.extract.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.datastore.AppPreferences
import com.myapp.feature.knowledge.extract.DEFAULT_SELECTORS
import com.myapp.feature.knowledge.extract.ExtractSelectorStore
import com.myapp.feature.knowledge.extract.reorderSelectors
import com.myapp.feature.knowledge.notify.KnowledgeDailyReceiver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 知识库设置页 ViewModel（PRD 3.7/3.8）：正文提取选择器 + 每日知识点推送开关。
 *
 * 每次改动都整份写回 [ExtractSelectorStore]（选择器数量个位数，没必要做增量 diff，
 * 与 `HomeCardOrderViewModel`/分类管理同一套「整表重写」取舍）。
 */
@HiltViewModel
class KnowledgeExtractSettingsViewModel @Inject constructor(
    private val store: ExtractSelectorStore,
    private val preferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val selectors: StateFlow<List<String>> = store.config
        .map { it.selectors }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_SELECTORS)

    val dailyPushEnabled: StateFlow<Boolean> = preferences.knowledgeDailyPushEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 开关本身就是"要不要排闹钟"的唯一开关来源，这里直接调度，不依赖 BootCompletedReceiver 补一次。 */
    fun setDailyPushEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.setKnowledgeDailyPushEnabled(enabled) }
        if (enabled) {
            KnowledgeDailyReceiver.scheduleNext(context)
        } else {
            KnowledgeDailyReceiver.cancel(context)
        }
    }

    fun add(selector: String) {
        val trimmed = selector.trim()
        if (trimmed.isEmpty() || trimmed in selectors.value) return
        viewModelScope.launch { store.setSelectors(selectors.value + trimmed) }
    }

    fun remove(selector: String) {
        viewModelScope.launch { store.setSelectors(selectors.value - selector) }
    }

    fun move(selector: String, delta: Int) {
        viewModelScope.launch {
            val moved = reorderSelectors(selectors.value, selector, delta) ?: return@launch
            store.setSelectors(moved)
        }
    }

    fun resetToDefault() {
        viewModelScope.launch { store.setSelectors(DEFAULT_SELECTORS) }
    }
}
