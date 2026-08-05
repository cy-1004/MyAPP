package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /**
     * 今日未完成 + 所有逾期项。
     * 逾期项一并返回并在 UI 置顶（PRD 3.3），否则过期待办会被用户忘掉。
     */
    @Query(
        """
        SELECT * FROM todo
        WHERE deleted_at IS NULL
          AND done = 0
          AND (due_at IS NULL OR due_at < :endOfToday)
        ORDER BY
          CASE WHEN due_at IS NOT NULL AND due_at < :now THEN 0 ELSE 1 END,
          priority DESC,
          due_at ASC
        """,
    )
    fun observeTodayAndOverdue(now: Long, endOfToday: Long): Flow<List<TodoEntity>>

    @Query(
        """
        SELECT * FROM todo
        WHERE deleted_at IS NULL AND done = 0
        ORDER BY priority DESC, due_at ASC
        """,
    )
    fun observeActive(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: Long): Flow<TodoEntity?>

    @Query("SELECT COUNT(*) FROM todo WHERE deleted_at IS NULL AND done = 0")
    fun observeActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("UPDATE todo SET done = :done, done_at = :doneAt, updated_at = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long)

    /** 软删除：保留 tombstone，为将来同步留退路（PRD 4.7.7）。 */
    @Query("UPDATE todo SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)
}
