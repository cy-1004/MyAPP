package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.PeriodRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDao {

    /** 全部记录，最近的在前。周期统计与日历都基于它。 */
    @Query("SELECT * FROM period_record WHERE deleted_at IS NULL ORDER BY start_date DESC")
    fun observeAll(): Flow<List<PeriodRecordEntity>>

    /**
     * 最近 [limit] 次，用于计算平均周期长度（PRD 3.2 取近 6 次）。
     * 注意要取 limit + 1 条才能算出 limit 个间隔。
     */
    @Query(
        """
        SELECT * FROM period_record
        WHERE deleted_at IS NULL
        ORDER BY start_date DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(limit: Int): List<PeriodRecordEntity>

    @Query("SELECT * FROM period_record WHERE deleted_at IS NULL ORDER BY start_date DESC LIMIT 1")
    suspend fun getLatest(): PeriodRecordEntity?

    @Query("SELECT * FROM period_record WHERE id = :id")
    suspend fun getById(id: Long): PeriodRecordEntity?

    /** 判断某天是否已有记录，防止同一天重复记两次开始。 */
    @Query("SELECT * FROM period_record WHERE deleted_at IS NULL AND start_date = :startDate LIMIT 1")
    suspend fun getByStartDate(startDate: Long): PeriodRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: PeriodRecordEntity): Long

    @Update
    suspend fun update(record: PeriodRecordEntity)

    @Query("UPDATE period_record SET end_date = :endDate, updated_at = :now WHERE id = :id")
    suspend fun setEndDate(id: Long, endDate: Long?, now: Long)

    @Query("UPDATE period_record SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE period_record SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)
}
