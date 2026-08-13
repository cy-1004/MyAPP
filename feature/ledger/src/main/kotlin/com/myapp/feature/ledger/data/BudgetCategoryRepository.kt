package com.myapp.feature.ledger.data

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.BudgetCategoryDao
import com.myapp.core.database.model.BudgetCategoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 分类预算上限仓库（PRD 3.6.2）。
 *
 * 与 [BudgetRepository] 分开：分类上限不做历史版本化（没有"近 12 期分类回顾"这个需求），
 * 是简单的「一个分类一行、原地更新」，两者的生命周期不一样，合到一个类里没有共享价值。
 */
@Singleton
class BudgetCategoryRepository @Inject constructor(
    private val dao: BudgetCategoryDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** categoryId -> 上限（分）。没设上限的分类不在这个 map 里。 */
    fun observeCaps(): Flow<Map<Long, Long>> =
        dao.observeAll().map { list -> list.associate { it.categoryId to it.capCents } }

    /** [capCents] <= 0 视为清除——分类预算本来就是可选的，跟 [BudgetRepository.setBudget] 的必填不同。 */
    suspend fun setCap(categoryId: Long, capCents: Long): Unit = withContext(io) {
        if (capCents <= 0L) {
            dao.deleteByCategoryId(categoryId)
            return@withContext
        }
        val now = AppTime.now()
        dao.upsert(
            BudgetCategoryEntity(
                categoryId = categoryId,
                capCents = capCents,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun clearCap(categoryId: Long): Unit = withContext(io) {
        dao.deleteByCategoryId(categoryId)
    }
}
