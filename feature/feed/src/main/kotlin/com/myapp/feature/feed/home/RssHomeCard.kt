package com.myapp.feature.feed.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.CardHeader
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.home.BaseHomeCard
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardOrder
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.feed.data.RssRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 首页「最新 3 条未读」资讯卡片（PRD 3.9）。没有未读资讯时不显示。 */
class RssHomeCard @Inject constructor(
    private val repository: RssRepository,
) : BaseHomeCard(
    id = "rss_feed",
    defaultOrder = HomeCardOrder.FEED,
    displayName = "资讯",
) {

    override fun isEnabled(): Flow<Boolean> = repository.observeLatestUnread().map { it.isNotEmpty() }

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        val viewModel: RssHomeCardViewModel = hiltViewModel()
        val articles by viewModel.latestUnread.collectAsStateWithLifecycle()

        AppCard(onClick = { onNavigate(Route.RssArticles) }) {
            CardHeader(title = "资讯")

            if (articles.isEmpty()) {
                EmptyState(text = "暂无未读资讯", actionLabel = "去订阅", onAction = { onNavigate(Route.RssSources) })
            } else {
                Column {
                    articles.forEach { article ->
                        Text(
                            text = "· ${article.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.appColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface RssHomeCardModule {
    @Binds
    @IntoSet
    fun bindRssHomeCard(card: RssHomeCard): HomeCard
}
