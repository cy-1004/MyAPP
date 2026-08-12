package com.myapp.feature.knowledge.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.common.time.asRelativeText
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors

/**
 * 知识源阅读页（PRD 3.7）：内嵌 WebView 保留飞书原生排版 + 自定义工具栏。
 *
 * 加载失败（断网等）时叠加显示缓存的纯文本降级视图，标注「缓存于 X」；
 * 没有缓存则提示无网络。WebView 本身该干嘛干嘛，读取失败不阻塞正常场景。
 */
@Composable
fun KnowledgeReaderScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeReaderViewModel = hiltViewModel(),
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var loadFailed by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = source?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        loadFailed = false
                        webViewRef?.reload()
                        viewModel.refreshContent()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = {
                        source?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "用浏览器打开")
                    }
                    IconButton(onClick = viewModel::togglePinned) {
                        val pinned = source?.pinned == true
                        Icon(
                            imageVector = if (pinned) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (pinned) "移出知识池" else "加入知识池",
                        )
                    }
                    IconButton(onClick = {
                        source?.let { clipboard.setText(AnnotatedString(it.url)) }
                    }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制链接")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val currentSource = source
            if (currentSource == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                return@Box
            }

            KnowledgeWebView(
                url = currentSource.url,
                onError = { loadFailed = true },
                onLoaded = { webView -> webViewRef = webView },
                modifier = Modifier.fillMaxSize(),
            )

            if (loadFailed) {
                OfflineFallback(
                    contentText = content?.contentText,
                    fetchedAt = content?.fetchedAt,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KnowledgeWebView(
    url: String,
    onError: () -> Unit,
    onLoaded: (WebView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) onError()
                    }
                }
                onLoaded(this)
                loadUrl(url)
            }
        },
    )
}

@Composable
private fun OfflineFallback(contentText: String?, fetchedAt: Long?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
    ) {
        if (contentText.isNullOrBlank() || fetchedAt == null) {
            Text(
                text = "加载失败，且暂无缓存正文可供离线阅读，请检查网络后重试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
            return@Column
        }
        Text(
            text = "缓存于 ${fetchedAt.asRelativeText()}，当前离线降级阅读",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.appColors.textTertiary,
            modifier = Modifier.padding(bottom = Spacing.md),
        )
        Text(
            text = contentText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
