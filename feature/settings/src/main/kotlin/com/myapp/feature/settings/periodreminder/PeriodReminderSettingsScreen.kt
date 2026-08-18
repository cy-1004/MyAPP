package com.myapp.feature.settings.periodreminder

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

private val LEAD_DAYS_OPTIONS = listOf(1, 2, 3, 5, 7)

/**
 * 经期提醒设置页（PRD 3.2）：提前天数 + 经期中每日关怀提醒。
 *
 * 提前天数选项沿用纪念日编辑页的 ChipRow 交互，量级小不需要滑杆/输入框。
 */
@Composable
fun PeriodReminderSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeriodReminderSettingsViewModel = hiltViewModel(),
) {
    val leadDays by viewModel.leadDays.collectAsStateWithLifecycle()
    val careEnabled by viewModel.careReminderEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("经期提醒") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = "预计经期开始日前几天提醒你",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )

            AppCard {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        LEAD_DAYS_OPTIONS.forEach { days ->
                            FilterChip(
                                selected = days == leadDays,
                                onClick = { viewModel.setLeadDays(days) },
                                label = { Text("提前 $days 天", style = MaterialTheme.typography.labelLarge) },
                                shape = MaterialTheme.shapes.small,
                            )
                        }
                    }
                }
            }

            Text(
                text = "经期中每日关怀提醒",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
                modifier = Modifier.padding(top = Spacing.md),
            )

            AppCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "开启", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            // 把「推几天、几点推」如实写出来：一条会自己冒出来的通知，
                            // 用户有权在开之前就知道它什么时候来。
                            // 天数/时点的真身是 PeriodRepository 里的 CARE_REMINDER_DAYS /
                            // CARE_REMINDER_HOUR（private，:feature:settings 够不着），改那边记得同步这句
                            text = "经期前 3 天，每天 19:00 提醒你照顾她",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.appColors.textSecondary,
                        )
                    }
                    Switch(
                        checked = careEnabled,
                        onCheckedChange = viewModel::setCareReminderEnabled,
                    )
                }
            }
        }
    }
}
