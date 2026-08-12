package com.myapp.feature.feed.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.data.RssRepository
import com.myapp.feature.feed.data.RssSourceDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class RssSourceEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository,
) : ViewModel() {

    private val id: Long = savedStateHandle.toRoute<Route.RssSourceDetail>().id

    private val _draft = MutableStateFlow(RssSourceDraft(id = id))
    val draft: StateFlow<RssSourceDraft> = _draft.asStateFlow()

    init {
        viewModelScope.launch { _draft.value = repository.loadDraft(id) }
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
