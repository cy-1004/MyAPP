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
    /**
     * @param direction 'EXPENSE' / 'INCOME'，默认支出。自动记账解析收款通知时传 'INCOME'。
     */
    suspend fun recordExpense(
        amountCents: Long,
        merchant: String?,
        category: String?,
        occurredAt: Long,
        raw: String? = null,
        direction: String = "EXPENSE",
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

/** 一条待提醒项，供 [ReminderScheduler.rescheduleAll] 重新注册闹钟。 */
data class ReminderRequest(
    val key: String,
    val triggerAtMillis: Long,
    val title: String,
    val body: String,
)

/**
 * 提醒来源。由各 feature 实现并 `@Binds @IntoSet` 绑定——和 [HomeCard] 一样的插件模式，
 * 这样 `rescheduleAll()` 收集全部实现时，加新模块不用改 scheduler 一行代码。
 */
interface ReminderSource {
    suspend fun pendingReminders(): List<ReminderRequest>
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

/**
 * @param sourceId 知识源 id；[isNoteFallback]=true 时改存笔记 id（两者是不同的 id 空间，
 *   但对调用方来说都是"点击后要打开的那个东西的 id"，复用一个字段没必要拆两个）
 * @param title 知识点标题（抓取到的正文标题，不一定等于 [sourceName]）
 * @param sourceName 来源页面名（用户给知识源起的名字，PRD 3.8「来源页面名」）
 */
data class KnowledgeItem(
    val sourceId: Long,
    val sectionIndex: Int,
    val title: String,
    val summary: String,
    val sourceName: String,
    val url: String?,
    /** true = 知识池为空/提取失败时从笔记降级来的（PRD 3.8），此时 [sourceId] 是笔记 id、[url]=null。 */
    val isNoteFallback: Boolean = false,
)

/**
 * 供知识池为空时降级取材（PRD 3.8：「保证卡片永不空白」）。由 :feature:note 实现。
 * 只读，跟 [NoteWriter] 分开——两个不同方向的能力没必要挤进一个接口。
 */
interface NoteBrowser {
    suspend fun randomNoteSnippet(): NoteSnippet?
}

data class NoteSnippet(val noteId: Long, val text: String)

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

/**
 * 勾选 / 取消勾选待办。由 :feature:todo 实现。
 *
 * 桌面小组件的圆圈勾选走这条契约，而不是直接写 TodoDao——重复任务的
 * 「生成下一次」逻辑在 TodoRepository.setDone 里，绕过它会让小组件与
 * App 内的行为分叉（下次提醒不会生成）。
 */
interface TodoToggleWriter {
    suspend fun setDone(id: Long, done: Boolean)
}

/**
 * 小组件刷新通知（PRD 3.10）。由 :feature:widget 实现。
 *
 * 各 feature 在数据变更（新增账目 / 完成待办 / 修改预算 / 增删纪念日）后调用
 * [notifyDataChanged]，widget 侧收集后对全部已添加的小组件执行 updateAll()。
 * 纯通知通道，不传数据——widget 自己从 Room 查最新状态。
 */
interface WidgetRefreshNotifier {
    suspend fun notifyDataChanged()
}
