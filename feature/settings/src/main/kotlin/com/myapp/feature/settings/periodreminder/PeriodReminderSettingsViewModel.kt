package com.myapp.feature.settings.periodreminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 经期提醒提前天数设置页 ViewModel（PRD 3.2，默认 2 天）。 */
@HiltViewModel
class PeriodReminderSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val leadDays: StateFlow<Int> = appPreferences.periodReminderLeadDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    fun setLeadDays(days: Int) {
        viewModelScope.launch { appPreferences.setPeriodReminderLeadDays(days) }
    }
}
