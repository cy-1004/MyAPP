package com.myapp.feature.settings.periodreminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.contract.PeriodReminderRefresher
import com.myapp.core.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 经期提醒设置页 ViewModel（PRD 3.2）：提前天数（默认 2 天）+ 经期中每日关怀提醒（默认开）。
 *
 * **每次改完设置都要调 [PeriodReminderRefresher.refresh]**：提醒的触发时间是写设置时
 * 算好、注册进 AlarmManager 的，光写 DataStore 动不了已经注册的闹钟。
 * 这里不直接依赖 :feature:period（feature 之间不许互相依赖），走 :core:common 的契约。
 */
@HiltViewModel
class PeriodReminderSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val reminderRefresher: PeriodReminderRefresher,
) : ViewModel() {

    val leadDays: StateFlow<Int> = appPreferences.periodReminderLeadDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    val careReminderEnabled: StateFlow<Boolean> = appPreferences.periodCareReminderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setLeadDays(days: Int) {
        viewModelScope.launch {
            appPreferences.setPeriodReminderLeadDays(days)
            reminderRefresher.refresh()
        }
    }

    fun setCareReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferences.setPeriodCareReminderEnabled(enabled)
            reminderRefresher.refresh()
        }
    }
}
