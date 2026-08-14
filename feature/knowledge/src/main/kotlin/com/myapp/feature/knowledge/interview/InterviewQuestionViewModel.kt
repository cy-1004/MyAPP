package com.myapp.feature.knowledge.interview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InterviewQuestionState(
    val question: InterviewQuestionUi? = null,
    /** 加载完成才显示「题目不见了」，否则会先闪一下空态。 */
    val loaded: Boolean = false,
)

@HiltViewModel
class InterviewQuestionViewModel @Inject constructor(
    private val repository: InterviewRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val questionId: Long = savedStateHandle.toRoute<Route.InterviewQuestion>().questionId

    private val _state = MutableStateFlow(InterviewQuestionState())
    val state: StateFlow<InterviewQuestionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = InterviewQuestionState(
                question = repository.getQuestion(questionId),
                loaded = true,
            )
        }
    }

    fun mastered() {
        viewModelScope.launch { repository.recordFeedback(questionId, mastered = true) }
    }

    fun snoozed() {
        viewModelScope.launch { repository.recordFeedback(questionId, mastered = false) }
    }
}
