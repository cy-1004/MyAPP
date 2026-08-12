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

    private val route = savedStateHandle.toRoute<Route.KnowledgeSourceDetail>()
    private val id: Long = route.id

    private val _draft = MutableStateFlow(KnowledgeSourceDraft(id = id))
    val draft: StateFlow<KnowledgeSourceDraft> = _draft.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = repository.loadDraft(id)
            val sharedUrl = route.sharedUrl
            // 分享菜单带来的链接只在新建时预填，不覆盖已有知识源的 URL。
            _draft.value = if (loaded.isNew && !sharedUrl.isNullOrBlank()) {
                loaded.copy(url = sharedUrl)
            } else {
                loaded
            }
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
