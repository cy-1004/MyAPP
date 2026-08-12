package com.myapp.feature.feed.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.feed.data.RssArticleUi
import com.myapp.feature.feed.data.RssRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class RssHomeCardViewModel @Inject constructor(
    repository: RssRepository,
) : ViewModel() {

    val latestUnread: StateFlow<List<RssArticleUi>> = repository.observeLatestUnread()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
