package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.myapp.core.database.model.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 交易 + 分类信息的查询结果（JOIN 后的投影）。
 * Room 按列名匹配字段：SQL 里用 `AS categoryName` 等别名把 snake_case 列映射到 camelCase 字段。
 */
data class TransactionWithCategory(
    val id: Long,
    val uuid: String,
    val amount: Long,
    val direction: String,
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val merchant: String?,
    val channel: String?,
    val occurredAt: Long,
    val status: String,
    val source: String,
    val note: String?,
)

/** 某个分类下的账目笔数（GROUP BY 投影）。分类管理页展示「N 笔账目」用。 */
data class CategoryUsage(
    val categoryId: Long,
    val count: Int,
)

/**
 * 某个分类在一个区间内的支出合计（GROUP BY 投影）。预算视图的分类排行用。
 *
 * 带上 name/icon/color 是为了一次查询直接出 UI 需要的全部字段，
 * 免得再对每个 categoryId 回查一次分类表。
 */
data class CategoryExpense(
    val categoryId: Long,
    val categoryName: String,
    val categoryIcon: String,
    val categoryColor: String,
    val totalAmount: Long,
    val count: Int,
)

/**
 * 某个自然月的支出合计（GROUP BY 投影）。统计页月度趋势用。
 * [yearMonth] 形如 "2026-08"，来自 SQLite `strftime('%Y-%m', ...)`。
 */
data class MonthlyExpense(
    val yearMonth: String,
    val totalAmount: Long,
)

@Dao
interface TransactionDao {

    /**
     * 各分类的账目笔数（未删除的账目）。分类管理页用它提示停用/删除会影响多少笔历史账目。
     * 没有账目的分类不会出现在结果里，调用方按 id 查 map 时用 0 兜底。
     */
    @Query(
        """
        SELECT category_id AS categoryId, COUNT(*) AS count
        FROM transaction_record
        WHERE deleted_at IS NULL
        GROUP BY category_id
        """,
    )
    fun observeCountByCategory(): Flow<List<CategoryUsage>>

    /**
     * 区间内按分类汇总的**支出**（仅 EXPENSE + 未删除），金额倒序。预算视图的分类排行用。
     *
     * JOIN 不带 `c.deleted_at IS NULL`：分类被软删后历史账目仍要能统计进来，
     * 否则删一个分类会让本期已花的分类明细和总额对不上（与列表页 JOIN 同口径）。
     */
    @Query(
        """
        SELECT t.category_id AS categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor,
               SUM(t.amount) AS totalAmount, COUNT(*) AS count
        FROM transaction_record t
        INNER JOIN category c ON c.id = t.category_id
        WHERE t.deleted_at IS NULL
          AND t.direction = 'EXPENSE'
          AND t.occurred_at >= :start
          AND t.occurred_at < :endExclusive
        GROUP BY t.category_id
        ORDER BY totalAmount DESC
        """,
    )
    fun observeCategoryExpensesInRange(start: Long, endExclusive: Long): Flow<List<CategoryExpense>>

    /**
     * 区间内按自然月汇总的**支出**（仅 EXPENSE + 未删除）。统计页月度趋势用。
     *
     * `strftime(..., 'localtime')`：occurred_at 存的是 UTC epochMilli，
     * 按本地时区分月才能跟 [com.myapp.core.common.time.AppTime] 的口径对上
     * （生产环境 AppTime.zone 恒为 systemDefault，两边天然一致）。
     * 没有支出的月份不会出现在结果里，Repository 补零。
     */
    @Query(
        """
        SELECT strftime('%Y-%m', occurred_at / 1000, 'unixepoch', 'localtime') AS yearMonth,
               SUM(amount) AS totalAmount
        FROM transaction_record
        WHERE deleted_at IS NULL
          AND direction = 'EXPENSE'
          AND occurred_at >= :start
          AND occurred_at < :endExclusive
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
        """,
    )
    fun observeMonthlyExpensesInRange(start: Long, endExclusive: Long): Flow<List<MonthlyExpense>>

    /**
     * 区间内未删除条目，按发生时间倒序。[endExclusive] 不含。
     * 用于「本期」「本月」等基于预算周期的查询。
     */
    @Query(
        """
        SELECT t.id, t.uuid, t.amount, t.direction, t.category_id AS categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor,
               t.merchant, t.channel, t.occurred_at AS occurredAt,
               t.status, t.source, t.note
        FROM transaction_record t
        INNER JOIN category c ON c.id = t.category_id
        WHERE t.deleted_at IS NULL
          AND t.occurred_at >= :start
          AND t.occurred_at < :endExclusive
        ORDER BY t.occurred_at DESC
        """,
    )
    fun observeInRangeWithCategory(start: Long, endExclusive: Long): Flow<List<TransactionWithCategory>>

    /**
     * 全部未删除条目，按发生时间倒序。列表页用。
     * 与 [observeInRangeWithCategory] 同 JOIN，只是不要 WHERE 区间。
     */
    @Query(
        """
        SELECT t.id, t.uuid, t.amount, t.direction, t.category_id AS categoryId,
               c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor,
               t.merchant, t.channel, t.occurred_at AS occurredAt,
               t.status, t.source, t.note
        FROM transaction_record t
        INNER JOIN category c ON c.id = t.category_id
        WHERE t.deleted_at IS NULL
        ORDER BY t.occurred_at DESC
        """,
    )
    fun observeAllWithCategory(): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transaction_record WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /**
     * 区间内支出总和（分）。仅统计 EXPENSE + 未删除。
     * 返回 Long 而非非空：无数据时 SUM 返回 null，Repository 用 ?: 0L 兜底。
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transaction_record
        WHERE deleted_at IS NULL
          AND direction = 'EXPENSE'
          AND occurred_at >= :start
          AND occurred_at < :endExclusive
        """,
    )
    fun observeExpenseSumInRange(start: Long, endExclusive: Long): Flow<Long>

    /** 一次性查询区间支出总和，供非观察场景（如 Snackbar 算剩余）使用。 */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transaction_record
        WHERE deleted_at IS NULL
          AND direction = 'EXPENSE'
          AND occurred_at >= :start
          AND occurred_at < :endExclusive
        """,
    )
    suspend fun sumExpenseInRange(start: Long, endExclusive: Long): Long

    /** 待确认笔数，首页卡片徽标用。Phase 1 永远 0（手工记账默认 CONFIRMED）。 */
    @Query(
        """
        SELECT COUNT(*) FROM transaction_record
        WHERE deleted_at IS NULL AND status = 'PENDING'
        """,
    )
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("UPDATE transaction_record SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Query("UPDATE transaction_record SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restore(id: Long, now: Long)

    /** 确认一笔待确认账目（自动记账解析落库后，用户确认/修改时调用）。 */
    @Query("UPDATE transaction_record SET status = 'CONFIRMED', updated_at = :now WHERE id = :id AND status = 'PENDING'")
    suspend fun confirmPending(id: Long, now: Long)

    /**
     * 自动记账去重：查 :since 之后是否已有相同原文的待确认条目。
     * 支付 App 偶尔会重复投递同一通知，无此检查会重复入账。
     */
    @Query(
        """
        SELECT id FROM transaction_record
        WHERE raw_text = :raw AND status = 'PENDING' AND deleted_at IS NULL
          AND occurred_at >= :since
        LIMIT 1
        """,
    )
    suspend fun getPendingByRawText(raw: String, since: Long): Long?
}
