package com.myapp.feature.ledger.data

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.BudgetDao
import com.myapp.core.database.model.BudgetEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 预算领域模型。Phase 1 只用 [cycleStartDay] + [totalAmountCents]；
 * [effectiveFrom] / [autoRollover] 留字段给 Phase 2 历史回顾。
 */
data class Budget(
    val id: Long,
    val cycleStartDay: Int,
    val totalAmountCents: Long,
    val effectiveFrom: Long,
    val autoRollover: Boolean,
)

/**
 * 预算仓库（PRD 3.6.2）。
 *
 * Phase 1 只用单行预算（effectiveTo = null）。Phase 2 加历史回顾时，
 * 旧预算 effectiveTo 落时间戳，新预算 effectiveFrom = now、effectiveTo = null。
 *
 * `setBudget` 的语义是「覆盖当前预算」：把现有 effectiveTo=null 的行设为
 * effectiveTo=now，再插一行新的。Phase 1 简化为 upsert 单行（id 固定为 1）。
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeCurrent(): Flow<Budget?> = dao.observeCurrent().map { it?.toDomain() }

    suspend fun getCurrent(): Budget? = withContext(io) {
        dao.getCurrent()?.toDomain()
    }

    /**
     * 设置当前预算。Phase 1：如果没有当前行就插一行，否则更新那一行。
     * Phase 2 改为「旧行 effectiveTo=now + 插新行」实现历史版本。
     */
    suspend fun setBudget(cycleStartDay: Int, totalAmountCents: Long): Unit = withContext(io) {
        require(cycleStartDay in 1..28) { "cycleStartDay must be 1..28, got $cycleStartDay" }
        require(totalAmountCents >= 0) { "totalAmountCents must be >= 0, got $totalAmountCents" }
        val now = AppTime.now()
        val existing = dao.getCurrent()
        if (existing == null) {
            dao.upsert(
                BudgetEntity(
                    cycleStartDay = cycleStartDay,
                    totalAmount = totalAmountCents,
                    effectiveFrom = now,
                    effectiveTo = null,
                    autoRollover = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            dao.upsert(
                existing.copy(
                    cycleStartDay = cycleStartDay,
                    totalAmount = totalAmountCents,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun BudgetEntity.toDomain() = Budget(
        id = id,
        cycleStartDay = cycleStartDay,
        totalAmountCents = totalAmount,
        effectiveFrom = effectiveFrom,
        autoRollover = autoRollover,
    )
}
