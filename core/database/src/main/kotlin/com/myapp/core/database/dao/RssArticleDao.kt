package com.myapp.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.RssArticleEntity
import com.myapp.core.database.model.RssPagedArticleRow
import kotlinx.coroutines.flow.Flow

/** 分页每页条数（`PagingConfig.pageSize`）。 */
const val DEFAULT_PAGE_SIZE = 50

@Dao
interface RssArticleDao {

    /**
     * 资讯列表查询（PRD 3.9 + 4.5）。
     *
     * 一条参数化 query 覆盖「全部/未读/收藏/按分组/按订阅源」五种模式，
     * 而不是五个近似重复的方法--`:onlyUnread`/`:onlyFavorite` 为 false 时对应条件直接放行，
     * `:groupName`/`:sourceId` 为 null 时不过滤。group_name 存在 rss_source 上，故 JOIN。
     *
     * 三个要点，改的时候不要退回去：
     * 1. **列是显式列出的，没有 `content`**--返回 [RssPagedArticleRow] 而不是整个实体。
     *    `content` 是抓回来的整篇正文，实测 5469 篇合计接近 8MB（PRD 4.13 云备份那节的实测数据），
     *    而列表一个字都用不到。查整行等于每次进资讯页都把 8MB 读出来、映射成对象再交给 Compose，
     *    这就是当年「每次进资讯页都要等好久」的原因。详情页要正文，走 `observeById`。
     * 2. **来源标题直接 JOIN 出来**（`rss_source.title AS source_title`）。
     *    别改回「先查列表、再查一次订阅源表建映射」--那套在分页下会退化成每篇文章查一次全表
     *    （`PagingData.map` 是逐条执行的，详见 [RssPagedArticleRow] 的说明）。
     * 3. **不写 `LIMIT`**。取多少由 `PagingConfig` 决定，SQL 里再写死会跟它打架。
     *    返回 `PagingSource` 而不是 Flow：Room 自己接 `InvalidationTracker`，
     *    表一变就让当前 PagingSource 失效、Paging 重新加载已持有的页。
     */
    @Query(
        """
        SELECT rss_article.id AS id,
               rss_article.source_id AS source_id,
               rss_source.title AS source_title,
               rss_article.link AS link,
               rss_article.title AS title,
               rss_article.summary AS summary,
               rss_article.cover_image_url AS cover_image_url,
               rss_article.published_at AS published_at,
               rss_article.is_read AS is_read,
               rss_article.is_favorite AS is_favorite
        FROM rss_article
        JOIN rss_source ON rss_article.source_id = rss_source.id
        WHERE rss_source.deleted_at IS NULL
          AND (:onlyUnread = 0 OR rss_article.is_read = 0)
          AND (:onlyFavorite = 0 OR rss_article.is_favorite = 1)
          AND (:groupName IS NULL OR rss_source.group_name = :groupName)
          AND (:sourceId IS NULL OR rss_article.source_id = :sourceId)
        ORDER BY rss_article.published_at DESC
        """,
    )
    fun pagingArticles(
        onlyUnread: Boolean = false,
        onlyFavorite: Boolean = false,
        groupName: String? = null,
        sourceId: Long? = null,
    ): PagingSource<Int, RssPagedArticleRow>

    /** 首页卡片「最新 3 条未读」（PRD 3.9）。 */
    @Query(
        """
        SELECT rss_article.* FROM rss_article
        JOIN rss_source ON rss_article.source_id = rss_source.id
        WHERE rss_source.deleted_at IS NULL AND rss_article.is_read = 0
        ORDER BY rss_article.published_at DESC
        LIMIT :limit
        """,
    )
    fun observeLatestUnread(limit: Int): Flow<List<RssArticleEntity>>

    @Query("SELECT * FROM rss_article WHERE id = :id")
    suspend fun getById(id: Long): RssArticleEntity?

    @Query("SELECT * FROM rss_article WHERE id = :id")
    fun observeById(id: Long): Flow<RssArticleEntity?>

    /** 按 (source_id, guid) 去重：已存在的 guid 直接忽略，不覆盖已读/收藏状态。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(articles: List<RssArticleEntity>): List<Long>

    @Query("UPDATE rss_article SET is_read = :isRead WHERE id = :id")
    suspend fun updateReadState(id: Long, isRead: Boolean)

    @Query("UPDATE rss_article SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    /** 清理策略（PRD 3.9）：非收藏文章保留 30 天，超期物理删除，收藏项永久保留不受影响。 */
    @Query("DELETE FROM rss_article WHERE is_favorite = 0 AND fetched_at < :cutoff")
    suspend fun deleteStale(cutoff: Long)

    @Query("DELETE FROM rss_article WHERE source_id = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)
}
