package com.myapp.feature.ledger.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.CardHeader
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.home.BaseHomeCard
import com.myapp.core.ui.home.HomeCardOrder
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.data.BudgetCycle
import com.myapp.feature.ledger.ui.cycleRangeText
import com.myapp.feature.ledger.ui.yuanWithSymbol
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

/**
 * 「今日支出 + 本期预算」首页卡片（PRD 3.6.4 / 4.7.2）。
 *
 * 插件机制参考 TodoHomeCard：经 @Binds @IntoSet 自动注入 Set<HomeCard>，
 * 首页代码一行不改。
 *
 * 卡片三态：
 *   - 未设预算：显示「点击设置本期预算」按钮
 *   - 今日无支出：显示「今天还没记账」
 *   - 有支出：显示今日金额 + 本期进度条
 */
class LedgerHomeCard @Inject constructor() : BaseHomeCard(
    id = "ledger",
    defaultOrder = HomeCardOrder.LEDGER,
) {

    @Composable
    override fun Content(onNavigate: (Route) -> Unit) {
        val viewModel: LedgerCardViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()

        AppCard(onClick = { onNavigate(Route.Ledger) }) {
            CardHeader(title = "今日支出")

            if (state.budget == null) {
                EmptyState(
                    text = if (state.todaySpendingCents == 0L) "今天还没记账"
                           else "今日已花 ${state.todaySpendingCents.yuanWithSymbol()}",
                    actionLabel = "设置预算",
                    onAction = { onNavigate(Route.Ledger) },
                )
            } else {
                BudgetContent(state = state)
            }
        }
    }
}

@Composable
private fun BudgetContent(state: LedgerCardState) {
    val budget = state.budget!!
    val cycle = BudgetCycle.currentCycleRange(budget.cycleStartDay)
    val remaining = (budget.totalAmountCents - state.cycleSpentCents).coerceAtLeast(0L)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        // 今日金额 + 本期剩余
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = state.todaySpendingCents.yuanWithSymbol(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "本期剩余 ${remaining.yuanWithSymbol()}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
            if (state.pendingCount > 0) {
                Text(
                    text = "${state.pendingCount} 笔待确认",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.warning,
                )
            }
        }

        // 进度条：颜色按消耗比例变档（PRD 3.6.4）
        val progressColor = when {
            state.progress >= 0.9f -> MaterialTheme.appColors.danger
            state.progress >= 0.7f -> MaterialTheme.appColors.warning
            else -> MaterialTheme.colorScheme.primary
        }
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(MaterialTheme.shapes.extraSmall),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )

        // 周期区间文本
        Text(
            text = cycleRangeText(cycle.first, cycle.last + 1),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.textTertiary,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
interface LedgerHomeCardModule {
    @Binds
    @IntoSet
    fun bindLedgerHomeCard(card: LedgerHomeCard): com.myapp.core.ui.home.HomeCard
}
