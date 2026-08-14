package com.myapp.feature.knowledge.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route

/**
 * 题库章节列表（PRD 3.7 改版）——「知识库」子 tab 的主内容。
 *
 * 每章一行：章节名 + 题数 + 「是否参与每日抽题」开关。
 * 开关粒度定在章节而不是题目：500 道题一条条勾不现实，
 * 而「这阵子只复习 Redis 和 Spring」是真需求，章节这一级刚好。
 *
 * 这里是 content 而不是完整 Screen（没有自己的 Scaffold/TopAppBar）：
 * 顶栏由宿主 FeedScreen 统一提供，子页面各自再挂一个会让顶部叠两层、
 * 占掉一大块又不跟随滚动收起。
 */
@Composable
fun InterviewChapterListContent(
    onNavigate: (Route) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: InterviewChapterListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.loaded && state.groups.isEmpty()) {
        EmptyState(
            text = "题库还没导入。题库随 App 打包，重启一次即可导入",
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (state.loaded) {
            item(key = "summary") {
                Text(
                    text = "共 ${state.totalQuestions} 道题，其中 ${state.pooledQuestions} 道参与每日抽题",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }

        state.groups.forEach { group ->
            item(key = "doc_${group.docKey}") {
                DocHeader(
                    group = group,
                    onToggleAll = { viewModel.setDocInPool(group.docKey, it) },
                )
            }
            items(items = group.chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    onClick = { onNavigate(Route.InterviewChapter(chapter.id)) },
                    onToggleInPool = { viewModel.setChapterInPool(chapter.id, it) },
                )
            }
        }
    }
}

@Composable
private fun DocHeader(group: InterviewDocGroup, onToggleAll: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.docName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${group.chapters.size} 章 · ${group.questionCount} 题",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textTertiary,
                )
            }
            TextButton(onClick = { onToggleAll(!group.allInPool) }) {
                Text(if (group.allInPool) "全部移出" else "全部加入")
            }
        }
        HorizontalDivider(color = MaterialTheme.appColors.border)
    }
}

@Composable
private fun ChapterRow(
    chapter: InterviewChapterUi,
    onClick: () -> Unit,
    onToggleInPool: (Boolean) -> Unit,
) {
    AppCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${chapter.questionCount} 题" + if (chapter.inPool) "" else " · 已移出抽题",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (chapter.inPool) {
                        MaterialTheme.appColors.textTertiary
                    } else {
                        MaterialTheme.appColors.textSecondary
                    },
                )
            }
            Switch(checked = chapter.inPool, onCheckedChange = onToggleInPool)
        }
    }
}
