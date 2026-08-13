package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.KnowledgeSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeSourceDao {

    /**
     * 全部未删除知识源，按 sortOrder 升序。管理页用。
     *
     * 不按 pinned 提前——上移/下移是对 sortOrder 做整表重排（见
     * `KnowledgeRepository.move`/[getAll]），如果列表展示顺序跟这个基准不一致，
     * 点上移/下移会跳到跟视觉位置对不上的地方。置顶只用星标展示，不改变排序。
     */
    @Query(
        """
        SELECT * FROM knowledge_source
        WHERE deleted_at IS NULL
        ORDER BY sort_order ASC
        """,
    )
    fun observeAll(): Flow<List<KnowledgeSourceEntity>>

    /** 置顶且启用的知识源，首页卡片快捷入口用。 */
    @Query(
        """
        SELECT * FROM knowledge_source
        WHERE deleted_at IS NULL AND pinned = 1 AND enabled = 1
        ORDER BY sort_order ASC
        """,
    )
    fun observePinned(): Flow<List<KnowledgeSourceEntity>>

    /** 加入知识池且启用的知识源，M7 每日知识点挑选候选池用。 */
    @Query(
        """
        SELECT * FROM knowledge_source
        WHERE deleted_at IS NULL AND in_pool = 1 AND enabled = 1
        ORDER BY sort_order ASC
        """,
    )
    suspend fun getPool(): List<KnowledgeSourceEntity>

    /** 一次性取全部未删除知识源，按 sortOrder 升序。整表重排时用，不需要 Flow。 */
    @Query(
        """
        SELECT * FROM knowledge_source
        WHERE deleted_at IS NULL
        ORDER BY sort_order ASC
        """,
    )
    suspend fun getAll(): List<KnowledgeSourceEntity>

    @Query("SELECT * FROM knowledge_source WHERE id = :id")
    fun observeById(id: Long): Flow<KnowledgeSourceEntity?>

    /** 不过滤 deleted_at：编辑页要能读到已软删的条目（虽然正常流程进不来）。 */
    @Query("SELECT * FROM knowledge_source WHERE id = :id")
    suspend fun getById(id: Long): KnowledgeSourceEntity?

    /** 当前最大排序值，新建知识源排到末尾用。表空时返回 0。 */
    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM knowledge_source WHERE deleted_at IS NULL")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: KnowledgeSourceEntity): Long

    @Update
    suspend fun update(source: KnowledgeSourceEntity)

    @Query("UPDATE knowledge_source SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE knowledge_source SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query(
        "UPDATE knowledge_source SET fetch_status = :status, last_fetch_at = :now, updated_at = :now WHERE id = :id",
    )
    suspend fun updateFetchStatus(id: Long, status: String, now: Long)

    /** 只在标题当前为空时才写入抓到的正文标题，不覆盖用户自己填的标题。 */
    @Query(
        """
        UPDATE knowledge_source SET title = :title, updated_at = :now
        WHERE id = :id AND (title IS NULL OR title = '')
        """,
    )
    suspend fun fillTitleIfBlank(id: Long, title: String, now: Long)
}
