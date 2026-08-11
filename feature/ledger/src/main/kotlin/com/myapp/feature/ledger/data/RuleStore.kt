package com.myapp.feature.ledger.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myapp.core.common.time.AppTime
import com.myapp.feature.ledger.notification.CustomRule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.ruleDataStore by preferencesDataStore(name = "rule_preferences")

/**
 * 规则编辑器的本地存储（PRD 3.6.1 Phase 3）。
 *
 * 与 [LedgerPrefsStore] 分开存：
 * - 用 `rule_preferences` 而非 `ledger_preferences`，避免老版本用户的未识别队列被新 schema 误读；
 * - 规则量级小（用户级几十条顶天）、无复杂查询需求，没必要进 Room 也不需要 v6 迁移。
 *
 * 两个键：
 * - [Keys.CUSTOM_RULES]：自定义规则列表的 JSON（[List<CustomRule>]）
 * - [Keys.DISABLED_BUILTIN_IDS]：被用户停用的内置规则 id 集合（[Set<String>]）
 *
 * 内置规则本体在代码里（[com.myapp.feature.ledger.notification.builtinPaymentRules]），
 * 这里只记「哪些被停用了」，比把内置规则也序列化进 DataStore 简单且不怕规则迭代后旧 JSON 漂移。
 */
@Singleton
class RuleStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.ruleDataStore

    val customRules: Flow<List<CustomRule>> = store.data.map { prefs ->
        prefs[Keys.CUSTOM_RULES]?.let(::decodeCustomRules) ?: emptyList()
    }

    val disabledBuiltinIds: Flow<Set<String>> = store.data.map { prefs ->
        prefs[Keys.DISABLED_BUILTIN_IDS]?.let(::decodeDisabledIds) ?: emptySet()
    }

    /** 新增；id 用当前时间戳（与 UnrecognizedItem 同约定），冲突时自增避让。
     *  传入非 0 id 且已存在则视为替换（撤销删除场景）。 */
    suspend fun add(rule: CustomRule): Long {
        var newId = rule.id
        store.edit { prefs ->
            val list = prefs[Keys.CUSTOM_RULES]?.let(::decodeCustomRules) ?: emptyList()
            newId = if (rule.id == 0L) {
                var candidate = AppTime.now()
                while (list.any { it.id == candidate }) candidate++
                candidate
            } else {
                rule.id
            }
            val next = if (list.any { it.id == newId }) {
                list.map { if (it.id == newId) rule.copy(id = newId) else it }
            } else {
                list + rule.copy(id = newId)
            }
            prefs[Keys.CUSTOM_RULES] = Json.encodeToString(
                ListSerializer(CustomRule.serializer()),
                next,
            )
        }
        return newId
    }

    suspend fun update(rule: CustomRule) {
        store.edit { prefs ->
            val list = prefs[Keys.CUSTOM_RULES]?.let(::decodeCustomRules) ?: return@edit
            prefs[Keys.CUSTOM_RULES] = Json.encodeToString(
                ListSerializer(CustomRule.serializer()),
                list.map { if (it.id == rule.id) rule else it },
            )
        }
    }

    suspend fun delete(id: Long) {
        store.edit { prefs ->
            val list = prefs[Keys.CUSTOM_RULES]?.let(::decodeCustomRules) ?: return@edit
            prefs[Keys.CUSTOM_RULES] = Json.encodeToString(
                ListSerializer(CustomRule.serializer()),
                list.filter { it.id != id },
            )
        }
    }

    suspend fun setBuiltinEnabled(id: String, enabled: Boolean) {
        store.edit { prefs ->
            val set = prefs[Keys.DISABLED_BUILTIN_IDS]?.let(::decodeDisabledIds) ?: emptySet()
            val next = if (enabled) set - id else set + id
            prefs[Keys.DISABLED_BUILTIN_IDS] = Json.encodeToString(
                SetSerializer(String.serializer()),
                next,
            )
        }
    }

    private fun decodeCustomRules(raw: String): List<CustomRule> =
        runCatching {
            Json.decodeFromString(ListSerializer(CustomRule.serializer()), raw)
        }.getOrDefault(emptyList())

    private fun decodeDisabledIds(raw: String): Set<String> =
        runCatching {
            Json.decodeFromString(SetSerializer(String.serializer()), raw)
        }.getOrDefault(emptySet())

    private object Keys {
        val CUSTOM_RULES: Preferences.Key<String> = stringPreferencesKey("rule.custom_rules")
        val DISABLED_BUILTIN_IDS: Preferences.Key<String> =
            stringPreferencesKey("rule.disabled_builtin_ids")
    }
}
