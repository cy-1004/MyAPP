package com.myapp.feature.knowledge.interview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 按文档分组后的章节，列表页直接照着渲染。 */
data class InterviewDocGroup(
    val docKey: String,
    val docName: String,
    val chapters: List<InterviewChapterUi>,
) {
    val questionCount: Int get() = chapters.sumOf { it.questionCount }
    val allInPool: Boolean get() = chapters.all { it.inPool }
}

data class InterviewChapterListState(
    val groups: List<InterviewDocGroup> = emptyList(),
    val loaded: Boolean = false,
) {
    val totalQuestions: Int get() = groups.sumOf { it.questionCount }
    val pooledQuestions: Int
        get() = groups.sumOf { group -> group.chapters.filter { it.inPool }.sumOf { it.questionCount } }
}

@HiltViewModel
class InterviewChapterListViewModel @Inject constructor(
    private val repository: InterviewRepository,
) : ViewModel() {

    val state: StateFlow<InterviewChapterListState> = repository.observeChapters()
        .map { chapters ->
            InterviewChapterListState(
                // DAO 已经按 doc_key、sort_order 排好，这里保持顺序分组即可
                groups = chapters
                    .groupBy { it.docKey }
                    .map { (docKey, list) ->
                        InterviewDocGroup(
                            docKey = docKey,
                            docName = list.first().docName,
                            chapters = list,
                        )
                    },
                loaded = true,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InterviewChapterListState(),
        )

    fun setChapterInPool(chapterId: Long, inPool: Boolean) {
        viewModelScope.launch { repository.setChapterInPool(chapterId, inPool) }
    }

    fun setDocInPool(docKey: String, inPool: Boolean) {
        viewModelScope.launch { repository.setDocInPool(docKey, inPool) }
    }
}
