package com.myapp.feature.feed.sources

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.theme.Spacing

/**
 * 新建 / 编辑订阅源（PRD 3.9）：RSS/Atom 地址 + 可选标题/分组。
 * 保存后立刻拉一次该源（[RssRepository.save] 已经触发），不用等下一次刷新才看到文章。
 */
@Composable
fun RssSourceEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RssSourceEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draft.isNew) "新建订阅源" else "编辑订阅源",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved = onBack) }, enabled = draft.canSave) {
                        Text("保存")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Spacing.xl)
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            OutlinedTextField(
                value = draft.url,
                onValueChange = viewModel::updateUrl,
                label = { Text("RSS / Atom 地址") },
                placeholder = { Text("https://example.com/feed.xml") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
            )

            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("标题（可选，留空用订阅源自带的标题）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md),
            )

            OutlinedTextField(
                value = draft.groupName,
                onValueChange = viewModel::updateGroupName,
                label = { Text("分组（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
