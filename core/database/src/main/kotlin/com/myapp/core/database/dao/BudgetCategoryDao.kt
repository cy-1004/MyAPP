package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.myapp.core.database.model.BudgetCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetCategoryDao {

    @Query("SELECT * FROM budget_category")
    fun observeAll(): Flow<List<BudgetCategoryEntity>>

    /** REPLACE：唯一索引 category_id 保证一个分类只有一行上限。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetCategoryEntity): Long

    @Query("DELETE FROM budget_category WHERE category_id = :categoryId")
    suspend fun deleteByCategoryId(categoryId: Long)
}
