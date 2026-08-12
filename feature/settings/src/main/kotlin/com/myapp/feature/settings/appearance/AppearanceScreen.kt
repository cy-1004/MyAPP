package com.myapp.feature.settings.appearance

import android.os.Build
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

private val THEME_MODE_OPTIONS = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
private val MOTION_LEVEL_OPTIONS = listOf("full" to "完整", "reduced" to "精简", "none" to "关闭")

/**
 * 外观设置页（PRD 3.12）：主题模式 / 动态取色 / 动效强度。
 *
 * 交互沿用 [com.myapp.feature.settings.periodreminder.PeriodReminderSettingsScreen] 的 ChipRow 模式，
 * 量级小不需要下拉菜单。
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val motionLevel by viewModel.motionLevel.collectAsStateWithLifecycle()
    val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsStateWithLifecycle()
    val motionLocked = viewModel.motionLevelLockedByAccessibility
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("外观") },
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
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SettingSection(title = "主题模式") {
                ChipRow(
                    options = THEME_MODE_OPTIONS,
                    selected = themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }

            SettingSwitchSection(
                title = "动态取色",
                description = if (dynamicColorSupported) {
                    "开启后跟随系统壁纸配色；关闭时保持 Claude 风格暖色调（默认）"
                } else {
                    "系统版本过低，暂不支持"
                },
                checked = dynamicColorEnabled && dynamicColorSupported,
                onCheckedChange = viewModel::setDynamicColorEnabled,
                enabled = dynamicColorSupported,
            )

            SettingSection(
                title = "动效强度",
                description = if (motionLocked) {
                    "系统已开启「移除动画」无障碍设置，已强制锁定为「关闭」"
                } else {
                    null
                },
            ) {
                ChipRow(
                    options = MOTION_LEVEL_OPTIONS,
                    selected = if (motionLocked) "none" else motionLevel,
                    onSelect = viewModel::setMotionLevel,
                    enabled = !motionLocked,
                )
            }
        }
    }
}

@Composable
private fun SettingSection(
    title: String,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            content()
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchSection(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
            }
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}
