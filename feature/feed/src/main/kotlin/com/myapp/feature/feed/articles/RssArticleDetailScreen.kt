package com.myapp.feature.feed.articles

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * RSS 文章详情（PRD 3.9）：有正文（`content`）就直接展示纯文本；没有正文（大多数只提供摘要的源）
 * 用 Custom Tabs 打开原链接——比 `ACTION_VIEW` 更快（预热+复用浏览器进程），且不离开 App 的
 * 任务栈观感。
 */
@Composable
fun RssArticleDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RssArticleDetailViewModel = hiltViewModel(),
) {
    val article by viewModel.article.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(article?.link, article?.content) {
        val current = article ?: return@LaunchedEffect
        if (current.content.isNullOrBlank() && current.link.isNotBlank()) {
            CustomTabsIntent.Builder().build().launchUrl(context, current.link.toUri())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("资讯详情", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    val current = article
                    if (current != null) {
                        IconButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, current.link.toUri()) }) {
                            Icon(Icons.Outlined.OpenInBrowser, contentDescription = "在浏览器打开")
                        }
                        IconButton(
                            onClick = {
                                viewModel.saveAsNote(
                                    onSaved = {
                                        // 详情页不常驻 Snackbar 观感，saveAsNote 走 fire-and-forget 提示交给列表页
                                    },
                                )
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Article, contentDescription = "存为笔记")
                        }
                        IconButton(onClick = { viewModel.setFavorite(!current.isFavorite) }) {
                            Icon(
                                imageVector = if (current.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = if (current.isFavorite) "取消收藏" else "收藏",
                                tint = if (current.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.appColors.textTertiary,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val current = article
        if (current == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator(modifier = Modifier.padding(top = Spacing.xxl)) }
            return@Scaffold
        }

        val content = current.content
        if (content.isNullOrBlank()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(Spacing.xl),
            ) {
                Text(current.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "该订阅源没有提供正文，已在浏览器中打开原文",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.appColors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(Spacing.xl)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(current.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = current.sourceTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textTertiary,
                    modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg),
                )
                Text(content, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
