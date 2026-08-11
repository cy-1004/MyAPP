package com.myapp.feature.ledger.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.ledgerDataStore by preferencesDataStore(name = "ledger_preferences")

/** 一条未被规则引擎识别的支付通知原文，等待用户手工补录。 */
@Serializable
data class UnrecognizedItem(
    val id: Long,
    val channel: String?,
    val title: String,
    val text: String,
    val occurredAt: Long,
)

/**
 * 记账模块的本地偏好（DataStore JSON）。
 *
 * 与 AppPreferences 分开：这两类数据是记账模块私有的中间产物（未识别队列、商户学习映射），
 * 不属于全局偏好；也不进 SQLite——量级小、无查询需求，没必要为此做一次 schema 迁移。
 */
@Singleton
class LedgerPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.ledgerDataStore

    // ---- 未识别队列 ----

    val unrecognized: Flow<List<UnrecognizedItem>> = store.data.map { prefs ->
        prefs[Keys.UNRECOGNIZED]?.let(::decodeList) ?: emptyList()
    }

    /** 追加一条未识别；与已有条目 title+text+channel 完全相同则跳过（通知重复投递）。 */
    suspend fun addUnrecognized(item: UnrecognizedItem) {
        store.edit { prefs ->
            val list = prefs[Keys.UNRECOGNIZED]?.let(::decodeList) ?: emptyList()
            val dup = list.any { it.channel == item.channel && it.title == item.title && it.text == item.text }
            if (!dup) {
                prefs[Keys.UNRECOGNIZED] = Json.encodeToString(list + item)
            }
        }
    }

    suspend fun removeUnrecognized(id: Long) {
        store.edit { prefs ->
            val list = prefs[Keys.UNRECOGNIZED]?.let(::decodeList) ?: return@edit
            prefs[Keys.UNRECOGNIZED] = Json.encodeToString(list.filter { it.id != id })
        }
    }

    // ---- 商户 → 分类 学习映射（用户改过一次分类后记住）----

    val merchantCategoryMap: Flow<Map<String, String>> = store.data.map { prefs ->
        decodeMerchantMap(prefs[Keys.MERCHANT_CATEGORY_MAP])
    }

    suspend fun learnMerchantCategory(merchant: String, categoryName: String) {
        if (merchant.isBlank() || categoryName.isBlank()) return
        store.edit { prefs ->
            // 注意别写成 `?: return@edit`：第一次学习时这个 key 还不存在，
            // 提前返回会让映射表永远建不起来（学习功能整体静默失效）
            val map = decodeMerchantMap(prefs[Keys.MERCHANT_CATEGORY_MAP]).toMutableMap()
            map[merchant] = categoryName
            // 防无限膨胀：超过 200 条丢最旧的（LinkedHashMap 保持插入序）
            while (map.size > MAX_LEARNED_ENTRIES) {
                map.remove(map.keys.first())
            }
            prefs[Keys.MERCHANT_CATEGORY_MAP] = Json.encodeToString(map)
        }
    }

    /**
     * 分类改名后同步学习映射的 value。
     *
     * 映射表存的是分类**名**不是 id（recordExpense 契约按名查库），改名不同步的话
     * 自动记账会按旧名查不到分类，静默落到「未分类」——用户只会觉得「学习功能失灵了」。
     */
    suspend fun renameLearnedCategory(oldName: String, newName: String) {
        if (oldName.isBlank() || newName.isBlank() || oldName == newName) return
        store.edit { prefs ->
            val map = decodeMerchantMap(prefs[Keys.MERCHANT_CATEGORY_MAP])
            if (map.values.none { it == oldName }) return@edit
            val renamed = map.mapValues { (_, value) -> if (value == oldName) newName else value }
            prefs[Keys.MERCHANT_CATEGORY_MAP] = Json.encodeToString(renamed)
        }
    }

    private fun decodeMerchantMap(raw: String?): Map<String, String> {
        if (raw == null) return emptyMap()
        return runCatching { Json.decodeFromString<Map<String, String>>(raw) }
            .getOrDefault(emptyMap())
    }

    private fun decodeList(raw: String): List<UnrecognizedItem> =
        runCatching { Json.decodeFromString<List<UnrecognizedItem>>(raw) }.getOrDefault(emptyList())

    private object Keys {
        val UNRECOGNIZED: Preferences.Key<String> = stringPreferencesKey("ledger.unrecognized")
        val MERCHANT_CATEGORY_MAP: Preferences.Key<String> =
            stringPreferencesKey("ledger.merchant_category_map")
    }

    private companion object {
        const val MAX_LEARNED_ENTRIES = 200
    }
}
