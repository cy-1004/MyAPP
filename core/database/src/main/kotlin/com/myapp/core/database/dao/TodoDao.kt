package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.TodoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /**
     * 未完成 + 截止时间早于 [before] 的条目，逾期项置顶。
     *
     * 「今日」与「最近 7 天」两个视图共用这一条查询，只是传入不同的 [before]——
     * 视图差异属于业务语义，放在 Repository 里表达，不在 DAO 里堆同构 SQL。
     *
     * 排序依次为：逾期优先 → 有期限优先 → 优先级高的在前 → 截止时间早的在前。
     * `due_at IS NULL` 在 SQLite 里求值为 0/1，用它把无期限项沉到底部。
     */
    @Query(
        """
        SELECT * FROM todo
        WHERE deleted_at IS NULL
          AND done = 0
          AND (due_at IS NULL OR due_at < :before)
        ORDER BY
          CASE WHEN due_at IS NOT NULL AND due_at < :now THEN 0 ELSE 1 END,
          due_at IS NULL,
          priority DESC,
          due_at ASC
        """,
    )
    fun observeUndoneBefore(now: Long, before: Long): Flow<List<TodoEntity>>

    /**
     * [observeUndoneBefore] 的一次性版本。桌面小组件在 provideGlance 里取快照用
     * （Glance 不是观察式 UI，每次 updateAll 重跑 provideGlance 拿最新数据）。
     */
    @Query(
        """
        SELECT * FROM todo
        WHERE deleted_at IS NULL
          AND done = 0
          AND (due_at IS NULL OR due_at < :before)
        ORDER BY
          CASE WHEN due_at IS NOT NULL AND due_at < :now THEN 0 ELSE 1 END,
          due_at IS NULL,
          priority DESC,
          due_at ASC
        """,
    )
    suspend fun getUndoneBefore(now: Long, before: Long): List<TodoEntity>

    /** 今日（含逾期）未完成条目总数，小组件「还有 N 项」用。 */
    @Query(
        """
        SELECT COUNT(*) FROM todo
        WHERE deleted_at IS NULL AND done = 0
          AND (due_at IS NULL OR due_at < :before)
        """,
    )
    suspend fun countUndoneBefore(before: Long): Int

    /** 区间内已完成条目数，小组件区分「全部完成」与「今天没安排」用。 */
    @Query(
        """
        SELECT COUNT(*) FROM todo
        WHERE deleted_at IS NULL AND done = 1
          AND done_at >= :start AND done_at < :endExclusive
        """,
    )
    suspend fun countDoneInRange(start: Long, endExclusive: Long): Int

    /** 全部未完成，不限截止时间。 */
    @Query(
        """
        SELECT * FROM todo
        WHERE deleted_at IS NULL AND done = 0
        ORDER BY
          CASE WHEN due_at IS NOT NULL AND due_at < :now THEN 0 ELSE 1 END,
          due_at IS NULL,
          priority DESC,
          due_at ASC
        """,
    )
    fun observeActive(now: Long): Flow<List<TodoEntity>>

    /**
     * 已完成，按完成时间倒序。
     * 加 LIMIT 是因为这个视图只用于「回顾最近做了什么」，
     * 全量加载对长期使用的库没有意义，还会拖慢首帧。
     */
    @Query(
        """
        SELECT * FROM todo
        WHERE deleted_at IS NULL AND done = 1
        ORDER BY done_at DESC
        LIMIT 200
        """,
    )
    fun observeCompleted(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todo WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: Long): Flow<TodoEntity?>

    @Query("SELECT * FROM todo WHERE id = :id")
    suspend fun getById(id: Long): TodoEntity?

    @Query("SELECT COUNT(*) FROM todo WHERE deleted_at IS NULL AND done = 0")
    fun observeActiveCount(): Flow<Int>

    /** 有截止时间的未完成项，用于开机后重建提醒闹钟（PRD 9.3）。 */
    @Query(
        """
        SELECT * FROM todo
        WHERE deleted_at IS NULL AND done = 0 AND due_at IS NOT NULL AND due_at > :after
        """,
    )
    suspend fun getPendingReminders(after: Long): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity): Long

    @Update
    suspend fun update(todo: TodoEntity)

    @Query("UPDATE todo SET done = :done, done_at = :doneAt, updated_at = :now WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, doneAt: Long?, now: Long)

    /** 软删除：保留 tombstone，为将来同步留退路（PRD 4.7.7）。 */
    @Query("UPDATE todo SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    /** 撤销删除。软删除的另一半价值——误删可无损恢复。 */
    @Query("UPDATE todo SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)
}
