package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /**
     * 当前生效预算（effective_to IS NULL），取最新一行。
     * Phase 1 永远只有一行；Phase 2 加历史回顾时会有多行，取 id 最大那行。
     */
    @Query(
        """
        SELECT * FROM budget
        WHERE effective_to IS NULL
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    fun observeCurrent(): Flow<BudgetEntity?>

    @Query(
        """
        SELECT * FROM budget
        WHERE effective_to IS NULL
        ORDER BY id DESC
        LIMIT 1
        """,
    )
    suspend fun getCurrent(): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity): Long
}
