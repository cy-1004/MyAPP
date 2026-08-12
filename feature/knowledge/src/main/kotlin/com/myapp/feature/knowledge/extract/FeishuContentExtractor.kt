package com.myapp.feature.knowledge.extract

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONTokener

/** 提取结果（PRD 3.7）：提取失败不影响阅读，只影响摘要/搜索，所以用密封类而非抛异常。 */
sealed interface ExtractResult {
    data class Success(val title: String, val contentText: String) : ExtractResult
    data object LoginRequired : ExtractResult
    data object Failed : ExtractResult
}

/**
 * 飞书公开页正文提取（PRD 3.7）：本项目第一次用 WebView，也是唯一一处。
 *
 * 飞书文档是 JS 渲染的 SPA，直接 HTTP 抓 HTML 拿不到正文，必须走无头 WebView
 * 渲染完成后注入 JS 抽取——这是 PRD 明确要求的技术路径，不能换成 Jsoup 静态解析。
 *
 * 全程在 [Dispatchers.Main] 上跑：WebView 的创建/加载/JS 执行都要求主线程，
 * 调用方（[KnowledgeExtractWorker]，运行在后台线程）用 `withContext(Dispatchers.Main)` 切过来。
 * `context` 用 applicationContext——Worker 没有 Activity 可用，这是无头 WebView
 * 在后台任务里的标准做法；某些 ROM 可能限制无 Activity 的 WebView（PRD 风险登记表已知），
 * 提取失败时静默降级，不影响 [com.myapp.feature.knowledge.reader.KnowledgeReaderScreen]
 * 的正常内嵌 WebView 阅读。
 */
@Singleton
class FeishuContentExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val selectorStore: ExtractSelectorStore,
) {

    suspend fun extract(url: String): ExtractResult {
        val selectors = selectorStore.config.first().selectors
        return withTimeoutOrNull(TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                runCatching { loadAndExtract(url, selectors) }.getOrDefault(ExtractResult.Failed)
            }
        } ?: ExtractResult.Failed
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadAndExtract(url: String, selectors: List<String>): ExtractResult {
        val webView = WebView(context)
        return try {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true

            val finalUrl = awaitPageLoad(webView, url)
            if (isLoginPage(finalUrl)) return ExtractResult.LoginRequired

            val title = decodeJsResult(evalJs(webView, "document.title")).orEmpty()
            val content = decodeJsResult(evalJs(webView, extractionScript(selectors)))
            if (content.isNullOrBlank()) ExtractResult.Failed else ExtractResult.Success(title, content)
        } finally {
            webView.destroy()
        }
    }

    private suspend fun awaitPageLoad(webView: WebView, url: String): String =
        suspendCancellableCoroutine { cont ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    if (cont.isActive) cont.resume(loadedUrl)
                }
            }
            webView.loadUrl(url)
        }

    private suspend fun evalJs(webView: WebView, script: String): String? =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { result ->
                if (cont.isActive) cont.resume(result)
            }
        }

    /** 依次尝试候选选择器，第一个抓到非空文本就用；全部落空则兜底整个 body。 */
    private fun extractionScript(selectors: List<String>): String {
        val selectorsJson = JSONArray(selectors).toString()
        return """
            (function() {
                var selectors = $selectorsJson;
                for (var i = 0; i < selectors.length; i++) {
                    try {
                        var el = document.querySelector(selectors[i]);
                        if (el && el.innerText && el.innerText.trim().length > 0) {
                            return el.innerText;
                        }
                    } catch (e) {}
                }
                return document.body ? document.body.innerText : '';
            })()
        """.trimIndent()
    }

    private fun isLoginPage(url: String): Boolean =
        LOGIN_URL_MARKERS.any { url.contains(it, ignoreCase = true) }

    /** [WebView.evaluateJavascript] 的回调返回 JSON 编码字符串（含引号转义），需要解回原始文本。 */
    private fun decodeJsResult(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching { JSONTokener(raw).nextValue() as? String }.getOrNull()
    }

    companion object {
        private const val TIMEOUT_MS = 20_000L
        private val LOGIN_URL_MARKERS = listOf("accounts.feishu.cn", "/login", "/passport")
    }
}
