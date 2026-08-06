package com.myapp.feature.period.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.core.common.time.AppTime
import com.myapp.feature.period.data.PeriodRepository
import com.myapp.feature.period.data.PeriodState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PeriodCardViewModel @Inject constructor(
    private val repository: PeriodRepository,
) : ViewModel() {

    val state: StateFlow<Result<PeriodState>> = repository.observeState()
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

    /** 首页直接记录，不用先进详情页——这是「≤ 3 次点击」的关键一步（PRD 3.2 验收标准）。 */
    fun recordStart() {
        viewModelScope.launch { repository.recordStart(AppTime.today()) }
    }

    fun recordEnd() {
        viewModelScope.launch { repository.recordEnd(AppTime.today()) }
    }
}
