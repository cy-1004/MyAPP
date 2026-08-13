package com.myapp.feature.ledger.data

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.CategoryDao
import com.myapp.core.database.dao.TransactionDao
import com.myapp.core.database.model.CategoryEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 分类管理页的领域模型（比 [Category] 多带 isActive，管理页要展示已停用项）。
 */
data class ManagedCategory(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val isProtected: Boolean,
)

/**
 * 分类编辑页的草稿。id 为 0 表示新建（与待办/规则同一套约定）。
 *
 * [capYuanText] 是分类预算上限（元，可留空 = 不设），跟 name/icon/color 一样在这个草稿里，
 * 但保存时走独立的 [BudgetCategoryRepository]——那张表跟 category 表是两个不同的生命周期，
 * 合到 [CategoryRepository.save] 里会让一个方法同时管两张表，职责不清。
 */
data class CategoryDraft(
    val id: Long = 0L,
    val name: String = "",
    val icon: String = DEFAULT_CATEGORY_ICON,
    val color: String = DEFAULT_CATEGORY_COLOR,
    val isActive: Boolean = true,
    val isProtected: Boolean = false,
    val capYuanText: String = "",
) {
    val isNew: Boolean get() = id == 0L

    /** 保存按钮启用条件：名称非空且不超长。图标/颜色都有默认值。 */
    val canSave: Boolean get() = name.isBlank().not() && name.trim().length <= MAX_NAME_LENGTH

    companion object {
        const val MAX_NAME_LENGTH = 10
    }
}

/** 新建分类的默认图标/颜色 key，与 [com.myapp.feature.ledger.ui.categoryIcon] 的映射表对应。 */
const val DEFAULT_CATEGORY_ICON = "other"
const val DEFAULT_CATEGORY_COLOR = "neutralGray"

/** 保存结果。重名要拦住：分类名是 [com.myapp.core.common.contract.LedgerWriter] 的查找键。 */
sealed interface CategorySaveResult {
    data class Saved(val id: Long) : CategorySaveResult
    /** 已有同名分类（含已停用的），不允许保存。 */
    data object DuplicateName : CategorySaveResult
}

/**
 * 分类仓库（PRD 3.6 M5 Phase 3）。
 *
 * 三条不变式，改这个文件前先读：
 *
 * 1. **保留项（isProtected）不可删、不可停用、不可改名**。自动记账没命中分类时
 *    `LedgerRepository.resolveCategoryId` 会落到它，它消失了自动记账就会 error。
 * 2. **删除一律软删**。`TransactionDao` 的 JOIN 是 `INNER JOIN category ON c.id = t.category_id`，
 *    **不带** `c.deleted_at IS NULL` 条件，所以软删后历史账目仍能显示原分类名；
 *    真删（DELETE）会让那些账目从列表里整行消失。
 * 3. **改名要同步商户学习映射**。`LedgerPrefsStore.merchantCategoryMap` 的 value 存的是分类
 *    **名**，不改的话自动记账会按旧名查不到分类，静默落到「未分类」。
 *
 * 保存走读改写而非整体 REPLACE：uuid / createdAt / sortOrder / isProtected 不属于表单
 * （与 TodoRepository.save、LedgerRepository.save 同一套路）。
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val prefs: LedgerPrefsStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 全部未删除分类（含已停用），按 sortOrder 升序。管理页用。 */
    fun observeAll(): Flow<List<ManagedCategory>> =
        categoryDao.observeAll().map { list -> list.map { it.toManaged() } }

    /** 分类 id -> 账目笔数。没有账目的分类不在 map 里，读的时候用 0 兜底。 */
    fun observeUsage(): Flow<Map<Long, Int>> =
        transactionDao.observeCountByCategory().map { list ->
            list.associate { it.categoryId to it.count }
        }

    suspend fun loadDraft(id: Long): CategoryDraft = withContext(io) {
        if (id == 0L) return@withContext CategoryDraft()
        val entity = categoryDao.getById(id) ?: return@withContext CategoryDraft()
        CategoryDraft(
            id = entity.id,
            name = entity.name,
            icon = entity.icon,
            color = entity.color,
            isActive = entity.isActive,
            isProtected = entity.isProtected,
        )
    }

    /** 新建或更新。重名（含已停用分类）返回 [CategorySaveResult.DuplicateName]，不落库。 */
    suspend fun save(draft: CategoryDraft): CategorySaveResult = withContext(io) {
        val name = draft.name.trim()
        if (categoryDao.findDuplicateName(name, draft.id) != null) {
            return@withContext CategorySaveResult.DuplicateName
        }
        val now = AppTime.now()
        if (draft.isNew) {
            val id = categoryDao.upsert(
                CategoryEntity(
                    name = name,
                    icon = draft.icon,
                    color = draft.color,
                    sortOrder = categoryDao.maxSortOrder() + 1,
                    isActive = true,
                    isProtected = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            return@withContext CategorySaveResult.Saved(id)
        }
        val existing = categoryDao.getById(draft.id)
            ?: return@withContext CategorySaveResult.Saved(draft.id)
        // 保留项只允许改图标/颜色，名字是自动记账的落点不能动
        val newName = if (existing.isProtected) existing.name else name
        categoryDao.update(
            existing.copy(
                name = newName,
                icon = draft.icon,
                color = draft.color,
                updatedAt = now,
            ),
        )
        if (newName != existing.name) {
            prefs.renameLearnedCategory(existing.name, newName)
        }
        CategorySaveResult.Saved(draft.id)
    }

    /** 停用/启用。停用后不再出现在记账编辑页的选择器里，历史账目不受影响。保留项不可停用。 */
    suspend fun setActive(id: Long, active: Boolean): Unit = withContext(io) {
        val existing = categoryDao.getById(id) ?: return@withContext
        if (existing.isProtected && !active) return@withContext
        categoryDao.update(existing.copy(isActive = active, updatedAt = AppTime.now()))
    }

    /** 软删除。保留项不可删。 */
    suspend fun delete(id: Long): Unit = withContext(io) {
        val existing = categoryDao.getById(id) ?: return@withContext
        if (existing.isProtected) return@withContext
        categoryDao.softDelete(id, AppTime.now())
    }

    /** 撤销删除。 */
    suspend fun restore(id: Long): Unit = withContext(io) {
        categoryDao.restore(id, AppTime.now())
    }

    /**
     * 与相邻分类交换位置（[delta] = -1 上移 / +1 下移）。
     *
     * 排序值可能有重复或空洞（种子灌的是 1..10，用户删过再加就会跳号），
     * 所以不做 `sortOrder ± 1` 的算术，而是取当前列表算出目标下标再**整表重排**成 1..n
     * ——只有这样才能保证任何历史数据下拖动都稳定。分类量级最多几十条，代价可忽略。
     */
    suspend fun move(id: Long, delta: Int): Unit = withContext(io) {
        val ordered = categoryDao.getAll()
        val reordered = reorder(ordered.map { it.id }, id, delta) ?: return@withContext
        val now = AppTime.now()
        val byId = ordered.associateBy { it.id }
        reordered.forEachIndexed { index, categoryId ->
            val entity = byId[categoryId] ?: return@forEachIndexed
            val newOrder = index + 1
            if (entity.sortOrder != newOrder) {
                categoryDao.update(entity.copy(sortOrder = newOrder, updatedAt = now))
            }
        }
    }

    private fun CategoryEntity.toManaged() = ManagedCategory(
        id = id,
        name = name,
        icon = icon,
        color = color,
        sortOrder = sortOrder,
        isActive = isActive,
        isProtected = isProtected,
    )
}

/**
 * 把 [id] 在 [ids] 里挪动 [delta] 位，返回新顺序；已在边界（无法再挪）时返回 null 表示不用写库。
 *
 * 纯函数，[CategoryRepositoryTest] 钉死。
 */
fun reorder(ids: List<Long>, id: Long, delta: Int): List<Long>? {
    val from = ids.indexOf(id)
    if (from < 0) return null
    val to = from + delta
    if (to < 0 || to > ids.lastIndex) return null
    val mutable = ids.toMutableList()
    mutable.removeAt(from)
    mutable.add(to, id)
    return mutable
}
