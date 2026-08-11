package com.myapp.feature.ledger.data

import com.myapp.feature.ledger.notification.CustomRule
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RuleStore 序列化层测试（PRD 3.6.1 Phase 3）。
 *
 * RuleStore 自身依赖 Android Context（preferencesDataStore 扩展属性），无法在纯 JVM 跑。
 * 但 Store 的核心逻辑就是 JSON 编解码 + List/Set 操作，这里直接测这套编解码：
 * - List<CustomRule> 往返
 * - Set<String>（disabledBuiltinIds）往返
 * - 空列表 / 空集合的默认值
 * - 损坏 JSON 的容错（runCatching 兜底空列表）
 *
 * DataStore 本身的读写可靠性由 Google 测过，不在本测试范围。
 */
class RuleStoreTest {

    private val ruleListSerializer = ListSerializer(CustomRule.serializer())
    private val stringSetSerializer = SetSerializer(String.serializer())

    @Test
    fun customRuleList_roundTrip() {
        val rules = listOf(
            CustomRule(
                id = 1L,
                name = "微信凭证",
                channel = "WECHAT",
                direction = TransactionDirection.EXPENSE,
                titleKeywords = listOf("微信"),
                amountKeyword = "付款",
                merchantKeyword = "向",
                merchantBeforeAmount = true,
            ),
            CustomRule(
                id = 2L,
                name = "通用元",
                channel = null,
                direction = TransactionDirection.INCOME,
                titleKeywords = emptyList(),
                amountKeyword = "收款",
                merchantKeyword = null,
                merchantBeforeAmount = false,
            ),
        )
        val json = Json.encodeToString(ruleListSerializer, rules)
        val decoded = Json.decodeFromString(ruleListSerializer, json)
        assertEquals(rules, decoded)
    }

    @Test
    fun emptyList_roundTrip() {
        val json = Json.encodeToString(ruleListSerializer, emptyList<CustomRule>())
        val decoded = Json.decodeFromString(ruleListSerializer, json)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun disabledBuiltinIds_roundTrip() {
        val set = setOf("builtin.wechat.voucher", "builtin.generic.symbol")
        val json = Json.encodeToString(stringSetSerializer, set)
        val decoded = Json.decodeFromString(stringSetSerializer, json)
        assertEquals(set, decoded)
    }

    @Test
    fun emptySet_roundTrip() {
        val json = Json.encodeToString(stringSetSerializer, emptySet<String>())
        val decoded = Json.decodeFromString(stringSetSerializer, json)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun corruptJson_customRules_returnsEmptyList() {
        // RuleStore.decodeCustomRules 用 runCatching 兜底；模拟损坏 JSON
        val decoded = runCatching {
            Json.decodeFromString(ruleListSerializer, "not a json")
        }.getOrDefault(emptyList())
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun corruptJson_disabledIds_returnsEmptySet() {
        val decoded = runCatching {
            Json.decodeFromString(stringSetSerializer, "}{")
        }.getOrDefault(emptySet())
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun customRuleWithMinimalFields_serializes() {
        // 只填必填字段（merchantKeyword 为 null 默认）
        val rule = CustomRule(
            id = 0L,
            name = "测试",
            channel = null,
            direction = TransactionDirection.EXPENSE,
            titleKeywords = emptyList(),
            amountKeyword = "付款",
        )
        val json = Json.encodeToString(CustomRule.serializer(), rule)
        val decoded = Json.decodeFromString(CustomRule.serializer(), json)
        assertEquals(rule, decoded)
        assertEquals(null, decoded.merchantKeyword)
        assertEquals(false, decoded.merchantBeforeAmount)
    }
}
