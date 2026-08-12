package com.myapp.feature.knowledge.list

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
import com.myapp.core.designsystem.theme.appColors

/**
 * 新建 / 编辑知识源（PRD 3.7）：粘贴飞书公开链接 + 可选标题/分组。
 *
 * 保存成功后立刻返回列表页——提取任务在后台跑，不用等它完成（[KnowledgeRepository.save]
 * 已经 enqueue 好了），符合 PRD「提取失败不影响使用」的原则。
 */
@Composable
fun KnowledgeSourceEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeSourceEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draft.isNew) "新建知识源" else "编辑知识源",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved = onBack) },
                        enabled = draft.canSave,
                    ) {
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
                label = { Text("飞书公开链接") },
                placeholder = { Text("https://xxx.feishu.cn/docx/...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
            )
            Text(
                text = "页面要保持「互联网公开」分享状态，需要登录的链接会在列表标记「已失效」",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textTertiary,
                modifier = Modifier.padding(bottom = Spacing.lg),
            )

            OutlinedTextField(
                value = draft.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("标题（可选，留空用抓到的正文标题）") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
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
