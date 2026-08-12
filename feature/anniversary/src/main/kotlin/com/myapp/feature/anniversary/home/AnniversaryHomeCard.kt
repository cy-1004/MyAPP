package com.myapp.feature.anniversary.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.result.Result
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
import com.myapp.feature.anniversary.data.Anniversary
import com.myapp.feature.anniversary.ui.CountdownText
import com.myapp.feature.anniversary.ui.subtitle
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * 「纪念日」首页卡片（PRD 3.11 第一张卡）。
 *
 * 与待办卡片一样，注册动作只有下面的 @Binds @IntoSet —— :feature:home 一行都不用改。
 */
class AnniversaryHomeCard @Inject constructor() : HomeCard {

    override val id: String = "anniversary"
    override val defaultOrder: Int = HomeCardOrder.GREETING
    override val displayName: String = "纪念日"

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        val viewModel: AnniversaryCardViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        AppCard(onClick = { onNavigate(Route.AnniversaryList) }) {
            CardHeader(title = "纪念日")

            when (val s = state) {
                is Result.Loading -> CardSkeleton()

                is Result.Error -> CardErrorState()

                is Result.Success -> if (s.data.isEmpty()) {
                    EmptyState(
                        text = "记下一个值得数着过的日子",
                        actionLabel = "加一个",
                        onAction = { onNavigate(Route.AnniversaryDetail()) },
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        s.data.forEach { item -> AnniversaryRow(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnniversaryRow(item: Anniversary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CountdownText(item = item)
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface AnniversaryHomeCardModule {
    @Binds
    @IntoSet
    fun bindAnniversaryHomeCard(card: AnniversaryHomeCard): HomeCard
}
