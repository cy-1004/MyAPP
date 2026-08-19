package com.myapp.feature.ledger.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.myapp.core.common.contract.LedgerWriter
import com.myapp.core.common.contract.WidgetRefreshNotifier
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.BudgetCycle
import com.myapp.core.database.dao.BudgetAlertStateDao
import com.myapp.core.database.dao.CategoryDao
import com.myapp.core.database.dao.TransactionDao
import com.myapp.core.database.dao.TransactionWithCategory
import com.myapp.core.database.model.BudgetAlertStateEntity
import com.myapp.core.database.model.TransactionEntity
import com.myapp.feature.ledger.notification.BudgetAlertNotifier
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 账目列表每页条数。比资讯的 50 小一档：账目行比文章行矮得多，
 * 一屏能塞十几条，50 条差不多是三四屏，够预取又不至于一次查太多。
 */
private const val TRANSACTION_PAGE_SIZE = 40

/**
 * 领域模型：一笔账目（带分类信息）。
 * 与 [TransactionEntity] 分开，避免 UI 直接依赖表结构。
 */
data class Transaction(
    val id: Long,
    val amountCents: Long,
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

/**
 * 某分类在一个区间内的支出汇总。预算视图的分类排行用。
 * [totalCents] 是该分类在区间内的支出合计（分），[count] 是笔数。
 * [capCents] 是该分类的预算上限（PRD 3.6.2），null = 没设，`CategoryExpenseRow` 据此切换
 * 「占总支出比例」与「相对上限的进度/超支标红」两种展示。
 */
data class CategoryExpenseItem(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val color: String,
    val totalCents: Long,
    val count: Int,
    val capCents: Long? = null,
)

/**
 * 分类领域模型。
 *
 * [parentId] 非空表示这是二级分类（PRD 3.6.1，最多两级）。
 * 记账编辑页的分类选择器（`CategoryPicker`）按它把子分类分组挂在各自的父分类下。
 */
data class Category(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val sortOrder: Int,
    val isProtected: Boolean,
    val parentId: Long? = null,
)

/** 编辑页的可变草稿。amountText 是字符串，校验在 [canSave] 里。 */
data class TransactionDraft(
    val id: Long = 0L,
    val amountText: String = "",
    val direction: String = TransactionDirection.EXPENSE,
    val categoryId: Long = 0L,
    val merchant: String = "",
    val channel: String? = null,
    val occurredAt: Long = AppTime.now(),
    val note: String = "",
    /** 加载时条目是否为 PENDING（自动记账落库）。保存后编辑页据此触发学习与通知撤除。 */
    val wasPending: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L

    /** canSave：金额能解析成正整数 + 分类已选。 */
    val canSave: Boolean
        get() = parseAmountCents(amountText) != null && categoryId > 0L
}

/** 记账方向。存字符串不存枚举（与 AnniversaryEntity.repeatType 同约定）。 */
object TransactionDirection {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
}

/** 账目状态。Phase 1 只有 CONFIRMED；PENDING 留给 Phase 3 自动记账。 */
object TransactionStatus {
    const val PENDING = "PENDING"
    const val CONFIRMED = "CONFIRMED"
}

/** 账目来源。 */
object TransactionSource {
    const val AUTO = "AUTO"
    const val MANUAL = "MANUAL"
}

/**
 * 把用户输入的金额文本解析成「分」Long。
 *
 * 容错「23.5」「23」「023」「.5」「23.」「23.50」等写法；
 * 上限 1 亿元（1_000_000_00 分）防止溢出；解析失败返回 null。
 *
 * 用 BigDecimal 而非 toDouble：浮点在金额场景不可接受（PRD 4.2）。
 */
fun parseAmountCents(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.matches(Regex("""^\d*\.?\d{0,2}$"""))) return null
    val dotIndex = trimmed.indexOf('.')
    val yuanStr: String
    val fenStr: String
    if (dotIndex < 0) {
        yuanStr = trimmed
        fenStr = ""
    } else {
        yuanStr = trimmed.substring(0, dotIndex)
        fenStr = trimmed.substring(dotIndex + 1)
    }
    if (yuanStr.isEmpty() && fenStr.isEmpty()) return null
    val yuan = if (yuanStr.isEmpty()) 0L else yuanStr.toLongOrNull() ?: return null
    val fen = when (fenStr.length) {
        0 -> 0L
        1 -> fenStr.toLong() * 10
        2 -> fenStr.toLong()
        else -> return null
    }
    val total = yuan * 100 + fen
    if (total <= 0L) return null
    if (total > 1_000_000_00L) return null  // 上限 1 亿元
    return total
}

/**
 * 记账仓库（PRD 3.6.1）。
 *
 * 同时实现 [LedgerWriter] 契约（PRD 4.7.4）--全局 FAB「记一笔」与未来的
 * NotificationListenerService 都通过 LedgerWriter 接口注入，不依赖 :feature:ledger 本身。
 *
 * 保存走读改写而非整体 REPLACE：uuid / createdAt / status / source 不属于表单，
 * 整体构造会把它们悄悄清掉（uuid 丢了将来同步就对不上）。参考 TodoRepository.save。
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
    private val budgetRepository: BudgetRepository,
    private val budgetCategoryRepository: BudgetCategoryRepository,
    private val alertStateDao: BudgetAlertStateDao,
    private val alertNotifier: BudgetAlertNotifier,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LedgerWriter {

    /** 今日支出列表，首页卡片与列表页「今天」分组共用。 */
    fun observeToday(): Flow<List<Transaction>> {
        val (start, endExclusive) = AppTime.todayRange().let { it.first to it.last }
        return observeInRange(start, endExclusive)
    }

    /** 启用中的分类列表，编辑页分类选择器用。 */
    fun observeActiveCategories(): Flow<List<Category>> =
        categoryDao.observeActive().map { list -> list.map { it.toDomain() } }

    /** 任意区间列表，按发生时间倒序。 */
    fun observeInRange(start: Long, endExclusive: Long): Flow<List<Transaction>> =
        transactionDao.observeInRangeWithCategory(start, endExclusive).map { list ->
            list.map { it.toDomain() }
        }

    /**
     * 列表分页流（PRD 4.5）。
     *
     * 改版前列表页走的是一条**没有任何上限**的全表查询，每加一笔账就把全表重查、
     * 重新映射、再在内存里按日期分组一遍。自动记账是天天在写的，这条迟早会咬人。
     * 那条全表查询（`observeAll` / `TransactionDao.observeAllWithCategory`）改完就没人用了，
     * 已一并删除--备份走的是 `BackupDao` 自己的 `SELECT *`，统计走 `observeInRange`，
     * 都不经过它。
     *
     * `enablePlaceholders = false`：留空位要先 COUNT 一次全表，而这个列表不显示总条数。
     */
    fun pagedTransactions(): Flow<PagingData<Transaction>> = Pager(
        config = PagingConfig(
            pageSize = TRANSACTION_PAGE_SIZE,
            maxSize = TRANSACTION_PAGE_SIZE * 4,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { transactionDao.pagingAllWithCategory() },
    ).flow.map { paging -> paging.map { it.toDomain() } }

    /**
     * 每天的支出合计（分），键是本地日期。列表的日期表头用。
     *
     * 分页之后一天的条目可能跨在两页之间，光看当前页加不出那天的合计，
     * 所以单独订阅一份按天聚合的结果--`GROUP BY` 在 SQLite 里做完，
     * 返回的行数是「天数」量级，比把分页数据凑齐便宜得多。
     *
     * 解析失败的行直接丢掉：这只是表头上的一个数字，
     * 为一个显示用的合计让整个列表崩掉不划算。
     */
    fun observeDailyExpenseTotals(): Flow<Map<LocalDate, Long>> =
        transactionDao.observeDailyExpenseTotals().map { rows ->
            rows.mapNotNull { row ->
                runCatching { LocalDate.parse(row.day) to row.totalCents }.getOrNull()
            }.toMap()
        }

    /** 任意区间的支出总和（分）。预算视图用自己算好的周期区间来查。 */
    fun observeExpenseSumInRange(start: Long, endExclusive: Long): Flow<Long> =
        transactionDao.observeExpenseSumInRange(start, endExclusive)

    /** 区间内按分类汇总的支出，金额倒序，带上各分类的预算上限。预算视图的分类排行用。 */
    fun observeCategoryExpenses(start: Long, endExclusive: Long): Flow<List<CategoryExpenseItem>> =
        combine(
            transactionDao.observeCategoryExpensesInRange(start, endExclusive),
            budgetCategoryRepository.observeCaps(),
        ) { list, caps ->
            list.map {
                CategoryExpenseItem(
                    categoryId = it.categoryId,
                    name = it.categoryName,
                    icon = it.categoryIcon,
                    color = it.categoryColor,
                    totalCents = it.totalAmount,
                    count = it.count,
                    capCents = caps[it.categoryId],
                )
            }
        }

    /** 按自然月分组的支出合计（分），key 形如 "2026-08"。统计页月度趋势用，配合 [StatisticsInsights.fillGaps]。 */
    fun observeMonthlyExpenses(start: Long, endExclusive: Long): Flow<Map<String, Long>> =
        transactionDao.observeMonthlyExpensesInRange(start, endExclusive).map { list ->
            list.associate { it.yearMonth to it.totalAmount }
        }

    /** 当前预算周期内的支出总和（分）。未设预算时返回 0。 */
    fun observeCurrentCycleSpending(cycleStartDay: Int): Flow<Long> {
        val range = BudgetCycle.currentCycleRange(cycleStartDay)
        return transactionDao.observeExpenseSumInRange(range.first, range.last + 1)
    }

    /** 一次性查询区间支出总和（分）。Snackbar 算「本期剩余」时用，避免再开一个 Flow 订阅。 */
    suspend fun sumExpenseInRange(start: Long, endExclusive: Long): Long = withContext(io) {
        transactionDao.sumExpenseInRange(start, endExclusive)
    }

    /** 待确认笔数，首页徽标用。Phase 1 永远 0。 */
    fun observePendingCount(): Flow<Int> = transactionDao.observePendingCount()

    suspend fun loadDraft(id: Long): TransactionDraft = withContext(io) {
        if (id == 0L) return@withContext TransactionDraft()
        val entity = transactionDao.getById(id) ?: return@withContext TransactionDraft()
        TransactionDraft(
            id = entity.id,
            amountText = formatCentsToYuanText(entity.amount),
            direction = entity.direction,
            categoryId = entity.categoryId,
            merchant = entity.merchant.orEmpty(),
            channel = entity.channel,
            occurredAt = entity.occurredAt,
            note = entity.note.orEmpty(),
            wasPending = entity.status == TransactionStatus.PENDING,
        )
    }

    /** 新建或更新，返回条目 id。 */
    suspend fun save(draft: TransactionDraft): Long = withContext(io) {
        val amount = parseAmountCents(draft.amountText)
            ?: error("canSave 校验过这里不应失败：${draft.amountText}")
        val now = AppTime.now()
        val id = if (draft.isNew) {
            transactionDao.upsert(
                TransactionEntity(
                    amount = amount,
                    direction = draft.direction,
                    categoryId = draft.categoryId,
                    merchant = draft.merchant.trim().ifBlank { null },
                    channel = draft.channel,
                    occurredAt = draft.occurredAt,
                    status = TransactionStatus.CONFIRMED,
                    source = TransactionSource.MANUAL,
                    note = draft.note.trim().ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            val existing = transactionDao.getById(draft.id) ?: return@withContext draft.id
            transactionDao.update(
                existing.copy(
                    amount = amount,
                    direction = draft.direction,
                    categoryId = draft.categoryId,
                    merchant = draft.merchant.trim().ifBlank { null },
                    channel = draft.channel,
                    occurredAt = draft.occurredAt,
                    note = draft.note.trim().ifBlank { null },
                    // 自动记账落库的 PENDING 条目，用户编辑保存 = 确认
                    status = if (existing.status == TransactionStatus.PENDING) {
                        TransactionStatus.CONFIRMED
                    } else {
                        existing.status
                    },
                    updatedAt = now,
                ),
            )
            draft.id
        }
        widgetRefreshNotifier.notifyDataChanged()
        if (draft.direction == TransactionDirection.EXPENSE) evaluateBudgetAlerts()
        id
    }

    suspend fun delete(id: Long): Unit = withContext(io) {
        transactionDao.softDelete(id, AppTime.now())
        widgetRefreshNotifier.notifyDataChanged()
    }

    suspend fun restore(id: Long): Unit = withContext(io) {
        transactionDao.restore(id, AppTime.now())
        widgetRefreshNotifier.notifyDataChanged()
    }

    // ---- LedgerWriter 契约实现（PRD 4.7.4）----
    // 供全局 FAB / 未来 NotificationListenerService 调用。
    // category 参数是分类名（"餐饮"）而非 id，因为外部调用方不知道内部 id。
    // 内部按名查 id，找不到时落「未分类」。
    override suspend fun recordExpense(
        amountCents: Long,
        merchant: String?,
        category: String?,
        occurredAt: Long,
        raw: String?,
        direction: String,
    ): Long = withContext(io) {
        val now = AppTime.now()
        val categoryId = resolveCategoryId(category)
        val id = transactionDao.upsert(
            TransactionEntity(
                amount = amountCents,
                direction = direction,
                categoryId = categoryId,
                merchant = merchant?.trim()?.ifBlank { null },
                channel = null,
                occurredAt = occurredAt,
                status = if (raw != null) TransactionStatus.PENDING else TransactionStatus.CONFIRMED,
                rawText = raw,
                source = if (raw != null) TransactionSource.AUTO else TransactionSource.MANUAL,
                note = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        widgetRefreshNotifier.notifyDataChanged()
        if (direction == TransactionDirection.EXPENSE) evaluateBudgetAlerts()
        id
    }

    /**
     * 预算预警评估（PRD 3.6.2）：[save]/[recordExpense] 是仅有的两个支出写入口，
     * 在这里统一判定比在两条 UI 路径（手工记账/自动记账）各写一遍更不容易漏。
     * 没设预算时直接跳过——没有分母就没有阈值可言。
     */
    private suspend fun evaluateBudgetAlerts() {
        val budget = budgetRepository.getCurrent() ?: return
        val cycle = BudgetCycle.currentCycleRange(budget.cycleStartDay)
        val spentCents = transactionDao.sumExpenseInRange(cycle.first, cycle.last + 1)
        val state = alertStateDao.getByCycleStart(cycle.first)
        val alertState = AlertState(
            notified80 = state?.notified80 ?: false,
            notified100 = state?.notified100 ?: false,
        )
        val triggered = BudgetAlertEvaluator.evaluate(spentCents, budget.totalAmountCents, alertState)
        if (triggered.isEmpty()) return

        val now = AppTime.now()
        alertStateDao.upsert(
            BudgetAlertStateEntity(
                id = state?.id ?: 0,
                cycleStartEpoch = cycle.first,
                notified80 = alertState.notified80 || triggered.contains(AlertKind.REACHED_80),
                notified100 = alertState.notified100 || triggered.contains(AlertKind.REACHED_100),
                updatedAt = now,
            ),
        )
        triggered.forEach { kind -> alertNotifier.post(kind, spentCents, budget.totalAmountCents) }
    }

    /** 一键确认待确认条目（不改任何字段，仅状态流转）。 */
    suspend fun confirm(id: Long): Unit = withContext(io) {
        transactionDao.confirmPending(id, AppTime.now())
        widgetRefreshNotifier.notifyDataChanged()
    }

    /** 按名查分类 id；找不到时落「未分类」（isProtected=1 那条）。 */
    private suspend fun resolveCategoryId(name: String?): Long {
        if (name.isNullOrBlank()) return uncategorizedId()
        return categoryDao.getByName(name.trim())?.id ?: uncategorizedId()
    }

    private suspend fun uncategorizedId(): Long =
        categoryDao.getProtected()?.id
            ?: error("未分类保留项缺失，CategorySeeder 未运行？")

    private fun TransactionWithCategory.toDomain() = Transaction(
        id = id,
        amountCents = amount,
        direction = direction,
        categoryId = categoryId,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        categoryColor = categoryColor,
        merchant = merchant,
        channel = channel,
        occurredAt = occurredAt,
        status = status,
        source = source,
        note = note,
    )

    private fun com.myapp.core.database.model.CategoryEntity.toDomain() = Category(
        id = id,
        name = name,
        icon = icon,
        color = color,
        sortOrder = sortOrder,
        isProtected = isProtected,
        parentId = parentId,
    )
}

/** 分转元文本，如 2390 -> "23.90"。仅供编辑页回填用，展示用 LedgerFormat.yuanText。 */
internal fun formatCentsToYuanText(cents: Long): String {
    val yuan = cents / 100
    val fen = cents % 100
    return if (fen == 0L) "$yuan"
    else if (fen < 10L) "$yuan.0$fen"
    else "$yuan.$fen"
}
