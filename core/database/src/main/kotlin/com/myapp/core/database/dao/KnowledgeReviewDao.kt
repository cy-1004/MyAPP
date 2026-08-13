package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.KnowledgeReviewEntity

@Dao
interface KnowledgeReviewDao {

    @Query("SELECT * FROM knowledge_review WHERE source_id = :sourceId AND section_index = :sectionIndex")
    suspend fun getBySourceId(sourceId: Long, sectionIndex: Int = 0): KnowledgeReviewEntity?

    /** 选择算法要一次性看到全部复习状态，按 source_id 建 Map 用。 */
    @Query("SELECT * FROM knowledge_review")
    suspend fun getAll(): List<KnowledgeReviewEntity>

    /** REPLACE：唯一索引 (source_id, section_index) 保证同一知识点只有一行。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: KnowledgeReviewEntity): Long
}
