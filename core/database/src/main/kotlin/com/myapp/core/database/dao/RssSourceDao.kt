package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.RssSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RssSourceDao {

    /** 全部未删除订阅源，按 sortOrder 升序。与 [KnowledgeSourceDao.observeAll] 同一套约定。 */
    @Query(
        """
        SELECT * FROM rss_source
        WHERE deleted_at IS NULL
        ORDER BY sort_order ASC
        """,
    )
    fun observeAll(): Flow<List<RssSourceEntity>>

    @Query(
        """
        SELECT * FROM rss_source
        WHERE deleted_at IS NULL
        ORDER BY sort_order ASC
        """,
    )
    suspend fun getAll(): List<RssSourceEntity>

    /** 一次性取全部启用中的订阅源，刷新管线用。 */
    @Query(
        """
        SELECT * FROM rss_source
        WHERE deleted_at IS NULL AND enabled = 1
        ORDER BY sort_order ASC
        """,
    )
    suspend fun getAllEnabled(): List<RssSourceEntity>

    @Query("SELECT * FROM rss_source WHERE id = :id")
    fun observeById(id: Long): Flow<RssSourceEntity?>

    @Query("SELECT * FROM rss_source WHERE id = :id")
    suspend fun getById(id: Long): RssSourceEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM rss_source WHERE deleted_at IS NULL")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: RssSourceEntity): Long

    @Update
    suspend fun update(source: RssSourceEntity)

    @Query("UPDATE rss_source SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE rss_source SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query("UPDATE rss_source SET last_fetch_at = :now, updated_at = :now WHERE id = :id")
    suspend fun updateLastFetchAt(id: Long, now: Long)

    /**
     * 只在标题当前等于 URL（新建时留空的兜底值，见 RssRepository.save）时才写入订阅源
     * 自带的 `<title>`，不覆盖用户自己填的标题。同 KnowledgeSourceDao.fillTitleIfBlank
     * 的取舍，条件用「等于 URL」而不是「为空」——因为 RssSourceEntity.title 非空列，
     * 新建时留空会直接兜底成 url（同 KnowledgeRepository.save 的约定）。
     */
    @Query(
        """
        UPDATE rss_source SET title = :title, updated_at = :now
        WHERE id = :id AND title = url
        """,
    )
    suspend fun fillTitleIfMatchesUrl(id: Long, title: String, now: Long)
}
