package com.myapp.feature.ledger.data

import com.myapp.core.common.contract.WidgetRefreshNotifier
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
 * 预算领域模型。[effectiveFrom] 是这个版本开始生效的时刻，
 * 历史回顾靠它把「某一期该用哪份预算」对上（见 [BudgetHistoryInsights]）。
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
 * **预算是有版本的**：每改一次预算，旧行 `effective_to` 落时间戳留作历史，
 * 再插一行新的（`effective_to = null` 即当前生效）。历史回顾（近 12 期柱状图）
 * 要知道「那一期当时的预算是多少」，只有攒着历史版本才答得出来。
 *
 * > 这里原本是 upsert 单行（id 固定为 1），改预算直接把旧值覆盖掉——
 * > 结果是历史预算**从来没有被记录过**，回顾功能永远做不出来。
 * > 2026-08-13 改成保留版本，所以历史只能从这天之后开始攒。
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val dao: BudgetDao,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeCurrent(): Flow<Budget?> = dao.observeCurrent().map { it?.toDomain() }

    /** 全部预算版本（含已失效的），按生效时间正序。历史回顾用。 */
    fun observeAll(): Flow<List<Budget>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getCurrent(): Budget? = withContext(io) {
        dao.getCurrent()?.toDomain()
    }

    /**
     * 设置当前预算：旧版本留作历史，新版本从此刻开始生效。
     *
     * **值没变时直接返回**，不产生新版本——用户点开编辑对话框看一眼就确认是很常见的操作，
     * 每次都插一行的话历史里会塞满一堆内容完全相同的版本，
     * 「这一期的预算是多少」还是答得出来，但历史记录本身就没法看了。
     */
    suspend fun setBudget(cycleStartDay: Int, totalAmountCents: Long): Unit = withContext(io) {
        require(cycleStartDay in 1..28) { "cycleStartDay must be 1..28, got $cycleStartDay" }
        require(totalAmountCents >= 0) { "totalAmountCents must be >= 0, got $totalAmountCents" }
        val now = AppTime.now()
        val existing = dao.getCurrent()
        if (existing != null &&
            existing.cycleStartDay == cycleStartDay &&
            existing.totalAmount == totalAmountCents
        ) {
            return@withContext
        }
        val next = BudgetEntity(
            cycleStartDay = cycleStartDay,
            totalAmount = totalAmountCents,
            effectiveFrom = now,
            effectiveTo = null,
            autoRollover = true,
            createdAt = now,
            updatedAt = now,
        )
        if (existing == null) {
            dao.upsert(next)
        } else {
            // 旧行关档 + 插新行，同一个事务里做，中途崩溃不会留下没有生效预算的库
            dao.replaceCurrent(next, now)
        }
        widgetRefreshNotifier.notifyDataChanged()
    }

    private fun BudgetEntity.toDomain() = Budget(
        id = id,
        cycleStartDay = cycleStartDay,
        totalAmountCents = totalAmount,
        effectiveFrom = effectiveFrom,
        autoRollover = autoRollover,
    )
}
