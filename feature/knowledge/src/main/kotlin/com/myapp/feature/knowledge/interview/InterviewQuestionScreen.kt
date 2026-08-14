package com.myapp.feature.knowledge.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 一道面试题的阅读页（PRD 3.7 改版）。
 *
 * 底部的「已掌握 / 再看看」与首页卡片是同一套反馈——从卡片跳进来看完整答案后
 * 往往才知道自己会不会，只在卡片上给按钮不够用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewQuestionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InterviewQuestionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.question?.chapterTitle ?: "题目",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val question = state.question
        if (question == null) {
            if (state.loaded) {
                EmptyState(text = "这道题不见了，可能题库刚更新过", modifier = Modifier.fillMaxSize())
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    PaddingValues(
                        start = Spacing.xl,
                        end = Spacing.xl,
                        top = Spacing.md,
                        bottom = Spacing.xxl,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = question.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${question.docName} · ${question.chapterTitle}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.appColors.textTertiary,
            )

            InterviewMarkdownBody(
                markdown = question.body,
                assetDir = "interview/${question.docKey}",
                modifier = Modifier.fillMaxWidth(),
            )

            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = { viewModel.snoozed(); onBack() },
                    modifier = Modifier.weight(1f),
                ) { Text("再看看") }
                Button(
                    onClick = { viewModel.mastered(); onBack() },
                    modifier = Modifier.weight(1f),
                ) { Text("已掌握") }
            }
        }
    }
}
