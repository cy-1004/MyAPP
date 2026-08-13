package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.BudgetAlertStateEntity

@Dao
interface BudgetAlertStateDao {

    @Query("SELECT * FROM budget_alert_state WHERE cycle_start_epoch = :cycleStartEpoch")
    suspend fun getByCycleStart(cycleStartEpoch: Long): BudgetAlertStateEntity?

    /** REPLACE：唯一索引 cycle_start_epoch 保证一期只有一行去重状态。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetAlertStateEntity): Long
}
