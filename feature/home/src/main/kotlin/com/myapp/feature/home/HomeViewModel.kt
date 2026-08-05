package com.myapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.ui.home.HomeCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 首页 ViewModel。
 *
 * 注意它注入的是 `Set<HomeCard>` 而不是任何具体业务类型——
 * 首页对「待办」「记账」「经期」这些概念一无所知（PRD 4.7.2）。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cards: Set<@JvmSuppressWildcards HomeCard>,
) : ViewModel() {

    /**
     * 按用户配置排序并过滤掉禁用项。
     *
     * TODO 接入 AppPreferences.homeCardConfig，支持用户自定义顺序与显隐；
     *      当前先按 defaultOrder 排序。
     */
    val visibleCards: StateFlow<List<HomeCard>> = run {
        val sorted = cards.sortedBy { it.defaultOrder }
        val enabledFlows = sorted.map { card -> card.isEnabled() }

        if (enabledFlows.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(enabledFlows) { flags ->
                sorted.filterIndexed { index, _ -> flags[index] }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = cards.sortedBy { it.defaultOrder },
    )
}
