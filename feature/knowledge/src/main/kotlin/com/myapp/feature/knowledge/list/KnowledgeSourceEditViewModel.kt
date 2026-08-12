package com.myapp.feature.knowledge.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.data.KnowledgeRepository
import com.myapp.feature.knowledge.data.KnowledgeSourceDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class KnowledgeSourceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: KnowledgeRepository,
) : ViewModel() {

    private val id: Long = savedStateHandle.toRoute<Route.KnowledgeSourceDetail>().id

    private val _draft = MutableStateFlow(KnowledgeSourceDraft(id = id))
    val draft: StateFlow<KnowledgeSourceDraft> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            _draft.value = repository.loadDraft(id)
        }
    }

    fun updateUrl(value: String) {
        _draft.value = _draft.value.copy(url = value)
    }

    fun updateTitle(value: String) {
        _draft.value = _draft.value.copy(title = value)
    }

    fun updateGroupName(value: String) {
        _draft.value = _draft.value.copy(groupName = value)
    }

    fun save(onSaved: () -> Unit) {
        val current = _draft.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(current)
            onSaved()
        }
    }
}
