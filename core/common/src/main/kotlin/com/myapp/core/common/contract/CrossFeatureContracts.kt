package com.myapp.core.common.contract

/**
 * 跨模块协作契约（PRD 4.7.1）。
 *
 * feature 之间**永远不互相依赖**。需要协作时在这里定义接口，
 * 由提供方 feature 实现并用 Hilt 绑定，调用方只注入接口。
 *
 * 例：资讯页的「存为笔记」不 import :feature:note，
 * 而是注入 [NoteWriter]，由 :feature:note 提供实现。
 * 这样删掉 :feature:note 时，只需换一个空实现，资讯页不用改。
 */

/** 写入一条笔记。由 :feature:note 实现。 */
interface NoteWriter {
    suspend fun createNote(content: String, tags: List<String> = emptyList()): Long
}

/** 记一笔账。由 :feature:ledger 实现，供快捷入口、小组件、通知解析调用。 */
interface LedgerWriter {
    suspend fun recordExpense(
        amountCents: Long,
        merchant: String?,
        category: String?,
        occurredAt: Long,
        raw: String? = null,
    ): Long
}

/** 提醒调度。由 :core 层实现，供各 feature 注册闹钟，避免每个 feature 各写一套。 */
interface ReminderScheduler {
    /**
     * @param key 业务唯一键，如 "todo:42"。同 key 重复注册会覆盖。
     * 实现必须用 setExactAndAllowWhileIdle 以穿透 Doze（PRD 9.3）。
     */
    fun schedule(key: String, triggerAtMillis: Long, title: String, body: String)

    fun cancel(key: String)

    /** 开机 / 应用更新后重建全部闹钟。 */
    suspend fun rescheduleAll()
}

/**
 * 知识源（PRD 4.7.4）。
 * V1 实现是「飞书公开网页 + WebView 正文提取」，
 * 将来换 Notion / 本地 Markdown 只需换实现，上层无感知。
 */
interface KnowledgeSource {
    suspend fun refresh()
    suspend fun pickDailyKnowledge(): KnowledgeItem?
}

data class KnowledgeItem(
    val sourceId: Long,
    val sectionIndex: Int,
    val title: String,
    val summary: String,
    val url: String?,
)

/**
 * 同步能力占位（PRD 4.7.4）。
 * V1 无服务器，只有 [LocalExportSyncProvider] 一种手动导出实现。
 * 接口先留好，将来接 WebDAV / 私有服务器时不用重构数据层。
 */
interface SyncProvider {
    val isAvailable: Boolean
    suspend fun push(): Result<Unit>
    suspend fun pull(): Result<Unit>
}
