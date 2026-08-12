package com.myapp.feature.knowledge.data

import com.myapp.core.common.contract.KnowledgeItem
import com.myapp.core.common.contract.KnowledgeSource
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.KnowledgeContentDao
import com.myapp.core.database.dao.KnowledgeSourceDao
import com.myapp.core.database.model.KnowledgeContentEntity
import com.myapp.core.database.model.KnowledgeSourceEntity
import com.myapp.feature.knowledge.extract.KnowledgeExtractionScheduler
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 提取状态。stored 是数据库里的字符串值，加新状态不用迁移（与 QuestionStatus 同口径）。 */
enum class KnowledgeFetchStatus(val stored: String) {
    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    LOGIN_REQUIRED("LOGIN_REQUIRED");

    companion object {
        fun from(stored: String?): KnowledgeFetchStatus =
            entries.firstOrNull { it.stored == stored } ?: PENDING
    }
}

/** 领域模型：与数据库实体分开，避免 UI 直接依赖表结构。 */
data class KnowledgeSourceUi(
    val id: Long,
    val url: String,
    val title: String,
    val groupName: String,
    val pinned: Boolean,
    val enabled: Boolean,
    val fetchStatus: KnowledgeFetchStatus,
    val lastFetchAt: Long?,
)

/** 已缓存的正文，阅读页断网降级 / 首页摘要用。 */
data class KnowledgeContentUi(
    val contentText: String,
    val fetchedAt: Long,
)

/** 搜索命中：正文匹配到的知识源 + 命中片段。 */
data class KnowledgeSearchHit(
    val sourceId: Long,
    val sourceTitle: String,
    val snippet: String,
)

/** 编辑页草稿。id 为 0 表示新建，与 Note/Question/Category 同一套约定。 */
data class KnowledgeSourceDraft(
    val id: Long = 0L,
    val url: String = "",
    val title: String = "",
    val groupName: String = "",
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = url.isNotBlank()
}

/**
 * 知识源仓库（PRD 3.7）。
 *
 * 实现跨 feature 契约 [KnowledgeSource]（定义在 core:common，PRD 4.7.4）：
 * `refresh()` 给启用中的知识源各排一次提取任务（enqueue 后立即返回，不等提取完成——
 * 提取本身是分钟级的后台 WebView 操作，契约调用方不该被卡住）；`pickDailyKnowledge()`
 * 是 M7（每日知识推送，未落地）的占位实现，V1 只在知识池（=置顶）里随机挑一条已缓存正文。
 *
 * 保存走读改写而非整体 REPLACE（与 CategoryRepository/NoteRepository 同一套路）。
 */
@Singleton
class KnowledgeRepository @Inject constructor(
    private val sourceDao: KnowledgeSourceDao,
    private val contentDao: KnowledgeContentDao,
    private val extractionScheduler: KnowledgeExtractionScheduler,
    @IoDispatcher private val io: CoroutineDispatcher,
) : KnowledgeSource {

    fun observeAll(): Flow<List<KnowledgeSourceUi>> =
        sourceDao.observeAll().map { list -> list.map { it.toUi() } }

    /** 置顶且启用的知识源，首页卡片用。 */
    fun observePinned(): Flow<List<KnowledgeSourceUi>> =
        sourceDao.observePinned().map { list -> list.map { it.toUi() } }

    fun observeById(id: Long): Flow<KnowledgeSourceUi?> =
        sourceDao.observeById(id).map { it?.toUi() }

    fun observeContent(sourceId: Long): Flow<KnowledgeContentUi?> =
        contentDao.observeBySourceId(sourceId).map { it?.toUi() }

    /** 全文搜索正文，命中结果带上知识源标题方便列表展示与跳转。 */
    suspend fun search(query: String): List<KnowledgeSearchHit> = withContext(io) {
        val hits = contentDao.search(escapeFtsQuery(query))
        if (hits.isEmpty()) return@withContext emptyList()
        val sources = sourceDao.getAll().associateBy { it.id }
        hits.mapNotNull { content ->
            val source = sources[content.sourceId] ?: return@mapNotNull null
            KnowledgeSearchHit(
                sourceId = source.id,
                sourceTitle = source.title,
                snippet = content.contentText.take(SNIPPET_LENGTH),
            )
        }
    }

    suspend fun loadDraft(id: Long): KnowledgeSourceDraft = withContext(io) {
        if (id == 0L) return@withContext KnowledgeSourceDraft()
        val entity = sourceDao.getById(id) ?: return@withContext KnowledgeSourceDraft()
        KnowledgeSourceDraft(
            id = entity.id,
            url = entity.url,
            title = entity.title,
            groupName = entity.groupName,
        )
    }

    /**
     * 新建或更新，返回条目 id。新建成功后立刻排一次提取任务
     * （用户点保存就是在等结果，不用等下一次手动刷新）。
     */
    suspend fun save(draft: KnowledgeSourceDraft): Long = withContext(io) {
        val now = AppTime.now()
        val url = draft.url.trim()
        val title = draft.title.trim().ifBlank { url }
        val id = if (draft.isNew) {
            sourceDao.upsert(
                KnowledgeSourceEntity(
                    uuid = UUID.randomUUID().toString(),
                    url = url,
                    title = title,
                    groupName = draft.groupName.trim(),
                    sortOrder = sourceDao.maxSortOrder() + 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            // 读改写：uuid / createdAt / sortOrder / pinned / enabled / fetchStatus 不属于表单
            val existing = sourceDao.getById(draft.id) ?: return@withContext draft.id
            sourceDao.update(
                existing.copy(
                    url = url,
                    title = title,
                    groupName = draft.groupName.trim(),
                    updatedAt = now,
                ),
            )
            draft.id
        }
        extractionScheduler.enqueue(id)
        id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Unit = withContext(io) {
        val existing = sourceDao.getById(id) ?: return@withContext
        sourceDao.update(existing.copy(enabled = enabled, updatedAt = AppTime.now()))
    }

    suspend fun setPinned(id: Long, pinned: Boolean): Unit = withContext(io) {
        val existing = sourceDao.getById(id) ?: return@withContext
        sourceDao.update(existing.copy(pinned = pinned, updatedAt = AppTime.now()))
    }

    suspend fun delete(id: Long): Unit = withContext(io) {
        sourceDao.softDelete(id, AppTime.now())
    }

    suspend fun restore(id: Long): Unit = withContext(io) {
        sourceDao.restore(id, AppTime.now())
    }

    /** 手动刷新：重新加载正文 + 重排提取任务。阅读页刷新按钮用。 */
    fun refreshOne(id: Long) {
        extractionScheduler.enqueue(id)
    }

    /**
     * 与相邻知识源交换位置（[delta] = -1 上移 / +1 下移）。
     * 与 [com.myapp.feature.ledger.data.CategoryRepository.move] 同一套「整表重排成 1..n」策略。
     */
    suspend fun move(id: Long, delta: Int): Unit = withContext(io) {
        val ordered = sourceDao.getAll()
        val reordered = reorderIds(ordered.map { it.id }, id, delta) ?: return@withContext
        val now = AppTime.now()
        val byId = ordered.associateBy { it.id }
        reordered.forEachIndexed { index, sourceId ->
            val entity = byId[sourceId] ?: return@forEachIndexed
            val newOrder = index + 1
            if (entity.sortOrder != newOrder) {
                sourceDao.update(entity.copy(sortOrder = newOrder, updatedAt = now))
            }
        }
    }

    /** 实现 [KnowledgeSource]：给全部启用中的知识源各排一次提取任务，enqueue 后立即返回。 */
    override suspend fun refresh(): Unit = withContext(io) {
        sourceDao.getAll().filter { it.enabled }.forEach { extractionScheduler.enqueue(it.id) }
    }

    /**
     * 实现 [KnowledgeSource]：M7（每日知识推送）占位实现——
     * 知识池（=置顶知识源）里随机挑一条已缓存正文。M7 落地前只有这一个消费方，
     * 不做「今天推过哪些」的去重逻辑，等 M7 真正需要时再补。
     */
    override suspend fun pickDailyKnowledge(): KnowledgeItem? = withContext(io) {
        val pinned = sourceDao.getAll().filter { it.pinned && it.enabled }
        if (pinned.isEmpty()) return@withContext null
        val source = pinned.random()
        val content = contentDao.getBySourceId(source.id) ?: return@withContext null
        KnowledgeItem(
            sourceId = source.id,
            sectionIndex = content.sectionIndex,
            title = source.title,
            summary = content.contentText.take(SNIPPET_LENGTH),
            url = source.url,
        )
    }

    /**
     * FTS MATCH 转义，与 NoteRepository/QuestionRepository 同一套做法：用户输入直接做 MATCH
     * 会把空格当 AND、把 `* : "` 当操作符。用 `"..."` 短语包裹，内部的 `"` 双写转义。
     */
    private fun escapeFtsQuery(query: String): String {
        val escaped = query.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun KnowledgeSourceEntity.toUi() = KnowledgeSourceUi(
        id = id,
        url = url,
        title = title,
        groupName = groupName,
        pinned = pinned,
        enabled = enabled,
        fetchStatus = KnowledgeFetchStatus.from(fetchStatus),
        lastFetchAt = lastFetchAt,
    )

    private fun KnowledgeContentEntity.toUi() = KnowledgeContentUi(
        contentText = contentText,
        fetchedAt = fetchedAt,
    )

    companion object {
        private const val SNIPPET_LENGTH = 120
    }
}

/**
 * 把 [id] 在 [ids] 里挪动 [delta] 位，返回新顺序；已在边界（无法再挪）时返回 null 表示不用写库。
 *
 * 纯函数，与 `com.myapp.feature.ledger.data.reorder` 同一套实现，[KnowledgeRepositoryTest] 钉死。
 */
fun reorderIds(ids: List<Long>, id: Long, delta: Int): List<Long>? {
    val from = ids.indexOf(id)
    if (from < 0) return null
    val to = from + delta
    if (to < 0 || to > ids.lastIndex) return null
    val mutable = ids.toMutableList()
    mutable.removeAt(from)
    mutable.add(to, id)
    return mutable
}
