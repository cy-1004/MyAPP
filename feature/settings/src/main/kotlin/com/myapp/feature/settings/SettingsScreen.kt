package com.myapp.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route

/**
 * 设置首页（PRD 9.3）：保活自检入口 + 自动记账开关。
 *
 * 其余设置项（外观/关于）暂为占位，后续功能落地时填充。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // 用户从系统设置返回后需要刷新状态；自增计数触发重组（VM 属性是冷读 getter）
    var refreshTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshTick++
        onPauseOrDispose { }
    }

    val listenerOn = viewModel.notificationListenerEnabled

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = innerPadding.calculateTopPadding() + Spacing.md,
                bottom = innerPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "auto_ledger") {
                SettingsItem(
                    title = "自动记账",
                    description = if (listenerOn) {
                        "已开启：支付通知自动记一笔，点击查看或关闭"
                    } else {
                        "支付通知自动记一笔，需先开启通知使用权"
                    },
                    enabled = true,
                    onClick = { viewModel.openNotificationListenerSettings() },
                )
            }
            item(key = "keepalive") {
                SettingsItem(
                    title = "保活自检",
                    description = "检查后台限制设置，确保提醒与记账能稳定运行",
                    enabled = true,
                    onClick = { onNavigate(Route.KeepAliveCheck) },
                )
            }
            item(key = "appearance") {
                SettingsItem(
                    title = "外观",
                    description = "主题、动效级别（即将推出）",
                    enabled = false,
                    onClick = {},
                )
            }
            item(key = "about") {
                SettingsItem(
                    title = "关于",
                    description = "版本信息",
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = if (enabled) onClick else ({ /* disabled */ }),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.appColors.textTertiary,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) MaterialTheme.appColors.textSecondary
                        else MaterialTheme.appColors.textTertiary,
                    )
                }
                if (enabled) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.appColors.textTertiary,
                    )
                }
            }
        }
    }
}
