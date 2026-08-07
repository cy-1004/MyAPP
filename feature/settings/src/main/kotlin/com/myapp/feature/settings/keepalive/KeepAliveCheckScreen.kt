package com.myapp.feature.settings.keepalive

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pending
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 保活自检向导页（PRD 9.3）。
 *
 * 首启强制走一遍（startDestination = KeepAliveCheck）；
 * 后续从设置页进入复习。完成后写 onboarding 标志并导航回 Home。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeepAliveCheckScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    firstRun: Boolean = false,
    viewModel: KeepAliveCheckViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val canComplete = items.all { it.isSatisfied }

    // 首启时拦截系统返回键，防止用户跳过自检直接退出
    if (firstRun) {
        BackHandler { /* 拦截，不响应 */ }
    }

    // 从系统设置返回时刷新检测项状态
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { /* no-op */ }
    }

    // 通知权限请求：用户点「请求权限」按钮触发，结果回来后 onRefresh 会重查
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* onRefresh 已在 onResume 里调用，这里无需额外处理 */ }

    // 完成事件：VM 写完偏好后发事件，Screen 负责导航
    LaunchedEffect(Unit) {
        viewModel.completed.collect { onComplete() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("保活自检") },
                navigationIcon = {
                    if (!firstRun) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "intro") {
                IntroCard()
            }

            items(items = items, key = { it.id }) { item ->
                CheckItemRow(
                    item = item,
                    onAutoAction = { id ->
                        when (id) {
                            KeepAliveCheckIds.NOTIFICATION ->
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else -> viewModel.openSystemSettings(id)
                        }
                    },
                    onManualToggle = { id, checked -> viewModel.markManualDone(id, checked) },
                    onTryDirectOpen = { id -> viewModel.tryOpenColorOsSetting(id) },
                )
            }

            item(key = "complete") {
                Spacer(modifier = Modifier.padding(top = Spacing.sm))
                Button(
                    onClick = { viewModel.complete() },
                    enabled = canComplete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("完成")
                }
                if (!canComplete) {
                    Text(
                        text = "请先完成所有检测项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "为什么需要这些设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(Spacing.xs))
            Text(
                text = "ColorOS 的后台管理会冻结未加白名单的应用，导致提醒闹钟不响、记账监听失效、小组件不刷新。请逐项确认以下设置已开启。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
        }
    }
}

@Composable
private fun CheckItemRow(
    item: KeepAliveCheckItem,
    onAutoAction: (String) -> Unit,
    onManualToggle: (String, Boolean) -> Unit,
    onTryDirectOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIcon(item)
                Spacer(modifier = Modifier.size(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                }
            }

            if (item.category == KeepAliveCheckItem.Category.MANUAL && item.pathHint != null) {
                Text(
                    text = item.pathHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textTertiary,
                    modifier = Modifier.padding(
                        start = Spacing.xxl + Spacing.md,
                        top = Spacing.xs,
                    ),
                )
            }

            Spacer(modifier = Modifier.size(Spacing.sm))

            when (item.category) {
                KeepAliveCheckItem.Category.AUTO -> {
                    if (item.status != KeepAliveCheckItem.Status.PASSED) {
                        OutlinedButton(
                            onClick = { onAutoAction(item.id) },
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(item.actionLabel ?: "去设置")
                        }
                    }
                }
                KeepAliveCheckItem.Category.READONLY, KeepAliveCheckItem.Category.TEXTONLY -> {
                    // 无按钮
                }
                KeepAliveCheckItem.Category.MANUAL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.canTryDirectOpen) {
                            TextButton(
                                onClick = { onTryDirectOpen(item.id) },
                            ) {
                                Text("尝试直达")
                            }
                        } else {
                            Spacer(modifier = Modifier.size(1.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.manualDone,
                                onCheckedChange = { onManualToggle(item.id, it) },
                            )
                            Text(
                                text = item.actionLabel ?: "我已设置",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(item: KeepAliveCheckItem) {
    val (icon, tint) = when (item.category) {
        KeepAliveCheckItem.Category.AUTO -> when (item.status) {
            KeepAliveCheckItem.Status.PASSED ->
                Icons.Outlined.CheckCircle to MaterialTheme.appColors.success
            else ->
                Icons.Outlined.WarningAmber to MaterialTheme.appColors.warning
        }
        KeepAliveCheckItem.Category.READONLY ->
            Icons.Outlined.CheckCircle to MaterialTheme.appColors.success
        KeepAliveCheckItem.Category.TEXTONLY ->
            Icons.Outlined.Info to MaterialTheme.appColors.textTertiary
        KeepAliveCheckItem.Category.MANUAL ->
            if (item.manualDone) Icons.Outlined.CheckCircle to MaterialTheme.appColors.success
            else Icons.Outlined.Pending to MaterialTheme.appColors.textTertiary
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(24.dp),
    )
}
