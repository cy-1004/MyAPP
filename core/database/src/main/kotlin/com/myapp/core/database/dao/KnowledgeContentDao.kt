package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.KnowledgeContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeContentDao {

    @Query("SELECT * FROM knowledge_content WHERE source_id = :sourceId AND section_index = 0")
    fun observeBySourceId(sourceId: Long): Flow<KnowledgeContentEntity?>

    @Query("SELECT * FROM knowledge_content WHERE source_id = :sourceId AND section_index = 0")
    suspend fun getBySourceId(sourceId: Long): KnowledgeContentEntity?

    /**
     * 全文搜索：JOIN knowledge_content_fts 取命中的正文行，与 [NoteDao.search] 同一套模式。
     * FTS MATCH 的转义在 Repository 层完成。
     */
    @Query(
        """
        SELECT knowledge_content.* FROM knowledge_content
        JOIN knowledge_content_fts ON knowledge_content.id = knowledge_content_fts.rowid
        WHERE knowledge_content_fts MATCH :query
        """,
    )
    suspend fun search(query: String): List<KnowledgeContentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(content: KnowledgeContentEntity): Long

    /** 一个 source 目前只存一段正文，刷新时先清掉旧的再插入新的（整行替换，不做增量）。 */
    @Query("DELETE FROM knowledge_content WHERE source_id = :sourceId")
    suspend fun deleteBySourceId(sourceId: Long)
}
