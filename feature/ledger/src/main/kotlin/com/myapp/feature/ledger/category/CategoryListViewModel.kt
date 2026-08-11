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

/** 列表行：分类本身 + 该分类下的账目笔数（提示停用/删除会影响多少历史账目）。 */
data class CategoryRow(
    val category: ManagedCategory,
    val transactionCount: Int,
    /** 是否已在列表首位/末位（决定上移/下移按钮是否可点）。 */
    val isFirst: Boolean,
    val isLast: Boolean,
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
            CategoryListState(
                rows = categories.mapIndexed { index, category ->
                    CategoryRow(
                        category = category,
                        transactionCount = usage[category.id] ?: 0,
                        isFirst = index == 0,
                        isLast = index == categories.lastIndex,
                    )
                },
                loaded = true,
            )
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
        if (row.category.isProtected) return
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
