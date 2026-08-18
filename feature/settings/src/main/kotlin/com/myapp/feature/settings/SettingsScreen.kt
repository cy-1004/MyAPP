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
import androidx.compose.material3.Switch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route

/**
 * 设置首页（PRD 9.3 / 3.12）：保活自检入口 + 自动记账开关 + 外观 + 关于。
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
    val listenerConnected = viewModel.notificationListenerConnected
    val noteQuickEntry by viewModel.noteQuickEntryEnabled.collectAsStateWithLifecycle(initialValue = false)

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
                // 底栏是 MyApp 里浮在 Box 上的覆盖层，不是本 Scaffold 的 bottomBar，
                // 所以 innerPadding 里**没有**它的高度——只用 innerPadding 的话列表底部会被
                // 底栏挡住（本页条目变多后「关于」就整个看不见了）。
                // LocalBottomBarHeight 是底栏实测高度且已含导航栏 inset，不要再叠加
                // innerPadding.calculateBottomPadding()，否则底部 inset 会算两遍。
                bottom = LocalBottomBarHeight.current + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "auto_ledger") {
                SettingsItem(
                    title = "自动记账",
                    // 「已授权」不等于「能收到通知」：覆盖安装后绑定会断，
                    // 只显示已开启会让人以为在正常工作（PRD 9.3）
                    description = when {
                        !listenerOn -> "支付通知自动记一笔，需先开启通知使用权"
                        listenerConnected -> "已开启：支付通知自动记一笔，点击查看或关闭"
                        else -> "⚠ 已授权但服务未连接，现在收不到支付通知；点此关掉再打开一次"
                    },
                    enabled = true,
                    onClick = { viewModel.openNotificationListenerSettings() },
                )
            }
            item(key = "rule_mgmt") {
                SettingsItem(
                    title = "规则管理",
                    description = "自定义支付通知解析规则，让自动记账越用越准",
                    enabled = true,
                    onClick = { onNavigate(Route.RuleList) },
                )
            }
            item(key = "category_mgmt") {
                SettingsItem(
                    title = "分类管理",
                    description = "增删改记账分类、调整顺序、停用不用的分类",
                    enabled = true,
                    onClick = { onNavigate(Route.CategoryList) },
                )
            }
            item(key = "home_card_order") {
                SettingsItem(
                    title = "首页卡片排序",
                    description = "自定义首页各卡片的顺序与显隐",
                    enabled = true,
                    onClick = { onNavigate(Route.HomeCardOrder) },
                )
            }
            item(key = "period_reminder") {
                SettingsItem(
                    title = "经期提醒",
                    description = "开始前几天提醒，以及经期中每天的关怀提醒",
                    enabled = true,
                    onClick = { onNavigate(Route.PeriodReminderSettings) },
                )
            }
            item(key = "note_quick_entry") {
                SettingsSwitchItem(
                    title = "笔记快捷入口",
                    description = "在通知栏常驻一条入口，点一下直接新建笔记",
                    checked = noteQuickEntry,
                    onCheckedChange = viewModel::setNoteQuickEntryEnabled,
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
            item(key = "local_backup") {
                SettingsItem(
                    title = "本地备份",
                    description = "导出含笔记图片的加密备份文件，或从备份文件恢复",
                    enabled = true,
                    onClick = { onNavigate(Route.LocalBackup) },
                )
            }
            item(key = "cloud_backup") {
                SettingsItem(
                    title = "云备份",
                    description = "每天一次加密上传，换机时可从云端恢复",
                    enabled = true,
                    onClick = { onNavigate(Route.CloudBackup) },
                )
            }
            item(key = "ai") {
                SettingsItem(
                    title = "AI 分析",
                    description = "接入 DeepSeek 解读经期记录。默认关闭，开启前会说明发送什么",
                    enabled = true,
                    onClick = { onNavigate(Route.AiSettings) },
                )
            }
            item(key = "appearance") {
                SettingsItem(
                    title = "外观",
                    description = "主题模式、动态取色、动效强度",
                    enabled = true,
                    onClick = { onNavigate(Route.Appearance) },
                )
            }
            item(key = "about") {
                SettingsItem(
                    title = "关于",
                    description = "版本信息",
                    enabled = true,
                    onClick = { onNavigate(Route.About) },
                )
            }
        }
    }
}

/**
 * 带开关的设置项。本页其余条目都是「点进去」，这条是就地切换--
 * 为一个布尔开关单开一个页面不值当，但也不能把它做成看起来能点进去的样子，
 * 所以整卡可点（点哪都是切换）+ 右侧 Switch 明示状态。
 */
@Composable
private fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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
