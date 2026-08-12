package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.RssArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RssArticleDao {

    /**
     * 列表查询用一条参数化 query 覆盖「全部/未读/收藏/按分组」四种模式（PRD 3.9），
     * 而不是四个近似重复的方法——`:onlyUnread`/`:onlyFavorite` 为 false 时对应条件直接放行，
     * `:groupName` 为 null 时不过滤分组。group_name 存在 rss_source 上，故 JOIN。
     */
    @Query(
        """
        SELECT rss_article.* FROM rss_article
        JOIN rss_source ON rss_article.source_id = rss_source.id
        WHERE rss_source.deleted_at IS NULL
          AND (:onlyUnread = 0 OR rss_article.is_read = 0)
          AND (:onlyFavorite = 0 OR rss_article.is_favorite = 1)
          AND (:groupName IS NULL OR rss_source.group_name = :groupName)
        ORDER BY rss_article.published_at DESC
        """,
    )
    fun observeArticles(
        onlyUnread: Boolean = false,
        onlyFavorite: Boolean = false,
        groupName: String? = null,
    ): Flow<List<RssArticleEntity>>

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
