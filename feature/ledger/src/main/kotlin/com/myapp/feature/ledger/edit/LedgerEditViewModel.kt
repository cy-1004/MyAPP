package com.myapp.feature.ledger.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.data.Category
import com.myapp.feature.ledger.data.LedgerRepository
import com.myapp.feature.ledger.data.TransactionDraft
import com.myapp.feature.ledger.data.parseAmountCents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 编辑完成后要做什么。Saved 带金额（分），列表页用来拼「已记录 ￥X，本期剩余 ￥Y」。 */
sealed interface LedgerEditResult {
    data class Saved(val amountCents: Long) : LedgerEditResult
    data object Deleted : LedgerEditResult
}

@HiltViewModel
class LedgerEditViewModel @Inject constructor(
    private val repository: LedgerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** id 为 0 表示新建。 */
    private val ledgerId: Long = savedStateHandle.toRoute<Route.LedgerDetail>().id

    private val _draft = MutableStateFlow(TransactionDraft(id = ledgerId))
    val draft: StateFlow<TransactionDraft> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(ledgerId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** 启用中的分类，编辑页选择器用。 */
    val categories: StateFlow<List<Category>> = repository.observeActiveCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _results = Channel<LedgerEditResult>(Channel.BUFFERED)
    val results: Flow<LedgerEditResult> = _results.receiveAsFlow()

    init {
        if (ledgerId != 0L) {
            viewModelScope.launch {
                _draft.value = repository.loadDraft(ledgerId)
                _loaded.value = true
            }
        }
    }

    fun updateAmount(value: String) = update {
        // 只允许数字与最多一个小数点，过滤粘贴的非法字符
        val filtered = value.filter { it.isDigit() || it == '.' }
        val dotCount = filtered.count { it == '.' }
        val sanitized = if (dotCount > 1) {
            val firstDot = filtered.indexOf('.')
            filtered.substring(0, firstDot + 1) +
                filtered.substring(firstDot + 1).filter { it != '.' }
        } else {
            filtered
        }
        it.copy(amountText = sanitized)
    }

    fun updateDirection(value: String) = update { it.copy(direction = value) }

    fun updateCategory(value: Long) = update { it.copy(categoryId = value) }

    fun updateMerchant(value: String) = update { it.copy(merchant = value) }

    fun updateOccurredAt(value: Long) = update { it.copy(occurredAt = value) }

    fun updateNote(value: String) = update { it.copy(note = value) }

    fun save() {
        val current = _draft.value
        if (!current.canSave) return
        val amount = parseAmountCents(current.amountText) ?: return
        viewModelScope.launch {
            repository.save(current)
            _results.send(LedgerEditResult.Saved(amount))
        }
    }

    fun delete() {
        val id = _draft.value.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.delete(id)
            _results.send(LedgerEditResult.Deleted)
        }
    }

    private fun update(block: (TransactionDraft) -> TransactionDraft) {
        _draft.value = block(_draft.value)
    }
}
