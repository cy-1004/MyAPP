package com.myapp.feature.settings.cardorder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing

/**
 * 首页卡片排序设置页（PRD 3.11 / 4.7.2）。
 *
 * 卡片量级小（个位数），用确定性的上下箭头而不是拖拽——与分类管理同一套取舍。
 */
@Composable
fun HomeCardOrderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeCardOrderViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("首页卡片排序") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            EmptyState(
                text = "暂无可排序的首页卡片",
                modifier = Modifier.fillMaxSize(),
            )
            return@Scaffold
        }

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
            items(items = items, key = { it.id }) { item ->
                HomeCardOrderRow(
                    item = item,
                    isFirst = item.id == items.first().id,
                    isLast = item.id == items.last().id,
                    onMoveUp = { viewModel.moveUp(item.id) },
                    onMoveDown = { viewModel.moveDown(item.id) },
                    onToggleEnabled = { viewModel.setEnabled(item.id, it) },
                )
            }
        }
    }
}

@Composable
private fun HomeCardOrderRow(
    item: HomeCardOrderItem,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
            }

            Switch(checked = item.enabled, onCheckedChange = onToggleEnabled)
        }
    }
}
