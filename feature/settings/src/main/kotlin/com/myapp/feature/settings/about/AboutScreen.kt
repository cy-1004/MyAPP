package com.myapp.feature.settings.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 关于页（PRD 3.12）：版本信息 + 开源许可。
 *
 * 不做「检查更新」——单人自用不上架应用商店，新版本由开发者手动安装 APK。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val info = viewModel.versionInfo

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.padding(top = Spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(text = "MyAPP", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "版本 ${info.versionName} (${info.versionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }
            }

            item(key = "info") {
                AppCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        AboutInfoRow(label = "应用包名", value = info.packageName)
                        // 云备份默认关闭、需要手动开——不能笼统说「无云端备份」，
                        // 那会让开了云备份的人误以为设置没生效
                        AboutInfoRow(
                            label = "本地存储",
                            value = "数据默认仅存本机；「云备份」页可手动开启加密云端同步",
                        )
                    }
                }
            }

            item(key = "licenses_title") {
                Text(
                    text = "开源许可",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appColors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                )
            }

            items(items = openSourceLicenses, key = { it.name }) { entry ->
                AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${entry.author} · ${entry.license}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.appColors.textSecondary,
                        )
                    }
                }
            }

            item(key = "footer") {
                Text(
                    text = "单人自用应用，不上架应用商店，新版本由开发者手动安装。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textTertiary,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.textSecondary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
