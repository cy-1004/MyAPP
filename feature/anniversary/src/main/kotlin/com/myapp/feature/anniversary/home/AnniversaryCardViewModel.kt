package com.myapp.feature.anniversary.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.anniversary.data.Anniversary
import com.myapp.feature.anniversary.data.AnniversaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AnniversaryCardViewModel @Inject constructor(
    repository: AnniversaryRepository,
) : ViewModel() {

    /** 置顶的一条 + 下一个即将到来的，两条正好占满一张卡的高度。 */
    val state: StateFlow<Result<List<Anniversary>>> = repository.observeHighlighted(limit = 2)
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )
}
