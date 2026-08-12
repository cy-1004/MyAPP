package com.myapp.feature.feed.articles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.data.RssArticleUi
import com.myapp.feature.feed.data.RssRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class RssArticleDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RssRepository,
) : ViewModel() {

    private val articleId: Long = savedStateHandle.toRoute<Route.RssArticleDetail>().articleId

    val article: StateFlow<RssArticleUi?> = repository.observeArticle(articleId).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        viewModelScope.launch { repository.setRead(articleId, true) }
    }

    fun setFavorite(isFavorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(articleId, isFavorite) }
    }

    fun saveAsNote(onSaved: () -> Unit) {
        viewModelScope.launch {
            if (repository.saveAsNote(articleId) != null) onSaved()
        }
    }
}
