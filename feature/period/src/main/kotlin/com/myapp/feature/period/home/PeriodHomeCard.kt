package com.myapp.feature.period.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.result.Result
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.CardErrorState
import com.myapp.core.designsystem.component.CardHeader
import com.myapp.core.designsystem.component.CardSkeleton
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.TabularNumbers
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.home.HomeCard
import com.myapp.core.ui.home.HomeCardOrder
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.period.data.PeriodStatus
import com.myapp.feature.period.ui.explanation
import com.myapp.feature.period.ui.headline
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * 「经期」首页卡片（PRD 3.11）。
 *
 * 卡片自带「今天开始了」按钮：从打开 App 到记完一次只要两下，
 * 这是 PRD 3.2「≤ 3 次点击」验收标准的兑现方式。
 */
class PeriodHomeCard @Inject constructor() : HomeCard {

    override val id: String = "period"
    override val defaultOrder: Int = HomeCardOrder.PERIOD

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        val viewModel: PeriodCardViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        AppCard(onClick = { onNavigate(Route.PeriodCalendar) }) {
            CardHeader(title = "经期")

            when (val s = state) {
                is Result.Loading -> CardSkeleton()

                is Result.Error -> CardErrorState()

                is Result.Success -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = s.data.status.headline(),
                            style = MaterialTheme.typography.bodyLarge.merge(TabularNumbers),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = s.data.explanation(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.textSecondary,
                        )
                    }

                    val ongoing = s.data.status is PeriodStatus.Ongoing
                    TextButton(
                        onClick = { if (ongoing) viewModel.recordEnd() else viewModel.recordStart() },
                    ) {
                        Text(
                            text = if (ongoing) "结束了" else "开始了",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface PeriodHomeCardModule {
    @Binds
    @IntoSet
    fun bindPeriodHomeCard(card: PeriodHomeCard): HomeCard
}
