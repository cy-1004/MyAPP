package com.myapp.feature.question.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.result.Result
import com.myapp.core.common.time.asRelativeText
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.CardErrorState
import com.myapp.core.designsystem.component.CardHeader
import com.myapp.core.designsystem.component.CardSkeleton
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardOrder
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.question.data.Question
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * 「随机一条待解决疑问」首页卡片（PRD 3.5）。
 *
 * 起提醒作用：每次进首页看到一条没解决的疑问，引导用户去解答。
 * 点卡片进编辑页；右上角 refresh icon 换一条（重新跑 ORDER BY RANDOM）。
 */
class QuestionHomeCard @Inject constructor() : HomeCard {

    override val id: String = "question"
    override val defaultOrder: Int = HomeCardOrder.QUESTION
    override val displayName: String = "待解疑问"

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        val viewModel: QuestionCardViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        AppCard(onClick = {
            // 点击卡片进列表页（若当前有随机疑问则进其编辑页）
            val current = (state as? Result.Success)?.data
            if (current != null) {
                onNavigate(Route.QuestionDetail(current.id))
            } else {
                onNavigate(Route.QuestionList)
            }
        }) {
            CardHeader(
                title = "待解疑问",
                trailing = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "换一条",
                            tint = MaterialTheme.appColors.textTertiary,
                            modifier = Modifier.padding(0.dp),
                        )
                    }
                },
            )

            when (val s = state) {
                is Result.Loading -> CardSkeleton()

                is Result.Error -> CardErrorState()

                is Result.Success -> {
                    val q = s.data
                    if (q == null) {
                        EmptyState(
                            text = "暂无待解决疑问",
                            actionLabel = "记一条",
                            onAction = { onNavigate(Route.QuestionDetail()) },
                        )
                    } else {
                        QuestionContent(q)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionContent(q: Question) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = q.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        if (q.tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                q.tags.take(MAX_TAGS).forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.appColors.textSecondary,
                        ),
                    )
                }
            }
        }

        Text(
            text = q.updatedAt.asRelativeText(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.textTertiary,
            modifier = Modifier.padding(start = 28.dp),
        )
    }
}

private const val MAX_TAGS = 3

/**
 * 把卡片注入全局卡片集合。
 * **这是唯一的注册动作**--首页会自动发现并渲染它。
 */
@Module
@InstallIn(SingletonComponent::class)
interface QuestionHomeCardModule {
    @Binds
    @IntoSet
    fun bindQuestionHomeCard(card: QuestionHomeCard): HomeCard
}
