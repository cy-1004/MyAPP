package com.myapp.feature.knowledge.extract.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 正文提取选择器设置页（PRD 3.7）：「提取选择器存本地配置，可在 App 内调整而无需发版」。
 *
 * 顺序有意义（依次尝试，第一个抓到非空文本的生效），所以用上移/下移箭头而不是任意排序，
 * 与首页卡片排序/分类管理同一套交互取舍——选择器量级个位数，用不上拖拽。
 */
@Composable
fun KnowledgeExtractSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeExtractSettingsViewModel = hiltViewModel(),
) {
    val selectors by viewModel.selectors.collectAsStateWithLifecycle()
    var newSelector by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("正文提取设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetToDefault) {
                        Icon(Icons.Outlined.RestartAlt, contentDescription = "恢复默认")
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
                top = innerPadding.calculateTopPadding() + Spacing.md,
                bottom = innerPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item(key = "hint") {
                Text(
                    text = "飞书文档正文的 CSS 选择器候选，按顺序依次尝试，" +
                        "第一个抓到非空文本的生效；全部选择器都抓不到时兜底整个页面正文。" +
                        "飞书改版导致提取失效时可以在这里调整，不用等发版。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }

            item(key = "add") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    OutlinedTextField(
                        value = newSelector,
                        onValueChange = { newSelector = it },
                        label = { Text("新选择器，如 .docx-content") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        viewModel.add(newSelector)
                        newSelector = ""
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加")
                    }
                }
            }

            if (selectors.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "没有选择器了，提取会直接兜底读整个页面正文",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textTertiary,
                    )
                }
            }

            items(items = selectors, key = { it }) { selector ->
                val index = selectors.indexOf(selector)
                SelectorRow(
                    selector = selector,
                    isFirst = index == 0,
                    isLast = index == selectors.lastIndex,
                    onMoveUp = { viewModel.move(selector, -1) },
                    onMoveDown = { viewModel.move(selector, 1) },
                    onDelete = { viewModel.remove(selector) },
                )
            }
        }
    }
}

@Composable
private fun SelectorRow(
    selector: String,
    isFirst: Boolean,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = selector,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(top = Spacing.sm),
            )
            IconButton(onClick = onMoveUp, enabled = !isFirst) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
            }
            IconButton(onClick = onMoveDown, enabled = !isLast) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除")
            }
        }
    }
}
