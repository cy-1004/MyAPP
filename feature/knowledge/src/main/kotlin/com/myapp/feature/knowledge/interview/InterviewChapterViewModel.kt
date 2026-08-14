package com.myapp.feature.knowledge.interview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.database.dao.InterviewDao
import com.myapp.core.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 列表行：只要题干和一小段预览，正文全量留到详情页再渲染。 */
data class InterviewQuestionRow(
    val id: Long,
    val title: String,
    val preview: String,
)

data class InterviewChapterState(
    val title: String = "",
    val questions: List<InterviewQuestionRow> = emptyList(),
)

@HiltViewModel
class InterviewChapterViewModel @Inject constructor(
    private val dao: InterviewDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val chapterId: Long = savedStateHandle.toRoute<Route.InterviewChapter>().chapterId

    private val _state = MutableStateFlow(InterviewChapterState())
    val state: StateFlow<InterviewChapterState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val chapter = dao.getChapter(chapterId)
            dao.observeQuestionsInChapter(chapterId).collect { questions ->
                _state.value = InterviewChapterState(
                    title = chapter?.title.orEmpty(),
                    questions = questions.map {
                        InterviewQuestionRow(
                            id = it.id,
                            title = it.title,
                            preview = plainTextPreview(it.body, PREVIEW_LENGTH),
                        )
                    },
                )
            }
        }
    }

    private companion object {
        const val PREVIEW_LENGTH = 80
    }
}
