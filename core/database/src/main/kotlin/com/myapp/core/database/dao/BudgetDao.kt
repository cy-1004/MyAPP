package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * 全部预算版本，按生效时间正序。历史回顾用（PRD 3.6.2）。
     * 含已失效的行（effective_to 非空），这正是「历史」的来源。
     */
    @Query("SELECT * FROM budget ORDER BY effective_from ASC, id ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity): Long

    @Query("UPDATE budget SET effective_to = :now, updated_at = :now WHERE effective_to IS NULL")
    suspend fun closeCurrent(now: Long)

    /**
     * 用新预算取代当前预算，**旧行保留为历史版本**（effective_to 落时间戳）。
     *
     * 必须在一个事务里：两步之间崩溃会留下「一行历史都没关、或者一行生效的都没有」的库，
     * 后者会让 App 显示「还没设预算」，用户以为预算丢了。
     */
    @Transaction
    suspend fun replaceCurrent(next: BudgetEntity, now: Long) {
        closeCurrent(now)
        upsert(next)
    }
}
