package com.myapp.feature.question.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.result.Result
import com.myapp.core.common.result.asResult
import com.myapp.feature.question.data.Question
import com.myapp.feature.question.data.QuestionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class QuestionCardViewModel @Inject constructor(
    private val repository: QuestionRepository,
) : ViewModel() {

    private val _refresh = MutableStateFlow(0)

    /**
     * Refresh trigger：[QuestionRepository.observeRandomPending] 用 `ORDER BY RANDOM() LIMIT 1`，
     * 只在表变更时重新 emit--同一条会一直停在那里。
     * 用 flatMapLatest + 自增计数器，点 refresh 时 _refresh.value++ 触发重新订阅，
     * 才能拿到一条新的随机疑问。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<Result<Question?>> = _refresh
        .flatMapLatest { repository.observeRandomPending() }
        .asResult()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Result.Loading,
        )

    fun refresh() {
        _refresh.value++
    }
}
