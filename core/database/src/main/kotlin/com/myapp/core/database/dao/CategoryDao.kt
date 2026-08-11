package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    /** 启用中的分类，按 sortOrder 升序。选择器只展示这些。 */
    @Query(
        """
        SELECT * FROM category
        WHERE deleted_at IS NULL AND is_active = 1
        ORDER BY sort_order ASC
        """,
    )
    fun observeActive(): Flow<List<CategoryEntity>>

    /** 全部分类（含停用），分类管理页用。 */
    @Query(
        """
        SELECT * FROM category
        WHERE deleted_at IS NULL
        ORDER BY sort_order ASC
        """,
    )
    fun observeAll(): Flow<List<CategoryEntity>>

    /** 一次性取全部分类（含停用），按 sortOrder 升序。整表重排时用，不需要 Flow。 */
    @Query(
        """
        SELECT * FROM category
        WHERE deleted_at IS NULL
        ORDER BY sort_order ASC
        """,
    )
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: Long): CategoryEntity?

    /** 按名查（含已停用），自动记账命中商户映射时反查分类 id 用。 */
    @Query("SELECT * FROM category WHERE name = :name AND deleted_at IS NULL LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    /** 「未分类」保留项（isProtected=1），自动记账没命中规则时落这里。 */
    @Query("SELECT * FROM category WHERE is_protected = 1 AND deleted_at IS NULL LIMIT 1")
    suspend fun getProtected(): CategoryEntity?

    /** 行数，CategorySeeder 用它判空决定是否灌种子。 */
    @Query("SELECT COUNT(*) FROM category")
    suspend fun count(): Int

    /** 同名分类（排除自身 id），分类管理页保存前查重用。名字是 LedgerWriter 的查找键，不能重复。 */
    @Query("SELECT * FROM category WHERE name = :name AND id != :excludeId AND deleted_at IS NULL LIMIT 1")
    suspend fun findDuplicateName(name: String, excludeId: Long): CategoryEntity?

    /** 当前最大排序值，新建分类排到末尾用。表空时返回 0。 */
    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM category WHERE deleted_at IS NULL")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE category SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE category SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)
}
