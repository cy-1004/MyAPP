package com.myapp.feature.feed.data

import android.content.Context
import android.net.Uri
import com.myapp.core.common.contract.NoteWriter
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.RssArticleDao
import com.myapp.core.database.dao.RssSourceDao
import com.myapp.core.database.model.RssArticleEntity
import com.myapp.core.database.model.RssArticleListRow
import com.myapp.core.database.model.RssSourceEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 领域模型：与数据库实体分开，避免 UI 直接依赖表结构（同 KnowledgeSourceUi 约定）。 */
data class RssSourceUi(
    val id: Long,
    val url: String,
    val title: String,
    val groupName: String,
    val enabled: Boolean,
    val lastFetchAt: Long?,
)

/**
 * 列表用的轻量模型：**没有 `content`**。
 * 与 [RssArticleUi]（详情页用，带正文）分开，理由见 `RssArticleListRow` 的说明——
 * 列表一个字都用不到正文，查出来就是白读几 MB。
 */
data class RssArticleListItem(
    val id: Long,
    val sourceId: Long,
    val sourceTitle: String,
    val link: String,
    val title: String,
    val summary: String,
    val coverImageUrl: String?,
    val publishedAt: Long,
    val isRead: Boolean,
    val isFavorite: Boolean,
)

data class RssArticleUi(
    val id: Long,
    val sourceId: Long,
    val sourceTitle: String,
    val link: String,
    val title: String,
    val summary: String,
    val content: String?,
    val coverImageUrl: String?,
    val publishedAt: Long,
    val isRead: Boolean,
    val isFavorite: Boolean,
)

/** 编辑页草稿。id 为 0 表示新建，与 KnowledgeSourceDraft 同一套约定。 */
data class RssSourceDraft(
    val id: Long = 0L,
    val url: String = "",
    val title: String = "",
    val groupName: String = "",
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = url.isNotBlank()
}

/**
 * 文章列表筛选模式（PRD 3.9：全部/按分组/未读/已收藏/按订阅源）。
 *
 * [Source] 比 [Group] 细一档：分组是用户给若干订阅源起的类别名，
 * 而实际看资讯时更常想的是「只看这一个源今天发了什么」。两者都保留，不互相替代。
 */
sealed interface RssFilter {
    data object All : RssFilter
    data object Unread : RssFilter
    data object Favorite : RssFilter
    data class Group(val name: String) : RssFilter
    data class Source(val sourceId: Long) : RssFilter
}

/** OPML 导入结果：新增几个、因为 URL 已存在跳过几个（PRD 3.9）。 */
data class RssImportResult(val added: Int, val skipped: Int)

/**
 * RSS 订阅仓库（PRD 3.9）。
 *
 * 刻意不用 WorkManager 做周期性后台拉取——M6 那次已经在真机上验证过 ColorOS 等 ROM
 * 会冻结周期性 WorkManager 任务（PRD 9.3，见 `KnowledgeExtractWorker` 的注释），M8 沿用
 * 同样的取舍：只在「打开资讯页」「下拉刷新」两个一次性触发点调用 [refreshAll]，
 * 不追求 PRD 原文的「后台定时」，这个裁剪已记录在交接文档「未完成」表。
 *
 * 拉取用 core:network 提供的单例 OkHttpClient；XML 解析交给纯函数 [RssFeedParser]，
 * 网络失败/解析失败只影响这一个源，不中断其它源的刷新（同 KnowledgeExtractWorker
 * 「提取失败不影响已有缓存」的取舍）。
 */
@Singleton
class RssRepository @Inject constructor(
    private val sourceDao: RssSourceDao,
    private val articleDao: RssArticleDao,
    private val okHttpClient: OkHttpClient,
    private val noteWriter: NoteWriter,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    fun observeSources(): Flow<List<RssSourceUi>> =
        sourceDao.observeAll().map { list -> list.map { it.toUi() } }

    /**
     * 列表查询。[limit] 由调用方按滚动进度递增（见 RssArticleListViewModel），
     * 不再一次性把全部文章读出来。
     */
    fun observeArticles(filter: RssFilter, limit: Int): Flow<List<RssArticleListItem>> =
        articleDao.observeArticles(
            onlyUnread = filter is RssFilter.Unread,
            onlyFavorite = filter is RssFilter.Favorite,
            groupName = (filter as? RssFilter.Group)?.name,
            sourceId = (filter as? RssFilter.Source)?.sourceId,
            limit = limit,
        ).map { rows -> attachSourceTitles(rows) }

    /** 当前筛选下的总条数，用于判断是否还能继续加载。 */
    fun observeArticleCount(filter: RssFilter): Flow<Int> =
        articleDao.observeArticleCount(
            onlyUnread = filter is RssFilter.Unread,
            onlyFavorite = filter is RssFilter.Favorite,
            groupName = (filter as? RssFilter.Group)?.name,
            sourceId = (filter as? RssFilter.Source)?.sourceId,
        )

    /** 文章详情页用，带上来源标题；favorite/read 状态变更时随 Flow 自动刷新。 */
    fun observeArticle(id: Long): Flow<RssArticleUi?> = articleDao.observeById(id).map { article ->
        article?.let { attachSourceTitles(listOf(it)).firstOrNull() }
    }

    /** 首页卡片「最新 3 条未读」（PRD 3.9）。 */
    fun observeLatestUnread(limit: Int = 3): Flow<List<RssArticleUi>> =
        articleDao.observeLatestUnread(limit).map { attachSourceTitles(it) }

    suspend fun loadDraft(id: Long): RssSourceDraft = withContext(io) {
        if (id == 0L) return@withContext RssSourceDraft()
        val entity = sourceDao.getById(id) ?: return@withContext RssSourceDraft()
        RssSourceDraft(id = entity.id, url = entity.url, title = entity.title, groupName = entity.groupName)
    }

    /**
     * 新建或更新，返回条目 id。新建成功后立刻拉一次——但不等它跑完再返回：
     * 网络请求可能要好几秒甚至超时，保存这个动作应该立刻响应，抓取结果由列表的
     * Flow 自然刷新出来即可（同 KnowledgeRepository.save「enqueue 后立即返回」的取舍，
     * 只是这里没有 WorkManager，用 appScope.launch 达到同样的效果）。
     */
    suspend fun save(draft: RssSourceDraft): Long = withContext(io) {
        val now = AppTime.now()
        val url = draft.url.trim()
        val title = draft.title.trim().ifBlank { url }
        val id = if (draft.isNew) {
            sourceDao.upsert(
                RssSourceEntity(
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
            val existing = sourceDao.getById(draft.id) ?: return@withContext draft.id
            sourceDao.update(
                existing.copy(url = url, title = title, groupName = draft.groupName.trim(), updatedAt = now),
            )
            draft.id
        }
        appScope.launch { fetchOne(id) }
        id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean): Unit = withContext(io) {
        val existing = sourceDao.getById(id) ?: return@withContext
        sourceDao.update(existing.copy(enabled = enabled, updatedAt = AppTime.now()))
    }

    suspend fun delete(id: Long): Unit = withContext(io) {
        sourceDao.softDelete(id, AppTime.now())
    }

    suspend fun restore(id: Long): Unit = withContext(io) {
        sourceDao.restore(id, AppTime.now())
    }

    /** 与相邻订阅源交换位置，与 KnowledgeRepository.move 同一套「整表重排成 1..n」策略。 */
    suspend fun move(id: Long, delta: Int): Unit = withContext(io) {
        val ordered = sourceDao.getAll()
        val reordered = reorderIds(ordered.map { it.id }, id, delta) ?: return@withContext
        val now = AppTime.now()
        val byId = ordered.associateBy { it.id }
        reordered.forEachIndexed { index, sourceId ->
            val entity = byId[sourceId] ?: return@forEachIndexed
            val newOrder = index + 1
            if (entity.sortOrder != newOrder) sourceDao.update(entity.copy(sortOrder = newOrder, updatedAt = now))
        }
    }

    suspend fun setRead(id: Long, isRead: Boolean): Unit = withContext(io) {
        articleDao.updateReadState(id, isRead)
    }

    suspend fun setFavorite(id: Long, isFavorite: Boolean): Unit = withContext(io) {
        articleDao.updateFavorite(id, isFavorite)
    }

    /** 「存为笔记」（PRD 3.9）：标题 + 链接 + 摘要一键存入 M3，不依赖 :feature:note。 */
    suspend fun saveAsNote(articleId: Long): Long? = withContext(io) {
        val article = articleDao.getById(articleId) ?: return@withContext null
        val content = buildString {
            appendLine(article.title)
            appendLine()
            if (article.summary.isNotBlank()) {
                appendLine(article.summary)
                appendLine()
            }
            append(article.link)
        }
        noteWriter.createNote(content = content, tags = listOf("资讯"))
    }

    /** 刷新全部启用中的订阅源（并发拉取，互不阻塞），随后清理过期文章。阅读页/下拉刷新用。 */
    suspend fun refreshAll(): Unit = withContext(io) {
        coroutineScope {
            sourceDao.getAllEnabled().map { source -> async { fetchAndStore(source) } }.awaitAll()
        }
        articleDao.deleteStale(AppTime.now() - STALE_THRESHOLD_MILLIS)
    }

    suspend fun fetchOne(id: Long): Unit = withContext(io) {
        val source = sourceDao.getById(id) ?: return@withContext
        fetchAndStore(source)
    }

    /** 导出全部未删除订阅源为 OPML 文本（PRD 3.9），写文件这一步交给调用方（要拿 Uri）。 */
    suspend fun exportOpml(): String = withContext(io) {
        RssOpml.export(sourceDao.getAll().map { it.toUi() })
    }

    /**
     * 导入 OPML：按 URL 去重（已存在的订阅源直接跳过，不覆盖），新增的立刻各自
     * `appScope.launch` 拉一次（同 [save] 的「不阻塞保存动作」取舍）。
     * 解析失败（不是合法 OPML，或 Uri 已经失效读不到）整体按 0 新增 0 跳过处理，不抛给调用方——
     * [uri] 只在这一次 suspend 调用内使用，不跨协程边界持有，避免 Activity 销毁后 Uri 失效。
     */
    suspend fun importOpml(uri: Uri): RssImportResult = withContext(io) {
        val entries = runCatching {
            context.contentResolver.openInputStream(uri)?.use(RssOpml::parse) ?: emptyList()
        }.getOrElse { return@withContext RssImportResult(0, 0) }
        val existingUrls = sourceDao.getAll().map { it.url }.toMutableSet()
        var added = 0
        var skipped = 0
        val now = AppTime.now()
        entries.forEach { entry ->
            if (entry.url.isBlank() || entry.url in existingUrls) {
                skipped++
                return@forEach
            }
            existingUrls += entry.url
            val id = sourceDao.upsert(
                RssSourceEntity(
                    uuid = UUID.randomUUID().toString(),
                    url = entry.url,
                    title = entry.title.ifBlank { entry.url },
                    groupName = entry.groupName,
                    sortOrder = sourceDao.maxSortOrder() + 1,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            appScope.launch { fetchOne(id) }
            added++
        }
        RssImportResult(added, skipped)
    }

    private suspend fun fetchAndStore(source: RssSourceEntity) {
        val now = AppTime.now()
        val parsed = runCatching { fetch(source.url) }.getOrNull()
        sourceDao.updateLastFetchAt(source.id, now)
        if (parsed == null) return
        val feedTitle = parsed.title?.trim()
        if (!feedTitle.isNullOrBlank()) {
            sourceDao.fillTitleIfMatchesUrl(source.id, feedTitle, now)
        }
        val articles = parsed.articles
            .filter { it.link.isNotBlank() || it.guid.isNotBlank() }
            .map { article ->
                RssArticleEntity(
                    sourceId = source.id,
                    guid = article.guid.ifBlank { article.link },
                    link = article.link,
                    title = article.title,
                    summary = article.summary,
                    content = article.content,
                    coverImageUrl = article.coverImageUrl,
                    publishedAt = article.publishedAt ?: now,
                    fetchedAt = now,
                )
            }
        if (articles.isNotEmpty()) articleDao.insertAll(articles)
    }

    private fun fetch(url: String): ParsedFeed {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("empty body")
            return body.byteStream().use(RssFeedParser::parse)
        }
    }

    private suspend fun attachSourceTitles(articles: List<RssArticleEntity>): List<RssArticleUi> {
        if (articles.isEmpty()) return emptyList()
        val sources = sourceDao.getAll().associateBy { it.id }
        return articles.mapNotNull { article ->
            val source = sources[article.sourceId] ?: return@mapNotNull null
            article.toUi(source.title)
        }
    }

    @JvmName("attachSourceTitlesToRows")
    private suspend fun attachSourceTitles(rows: List<RssArticleListRow>): List<RssArticleListItem> {
        if (rows.isEmpty()) return emptyList()
        val sources = sourceDao.getAll().associateBy { it.id }
        return rows.mapNotNull { row ->
            val source = sources[row.sourceId] ?: return@mapNotNull null
            RssArticleListItem(
                id = row.id,
                sourceId = row.sourceId,
                sourceTitle = source.title,
                link = row.link,
                title = row.title,
                summary = row.summary,
                coverImageUrl = row.coverImageUrl,
                publishedAt = row.publishedAt,
                isRead = row.isRead,
                isFavorite = row.isFavorite,
            )
        }
    }

    private fun RssSourceEntity.toUi() = RssSourceUi(
        id = id,
        url = url,
        title = title,
        groupName = groupName,
        enabled = enabled,
        lastFetchAt = lastFetchAt,
    )

    private fun RssArticleEntity.toUi(sourceTitle: String) = RssArticleUi(
        id = id,
        sourceId = sourceId,
        sourceTitle = sourceTitle,
        link = link,
        title = title,
        summary = summary,
        content = content,
        coverImageUrl = coverImageUrl,
        publishedAt = publishedAt,
        isRead = isRead,
        isFavorite = isFavorite,
    )

    companion object {
        private val STALE_THRESHOLD_MILLIS = TimeUnit.DAYS.toMillis(30)
    }
}

/**
 * 把 [id] 在 [ids] 里挪动 [delta] 位，返回新顺序；已在边界时返回 null 表示不用写库。
 * 与 `KnowledgeRepository.reorderIds` 同一套纯函数实现，[RssRepositoryTest] 钉死。
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
