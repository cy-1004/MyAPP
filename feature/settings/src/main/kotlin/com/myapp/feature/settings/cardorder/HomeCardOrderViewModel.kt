package com.myapp.feature.settings.cardorder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.datastore.AppPreferences
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardConfig
import com.myapp.core.ui.home.HomeCardConfigEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeCardOrderItem(
    val id: String,
    val displayName: String,
    val enabled: Boolean,
)

/**
 * 首页卡片排序设置页 ViewModel。
 *
 * 上移/下移都是拿当前展示顺序做一次数组搬移，再把整份顺序 + 显隐状态原样写回，
 * 不做增量 diff——卡片量级小（个位数），没必要为此维护更复杂的结构。
 */
@HiltViewModel
class HomeCardOrderViewModel @Inject constructor(
    private val cards: Set<@JvmSuppressWildcards HomeCard>,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val cardList = cards.toList()

    val items: StateFlow<List<HomeCardOrderItem>> = appPreferences.homeCardConfig.map { raw ->
        HomeCardConfig.applyOrder(cardList, raw).map { card ->
            HomeCardOrderItem(
                id = card.id,
                displayName = card.displayName,
                enabled = HomeCardConfig.isEnabled(card.id, raw),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun moveUp(id: String) = reorder(id, delta = -1)

    fun moveDown(id: String) = reorder(id, delta = 1)

    fun setEnabled(id: String, enabled: Boolean) {
        persist(items.value.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    private fun reorder(id: String, delta: Int) {
        val current = items.value
        val from = current.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in current.indices) return
        persist(current.toMutableList().apply { add(to, removeAt(from)) })
    }

    private fun persist(items: List<HomeCardOrderItem>) {
        viewModelScope.launch {
            val entries = items.map { HomeCardConfigEntry(id = it.id, enabled = it.enabled) }
            appPreferences.setHomeCardConfig(HomeCardConfig.encode(entries))
        }
    }
}
