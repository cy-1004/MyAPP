package com.myapp.feature.ledger.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.data.BudgetCategoryRepository
import com.myapp.feature.ledger.data.CategoryDraft
import com.myapp.feature.ledger.data.CategoryRepository
import com.myapp.feature.ledger.data.CategorySaveResult
import com.myapp.feature.ledger.data.parseAmountCents
import com.myapp.feature.ledger.ui.formatCentsToYuan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 编辑页的一次性结果：保存/删除成功要退页，重名只提示不退页。 */
sealed interface CategoryEditResult {
    data object Saved : CategoryEditResult
    data object Deleted : CategoryEditResult
    data object DuplicateName : CategoryEditResult
}

/**
 * 分类编辑页 VM（PRD 3.6 M5 Phase 3）。
 *
 * 与规则编辑页同一套约定：[Route.CategoryDetail] 的 id 为 0 表示新建。
 * 保留项（未分类）只允许改图标/颜色，名称输入框禁用、删除按钮隐藏。
 */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val repository: CategoryRepository,
    private val budgetCategoryRepository: BudgetCategoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: Long = savedStateHandle.toRoute<Route.CategoryDetail>().id

    private val _draft = MutableStateFlow(CategoryDraft(id = categoryId))
    val draft: StateFlow<CategoryDraft> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(categoryId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _results = Channel<CategoryEditResult>(Channel.BUFFERED)
    val results: Flow<CategoryEditResult> = _results.receiveAsFlow()

    init {
        if (categoryId != 0L) {
            viewModelScope.launch {
                val cap = budgetCategoryRepository.observeCaps().first()[categoryId]
                _draft.value = repository.loadDraft(categoryId).copy(
                    capYuanText = cap?.let { formatCentsToYuan(it) } ?: "",
                )
                _loaded.value = true
            }
        }
    }

    fun updateName(value: String) {
        // 超长直接不接受输入，比保存时才报错更早给到反馈。
        // 按 trim 后的长度判：保存时也是 trim 过再存，两处口径要一致
        if (value.trim().length > CategoryDraft.MAX_NAME_LENGTH) return
        _draft.update { it.copy(name = value) }
    }

    fun updateIcon(value: String) = _draft.update { it.copy(icon = value) }

    fun updateColor(value: String) = _draft.update { it.copy(color = value) }

    fun updateCap(value: String) {
        _draft.update { it.copy(capYuanText = value.filter { ch -> ch.isDigit() || ch == '.' }) }
    }

    fun save() {
        val current = _draft.value
        if (!current.canSave) return
        viewModelScope.launch {
            val result = when (val saved = repository.save(current)) {
                is CategorySaveResult.Saved -> {
                    val capCents = parseAmountCents(current.capYuanText) ?: 0L
                    budgetCategoryRepository.setCap(saved.id, capCents)
                    CategoryEditResult.Saved
                }
                CategorySaveResult.DuplicateName -> CategoryEditResult.DuplicateName
            }
            _results.send(result)
        }
    }

    fun delete() {
        val current = _draft.value
        if (current.isNew || current.isProtected) return
        viewModelScope.launch {
            repository.delete(current.id)
            _results.send(CategoryEditResult.Deleted)
        }
    }
}
