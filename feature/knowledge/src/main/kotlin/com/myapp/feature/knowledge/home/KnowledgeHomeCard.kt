package com.myapp.feature.knowledge.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
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
import com.myapp.core.ui.home.BaseHomeCard
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardOrder
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.data.KnowledgeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 置顶知识源快捷入口首页卡片（PRD 3.7：「首页可配快捷入口」）。
 *
 * 没有置顶知识源时不显示（[isEnabled]），避免首页出现一张空卡片。
 */
class KnowledgeHomeCard @Inject constructor(
    private val repository: KnowledgeRepository,
) : BaseHomeCard(
    id = "knowledge",
    defaultOrder = HomeCardOrder.KNOWLEDGE,
    displayName = "知识库",
) {

    override fun isEnabled(): Flow<Boolean> = repository.observePinned().map { it.isNotEmpty() }

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        val viewModel: KnowledgeHomeCardViewModel = hiltViewModel()
        val pinned by viewModel.pinned.collectAsStateWithLifecycle()

        AppCard(onClick = { onNavigate(Route.KnowledgeSources) }) {
            CardHeader(title = "知识库")

            if (pinned.isEmpty()) {
                EmptyState(
                    text = "还没有置顶的知识源",
                    actionLabel = "去添加",
                    onAction = { onNavigate(Route.KnowledgeSourceDetail()) },
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    pinned.forEach { source ->
                        AssistChip(
                            onClick = { onNavigate(Route.KnowledgeReader(source.id)) },
                            label = {
                                Text(
                                    text = source.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface KnowledgeHomeCardModule {
    @Binds
    @IntoSet
    fun bindKnowledgeHomeCard(card: KnowledgeHomeCard): HomeCard
}
