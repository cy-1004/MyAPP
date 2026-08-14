package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.RssArticleEntity
import com.myapp.core.database.model.RssArticleListRow
import kotlinx.coroutines.flow.Flow

/** 列表首屏条数；滚到底再按这个步长追加（见 [RssArticleDao.observeArticles]）。 */
const val DEFAULT_PAGE_SIZE = 50

@Dao
interface RssArticleDao {

    /**
     * 列表查询用一条参数化 query 覆盖「全部/未读/收藏/按分组/按订阅源」五种模式（PRD 3.9），
     * 而不是五个近似重复的方法——`:onlyUnread`/`:onlyFavorite` 为 false 时对应条件直接放行，
     * `:groupName`/`:sourceId` 为 null 时不过滤。group_name 存在 rss_source 上，故 JOIN。
     *
     * 两个性能要点，改的时候不要退回去：
     * 1. **列是显式列出的，没有 `content`**——返回 [RssArticleListRow] 而不是整个实体。
     *    正文是列表用不到的大字段，查出来纯属浪费（详见 [RssArticleListRow] 的说明）。
     * 2. **必须带 `LIMIT`**。原来这条 query 无上限，5000+ 篇全量返回，
     *    每次进资讯页都要等好几秒。列表按需增量放大 limit（见 RssArticleListViewModel.loadMore）。
     */
    @Query(
        """
        SELECT rss_article.id AS id,
               rss_article.source_id AS source_id,
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
        LIMIT :limit
        """,
    )
    fun observeArticles(
        onlyUnread: Boolean = false,
        onlyFavorite: Boolean = false,
        groupName: String? = null,
        sourceId: Long? = null,
        limit: Int = DEFAULT_PAGE_SIZE,
    ): Flow<List<RssArticleListRow>>

    /**
     * 当前筛选条件下的总条数，用于判断「还有没有更多」。
     * 与上面的列表 query 条件保持一致，改一边记得改另一边。
     */
    @Query(
        """
        SELECT COUNT(*) FROM rss_article
        JOIN rss_source ON rss_article.source_id = rss_source.id
        WHERE rss_source.deleted_at IS NULL
          AND (:onlyUnread = 0 OR rss_article.is_read = 0)
          AND (:onlyFavorite = 0 OR rss_article.is_favorite = 1)
          AND (:groupName IS NULL OR rss_source.group_name = :groupName)
          AND (:sourceId IS NULL OR rss_article.source_id = :sourceId)
        """,
    )
    fun observeArticleCount(
        onlyUnread: Boolean = false,
        onlyFavorite: Boolean = false,
        groupName: String? = null,
        sourceId: Long? = null,
    ): Flow<Int>

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
