package com.myapp.feature.ledger.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.feature.ledger.data.CategoryRepository
import com.myapp.feature.ledger.data.ManagedCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 列表行：分类本身 + 该分类下的账目笔数（提示停用/删除会影响多少历史账目）。
 *
 * [isFirst]/[isLast] 是**在同一父分类下的兄弟范围内**算的（PRD 3.6.1「最多两级」）--
 * 顶级分类只跟其他顶级分类比较，子分类只跟同一父分类下的其他子分类比较，
 * 与 [com.myapp.feature.ledger.data.CategoryRepository.move] 的移动范围保持一致，
 * 否则按钮能点但一移动就发现挪到了不相关的分类中间。
 */
data class CategoryRow(
    val category: ManagedCategory,
    val transactionCount: Int,
    val isFirst: Boolean,
    val isLast: Boolean,
    /** 顶级分类下面有没有子分类：决定要不要显示「添加子分类」入口、能不能删除。 */
    val hasChildren: Boolean,
    /** 是不是子分类：决定列表里要不要缩进显示。 */
    val isChild: Boolean,
)

data class CategoryListState(
    val rows: List<CategoryRow> = emptyList(),
    val loaded: Boolean = false,
) {
    val active: List<CategoryRow> get() = rows.filter { it.category.isActive }
    val inactive: List<CategoryRow> get() = rows.filterNot { it.category.isActive }
}

/** 一次性事件：删除后弹可撤销提示（与规则列表、纪念日列表同一套约定）。 */
data class CategoryUndoDeleteEvent(val id: Long, val name: String)

@HiltViewModel
class CategoryListViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {

    val state: StateFlow<CategoryListState> =
        combine(repository.observeAll(), repository.observeUsage()) { categories, usage ->
            CategoryListState(rows = buildCategoryRows(categories, usage), loaded = true)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CategoryListState(),
        )

    private val _undoEvents = Channel<CategoryUndoDeleteEvent>(Channel.BUFFERED)
    val undoEvents: Flow<CategoryUndoDeleteEvent> = _undoEvents.receiveAsFlow()

    fun setActive(id: Long, active: Boolean) {
        viewModelScope.launch { repository.setActive(id, active) }
    }

    fun delete(row: CategoryRow) {
        // hasChildren 拦在这里只是双保险：UI 侧已经不给有子分类的行挂侧滑删除了
        if (row.category.isProtected || row.hasChildren) return
        viewModelScope.launch {
            repository.delete(row.category.id)
            _undoEvents.send(CategoryUndoDeleteEvent(row.category.id, row.category.name))
        }
    }

    fun undoDelete(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    /** [delta] = -1 上移 / +1 下移。边界处 Repository 会直接忽略。 */
    fun move(id: Long, delta: Int) {
        viewModelScope.launch { repository.move(id, delta) }
    }
}

/**
 * 把扁平分类列表按父子关系组织成渲染顺序（PRD 3.6.1「最多两级」）：
 * 每个顶级分类后面紧跟着它的子分类（各自按 sortOrder 排序），子分类标 [CategoryRow.isChild]
 * 供列表缩进显示。[CategoryRow.isFirst]/[isLast] 在各自的兄弟范围内计算--
 * 顶级分类之间比，子分类只跟同一父分类下的兄弟比，与
 * [com.myapp.feature.ledger.data.CategoryRepository.move] 的移动范围一致。
 *
 * 纯函数，[CategoryRowsTest] 钉死。
 */
fun buildCategoryRows(categories: List<ManagedCategory>, usage: Map<Long, Int>): List<CategoryRow> {
    val childrenByParent = categories.filter { it.parentId != null }.groupBy { it.parentId }
    val topLevel = categories.filter { it.parentId == null }.sortedBy { it.sortOrder }

    val rows = mutableListOf<CategoryRow>()
    topLevel.forEachIndexed { index, parent ->
        val kids = childrenByParent[parent.id].orEmpty().sortedBy { it.sortOrder }
        rows += CategoryRow(
            category = parent,
            transactionCount = usage[parent.id] ?: 0,
            isFirst = index == 0,
            isLast = index == topLevel.lastIndex,
            hasChildren = kids.isNotEmpty(),
            isChild = false,
        )
        kids.forEachIndexed { childIndex, child ->
            rows += CategoryRow(
                category = child,
                transactionCount = usage[child.id] ?: 0,
                isFirst = childIndex == 0,
                isLast = childIndex == kids.lastIndex,
                hasChildren = false,
                isChild = true,
            )
        }
    }
    return rows
}
