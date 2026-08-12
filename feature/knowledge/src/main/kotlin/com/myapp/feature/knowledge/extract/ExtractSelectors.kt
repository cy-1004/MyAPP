package com.myapp.feature.knowledge.extract

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.extractDataStore by preferencesDataStore(name = "knowledge_extract_preferences")

/**
 * 正文提取用的 CSS 选择器候选列表（PRD 3.7）。
 *
 * PRD 原文：「提取选择器存本地配置，可在 App 内调整而无需发版」——飞书前端改版会让写死在
 * 代码里的选择器失效。设置页入口见
 * [com.myapp.feature.knowledge.extract.settings.KnowledgeExtractSettingsScreen]。
 *
 * 按顺序依次尝试，第一个抓到非空文本的选择器生效；全部选择器都抓不到时兜底整个 body。
 */
@Serializable
data class ExtractSelectorConfig(
    val selectors: List<String> = DEFAULT_SELECTORS,
)

/** 飞书文档/知识库/多维表格页面常见的正文容器类名，按经验排序，非精确保证。 */
val DEFAULT_SELECTORS = listOf(
    ".docx-content",
    ".doc-content",
    ".wiki-content",
    "article",
    "main",
)

@Singleton
class ExtractSelectorStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.extractDataStore
    private val serializer = ListSerializer(String.serializer())

    val config: Flow<ExtractSelectorConfig> = store.data.map { prefs ->
        val raw = prefs[Keys.SELECTORS]
        val selectors = raw?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
        ExtractSelectorConfig(selectors ?: DEFAULT_SELECTORS)
    }

    suspend fun setSelectors(selectors: List<String>) {
        store.edit { it[Keys.SELECTORS] = Json.encodeToString(serializer, selectors) }
    }

    private object Keys {
        val SELECTORS = stringPreferencesKey("knowledge.extractSelectors")
    }
}

/**
 * 把 [selector] 在 [selectors] 里挪动 [delta] 位，返回新顺序；已在边界时返回 null。
 *
 * 顺序有意义（依次尝试，第一个命中生效），所以设置页要能调整顺序，
 * 与 [com.myapp.feature.knowledge.data.reorderIds] 同一套「整表按下标重排」实现，
 * 只是这里操作的是字符串而不是 id。
 */
fun reorderSelectors(selectors: List<String>, selector: String, delta: Int): List<String>? {
    val from = selectors.indexOf(selector)
    if (from < 0) return null
    val to = from + delta
    if (to < 0 || to > selectors.lastIndex) return null
    val mutable = selectors.toMutableList()
    mutable.removeAt(from)
    mutable.add(to, selector)
    return mutable
}
