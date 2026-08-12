package com.myapp.feature.knowledge.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.knowledge.data.KnowledgeRepository
import com.myapp.feature.knowledge.data.KnowledgeSourceUi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class KnowledgeHomeCardViewModel @Inject constructor(
    repository: KnowledgeRepository,
) : ViewModel() {

    val pinned: StateFlow<List<KnowledgeSourceUi>> = repository.observePinned()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
