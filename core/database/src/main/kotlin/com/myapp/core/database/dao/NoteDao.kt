package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /**
     * 全部未删除笔记，置顶优先 + 按更新时间倒序。
     *
     * 排序键简单：pinned 是 0/1，DESC 让置顶在前；updated_at DESC 让最近改过的在前。
     */
    @Query(
        """
        SELECT * FROM note
        WHERE deleted_at IS NULL
        ORDER BY pinned DESC, updated_at DESC
        """,
    )
    fun observeAll(): Flow<List<NoteEntity>>

    /**
     * 按标签筛选。tags 是逗号分隔字符串，用 LIKE 子串匹配。
     * 标签量级小，V1 不必上关联表；用户输入 `%' OR 1=1--` 这种注入串
     * 不会绕过 Room 的参数绑定（:tag 是绑定参数不是字符串拼接）。
     */
    @Query(
        """
        SELECT * FROM note
        WHERE deleted_at IS NULL AND tags LIKE '%' || :tag || '%'
        ORDER BY pinned DESC, updated_at DESC
        """,
    )
    fun observeByTag(tag: String): Flow<List<NoteEntity>>

    /**
     * 全文搜索：JOIN note_fts 取命中的 note 行。
     *
     * FTS MATCH 的转义在 Repository 层完成（短语匹配 `"...`），
     * DAO 只接受已转义的 query，保持 DAO 的纯数据访问角色。
     */
    @Query(
        """
        SELECT note.* FROM note
        JOIN note_fts ON note.id = note_fts.rowid
        WHERE note.deleted_at IS NULL AND note_fts MATCH :query
        ORDER BY note.pinned DESC, note.updated_at DESC
        """,
    )
    fun search(query: String): Flow<List<NoteEntity>>

    /** 全部已用过的标签（逗号分隔字符串聚合）。Repository 拆分去重后给 UI 做筛选 chip。 */
    @Query("SELECT DISTINCT tags FROM note WHERE deleted_at IS NULL AND tags != ''")
    fun observeAllTags(): Flow<List<String>>

    @Query("SELECT * FROM note WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    /** 不过滤 deleted_at：编辑页要能读到已软删的条目（虽然正常流程进不来）。 */
    @Query("SELECT * FROM note WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE note SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE note SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    @Query("UPDATE note SET pinned = :pinned, updated_at = :now WHERE id = :id")
    suspend fun togglePinned(id: Long, pinned: Boolean, now: Long)

    /** 随机挑一条未删除笔记，M7 知识池为空时的降级取材用。 */
    @Query("SELECT * FROM note WHERE deleted_at IS NULL ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandom(): NoteEntity?
}
