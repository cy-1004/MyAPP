package com.myapp.feature.ledger.unrecognized

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.common.contract.LedgerWriter
import com.myapp.feature.ledger.data.LedgerPrefsStore
import com.myapp.feature.ledger.data.UnrecognizedItem
import com.myapp.feature.ledger.data.parseAmountCents
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 未识别队列（PRD 3.6.1 兜底）。
 *
 * 自动记账解析失败的支付通知原文都在这里，绝不静默丢弃。两种出路：
 * 补录（用户填金额/商户 → 落一条 CONFIRMED + MANUAL 账目）或忽略（确认为无关通知）。
 * 自定义规则「保存为新规则」留到 Phase 3 与规则编辑器一起做。
 */
@HiltViewModel
class UnrecognizedViewModel @Inject constructor(
    private val prefs: LedgerPrefsStore,
    private val ledgerWriter: LedgerWriter,
) : ViewModel() {

    val items: StateFlow<List<UnrecognizedItem>> = prefs.unrecognized.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /**
     * 补录：以用户填写的金额/商户落一条账目（分类默认「未分类」，保存后可再改），
     * 并从队列移除。发生时间用原通知时间，保证归到正确日期分组。
     */
    fun recordManual(item: UnrecognizedItem, amountText: String, merchant: String) {
        val cents = parseAmountCents(amountText) ?: return
        viewModelScope.launch {
            ledgerWriter.recordExpense(
                amountCents = cents,
                merchant = merchant.trim().ifBlank { null },
                category = null, // 落「未分类」，用户可进编辑页再改
                occurredAt = item.occurredAt,
            )
            prefs.removeUnrecognized(item.id)
        }
    }

    /** 忽略：确认为无关通知，从队列移除。 */
    fun dismiss(id: Long) {
        viewModelScope.launch { prefs.removeUnrecognized(id) }
    }
}
