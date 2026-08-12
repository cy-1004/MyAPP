package com.myapp.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.datastore.AppPreferences
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardConfig
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
    private val appPreferences: AppPreferences,
) : ViewModel() {

    /**
     * 按用户配置（[AppPreferences.homeCardConfig]）排序，并过滤掉用户禁用项
     * 与卡片自身 [HomeCard.isEnabled] 判定为不可见的项。
     */
    val visibleCards: StateFlow<List<HomeCard>> = run {
        val list = cards.toList()
        val enabledFlows = list.map { card -> card.isEnabled() }
        val selfEnabled = if (enabledFlows.isEmpty()) flowOf(emptyList()) else combine(enabledFlows) { it.toList() }

        combine(appPreferences.homeCardConfig, selfEnabled) { configRaw, selfFlags ->
            HomeCardConfig.applyOrder(list, configRaw).filter { card ->
                val index = list.indexOf(card)
                selfFlags[index] && HomeCardConfig.isEnabled(card.id, configRaw)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = cards.sortedBy { it.defaultOrder },
    )
}
