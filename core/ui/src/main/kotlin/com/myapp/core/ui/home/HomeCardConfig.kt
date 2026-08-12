package com.myapp.core.ui.home

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 一张卡片的用户配置：顺序（列表位置）与是否显示。 */
@Serializable
data class HomeCardConfigEntry(val id: String, val enabled: Boolean = true)

/**
 * 首页卡片排序/显隐配置的编解码与应用（PRD 4.7.2）。
 *
 * 以 JSON 字符串存进 [com.myapp.core.datastore.AppPreferences.homeCardConfig]。
 * 解码失败（脏数据、版本升级字段变化）一律退回空列表——首页要能显示，不能因为一条坏配置整页崩掉。
 */
object HomeCardConfig {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(HomeCardConfigEntry.serializer())

    fun decode(raw: String): List<HomeCardConfigEntry> =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    fun encode(entries: List<HomeCardConfigEntry>): String = json.encodeToString(serializer, entries)

    /**
     * 按保存的顺序排列 cards；没存过配置（首次使用）按 [HomeCard.defaultOrder] 排。
     * 配置里没出现的新卡片（版本升级新增）按 defaultOrder 追加在已知卡片之后。
     */
    fun <T : HomeCard> applyOrder(cards: List<T>, raw: String): List<T> {
        val entries = decode(raw)
        if (entries.isEmpty()) return cards.sortedBy { it.defaultOrder }

        val orderIndex = entries.withIndex().associate { (index, entry) -> entry.id to index }
        val known = cards.filter { orderIndex.containsKey(it.id) }.sortedBy { orderIndex.getValue(it.id) }
        val unknown = cards.filterNot { orderIndex.containsKey(it.id) }.sortedBy { it.defaultOrder }
        return known + unknown
    }

    /** 未在配置里出现过的卡片默认可见。 */
    fun isEnabled(id: String, raw: String): Boolean =
        decode(raw).firstOrNull { it.id == id }?.enabled ?: true
}
