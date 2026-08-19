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
 *
 * [parentId] 非空表示这是二级分类（PRD 3.6.1「支持自建子分类……最多两级」）。
 * 只有顶级分类（parentId == null）能被选作父分类，见 [resolveParentId]。
 */
data class ManagedCategory(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val isProtected: Boolean,
    val parentId: Long? = null,
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
    /** 上级分类 id；null = 顶级分类。新建子分类时由「添加子分类」入口预填。 */
    val parentId: Long? = null,
    /**
     * 这个分类底下有没有子分类。**只在加载已有分类时由 Repository 算好填入**，
     * 新建草稿恒为 false（新分类不可能已经有子分类）。
     * 编辑页据此禁用「所属分类」选择器--已经是父分类的不能再挂到别人底下，
     * 那样会出现三级。
     */
    val hasChildren: Boolean = false,
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
            parentId = entity.parentId,
            hasChildren = categoryDao.getAll().any { it.parentId == entity.id },
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
            val parentId = resolveParentId(
                requestedParentId = draft.parentId,
                selfId = null,
                isProtected = false,
                selfHasChildren = false,
                parentCandidate = draft.parentId?.let { categoryDao.getById(it) },
            )
            val id = categoryDao.upsert(
                CategoryEntity(
                    name = name,
                    icon = draft.icon,
                    color = draft.color,
                    sortOrder = categoryDao.maxSortOrder() + 1,
                    isActive = true,
                    isProtected = false,
                    parentId = parentId,
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
        val parentId = resolveParentId(
            requestedParentId = draft.parentId,
            selfId = existing.id,
            isProtected = existing.isProtected,
            selfHasChildren = categoryDao.getAll().any { it.parentId == existing.id },
            parentCandidate = draft.parentId?.let { categoryDao.getById(it) },
        )
        categoryDao.update(
            existing.copy(
                name = newName,
                icon = draft.icon,
                color = draft.color,
                parentId = parentId,
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

    /**
     * 软删除。保留项不可删；**有子分类的也不可删**--子分类不会被连带删掉
     * （它们可能各自有账目），留着会变成挂在一个「不存在」的父分类下面的孤儿。
     * 想删父分类，得先把子分类删掉或挪走。
     */
    suspend fun delete(id: Long): Unit = withContext(io) {
        val existing = categoryDao.getById(id) ?: return@withContext
        if (existing.isProtected) return@withContext
        if (categoryDao.getAll().any { it.parentId == id }) return@withContext
        categoryDao.softDelete(id, AppTime.now())
    }

    /** 撤销删除。 */
    suspend fun restore(id: Long): Unit = withContext(io) {
        categoryDao.restore(id, AppTime.now())
    }

    /**
     * 与相邻分类交换位置（[delta] = -1 上移 / +1 下移）。
     *
     * **只在同一父分类下的兄弟之间移动**：顶级分类只跟其他顶级分类比较，
     * 子分类只跟同一父分类下的其他子分类比较--不然「上移」能把一个子分类
     * 移到跟它毫不相关的顶级分类中间，管理页的分组显示就乱了。
     *
     * 排序值可能有重复或空洞（种子灌的是 1..10，用户删过再加就会跳号），
     * 所以不做 `sortOrder ± 1` 的算术，而是取当前兄弟列表算出目标下标再
     * **整组重排**成 1..n——只有这样才能保证任何历史数据下拖动都稳定。
     * 不同父分类下的兄弟组各自独立编号，`sort_order` 出现跨组重复值是正常的，
     * 反正没有任何查询会跨组直接按 sort_order 排序（都是先按 parentId 分组再排）。
     */
    suspend fun move(id: Long, delta: Int): Unit = withContext(io) {
        val all = categoryDao.getAll()
        val target = all.firstOrNull { it.id == id } ?: return@withContext
        val siblings = all.filter { it.parentId == target.parentId }
        val reordered = reorder(siblings.map { it.id }, id, delta) ?: return@withContext
        val now = AppTime.now()
        val byId = siblings.associateBy { it.id }
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
        parentId = parentId,
    )
}

/**
 * 校验并解析要落库的 `parentId`（PRD 3.6.1「支持自建子分类……最多两级」）。
 *
 * 四条规则，任一条不满足就退回顶级（`null`）而不是报错--这个函数是最后一道防线，
 * UI 层的选择器本来就只会给出合法选项，这里的校验是防御性的：
 * 1. 保留项（未分类）永远是顶级，忽略传入值--它必须稳定，自动记账没命中规则要有地方落。
 * 2. 不能把自己设成自己的父分类。
 * 3. **自己已经有子分类的不能再选父分类**--那样会出现三级（自己的孩子变成孙子）。
 * 4. 父分类候选必须本身是顶级（`parentCandidate.parentId == null`）--
 *    否则同样会出现三级（父分类的父分类变成祖父）。
 *
 * 纯函数，[CategoryParentTest] 钉死。
 */
fun resolveParentId(
    requestedParentId: Long?,
    selfId: Long?,
    isProtected: Boolean,
    selfHasChildren: Boolean,
    parentCandidate: CategoryEntity?,
): Long? {
    if (isProtected) return null
    if (requestedParentId == null) return null
    if (requestedParentId == selfId) return null
    if (selfHasChildren) return null
    if (parentCandidate == null || parentCandidate.parentId != null) return null
    return requestedParentId
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
