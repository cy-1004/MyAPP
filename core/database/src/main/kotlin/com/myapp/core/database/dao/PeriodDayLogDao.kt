package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.PeriodDayLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PeriodDayLogDao {

    /**
     * 全部日记录，最近的在前。
     *
     * 不分页也不按月查：一天最多一条，且只有「有异常才记」的日子才会有行，
     * 常年下来也就几百行、每行几十字节，整表读比按月查更简单，也让月历翻页不用重新查库。
     */
    @Query("SELECT * FROM period_day_log ORDER BY log_date DESC")
    fun observeAll(): Flow<List<PeriodDayLogEntity>>

    @Query("SELECT * FROM period_day_log WHERE log_date = :date LIMIT 1")
    suspend fun getByDate(date: Long): PeriodDayLogEntity?

    /** 日期上有唯一索引，同一天再记就是覆盖。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: PeriodDayLogEntity): Long

    @Query("DELETE FROM period_day_log WHERE log_date = :date")
    suspend fun deleteByDate(date: Long)
}
