package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.AnniversaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AnniversaryDao {

    /**
     * 全部有效纪念日。
     *
     * **刻意不在 SQL 里排序**：真正的排序键是「距下一次还有几天」，
     * 而农历换算与「每年重复」的下一次日期算不出 SQL 表达式。
     * 纪念日总量是几十条量级，在内存里算完再排完全无压力。
     */
    @Query("SELECT * FROM anniversary WHERE deleted_at IS NULL ORDER BY date ASC")
    fun observeAll(): Flow<List<AnniversaryEntity>>

    @Query("SELECT * FROM anniversary WHERE id = :id")
    suspend fun getById(id: Long): AnniversaryEntity?

    /** 全部有效纪念日的一次性快照，用于开机后重建提醒闹钟（PRD 9.3）。 */
    @Query("SELECT * FROM anniversary WHERE deleted_at IS NULL")
    suspend fun getAllActive(): List<AnniversaryEntity>

    @Query("SELECT COUNT(*) FROM anniversary WHERE deleted_at IS NULL")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AnniversaryEntity): Long

    @Update
    suspend fun update(item: AnniversaryEntity)

    /** 置顶是单选：先全部清零再置一条，避免出现两个置顶。 */
    @Query("UPDATE anniversary SET pinned = 0, updated_at = :now WHERE pinned = 1")
    suspend fun clearPinned(now: Long)

    @Query("UPDATE anniversary SET pinned = 1, updated_at = :now WHERE id = :id")
    suspend fun setPinned(id: Long, now: Long)

    @Query("UPDATE anniversary SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE anniversary SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)
}
