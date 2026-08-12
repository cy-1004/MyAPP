package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.QuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    /**
     * 全部未删除疑问，按更新时间倒序。Repository 在内存里按 status 分组，
     * 一次查询比按 status 分三次查询少 IO，量级小分组代价可忽略。
     */
    @Query(
        """
        SELECT * FROM question
        WHERE deleted_at IS NULL
        ORDER BY updated_at DESC
        """,
    )
    fun observeAll(): Flow<List<QuestionEntity>>

    /** 按标签筛选，与 [NoteDao.observeByTag] 同口径（LIKE 子串匹配）。 */
    @Query(
        """
        SELECT * FROM question
        WHERE deleted_at IS NULL AND tags LIKE '%' || :tag || '%'
        ORDER BY updated_at DESC
        """,
    )
    fun observeByTag(tag: String): Flow<List<QuestionEntity>>

    /**
     * 全文搜索：JOIN question_fts 取命中的 question 行，与 [NoteDao.search] 同一套模式。
     *
     * FTS MATCH 的转义在 Repository 层完成，DAO 只接受已转义的 query。
     */
    @Query(
        """
        SELECT question.* FROM question
        JOIN question_fts ON question.id = question_fts.rowid
        WHERE question.deleted_at IS NULL AND question_fts MATCH :query
        ORDER BY question.updated_at DESC
        """,
    )
    fun search(query: String): Flow<List<QuestionEntity>>

    /** 全部已用过的标签，Repository 拆分去重后给 UI 做筛选 chip。 */
    @Query("SELECT DISTINCT tags FROM question WHERE deleted_at IS NULL AND tags != ''")
    fun observeAllTags(): Flow<List<String>>

    /** 随机一条待解决疑问，首页卡片用。ORDER BY RANDOM() 在这量级性能足够。 */
    @Query(
        """
        SELECT * FROM question
        WHERE status = 'OPEN' AND deleted_at IS NULL
        ORDER BY RANDOM()
        LIMIT 1
        """,
    )
    fun observeRandomPending(): Flow<QuestionEntity?>

    @Query("SELECT * FROM question WHERE id = :id")
    fun observeById(id: Long): Flow<QuestionEntity?>

    /** 不过滤 deleted_at：编辑页要能读到已软删的条目（虽然正常流程进不来）。 */
    @Query("SELECT * FROM question WHERE id = :id")
    suspend fun getById(id: Long): QuestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(question: QuestionEntity): Long

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("UPDATE question SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE question SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)
}
