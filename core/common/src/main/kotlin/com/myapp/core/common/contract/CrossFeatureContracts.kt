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
     * @param snoozable 触发时的通知要不要带「稍后提醒」操作按钮（PRD 3.3）。
     * 只有待办用得上，其它提醒来源留默认 false。
     */
    fun schedule(
        key: String,
        triggerAtMillis: Long,
        title: String,
        body: String,
        snoozable: Boolean = false,
    )

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
    val snoozable: Boolean = false,
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
 * 当前实现是「assets 里的 md 面试题库」（PRD 3.7 改版），
 * 之前是「飞书公开网页 + WebView 正文提取」——换实现时上层没有改动，
 * 这个接口的抽象是站得住的。
 */
interface KnowledgeSource {
    suspend fun refresh()
    suspend fun pickDailyKnowledge(): KnowledgeItem?
}

/**
 * 每日知识点的来源类型。决定卡片上的按钮、点击后跳哪里。
 *
 * 三者的 [KnowledgeItem.sourceId] 分别是题目 id / 知识源 id / 笔记 id——
 * 是三个不同的 id 空间，靠这个字段区分，不要靠猜。
 */
enum class KnowledgeItemKind {
    /** md 面试题库里的一道题（PRD 3.7 改版后的主力来源）。 */
    INTERVIEW_QUESTION,

    /** 飞书公开网页知识源（已降级为只读收藏，不再参与抽题，保留以兼容旧数据）。 */
    FEISHU_SOURCE,

    /** 题库为空时从本地笔记降级取的一条（PRD 3.8「保证卡片永不空白」）。 */
    NOTE_FALLBACK,
}

/**
 * @param sourceId 视 [kind] 而定：题目 id / 知识源 id / 笔记 id
 * @param title 知识点标题（面试题的题干，或抓取到的正文标题）
 * @param sourceName 来源名（面试题是所属章节名，飞书源是用户起的页面名）
 */
data class KnowledgeItem(
    val sourceId: Long,
    val sectionIndex: Int,
    val title: String,
    val summary: String,
    val sourceName: String,
    val url: String?,
    val kind: KnowledgeItemKind = KnowledgeItemKind.INTERVIEW_QUESTION,
) {
    /** 笔记降级项没有「原页面」可跳，也不参与复习进度。 */
    val isNoteFallback: Boolean get() = kind == KnowledgeItemKind.NOTE_FALLBACK
}

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
