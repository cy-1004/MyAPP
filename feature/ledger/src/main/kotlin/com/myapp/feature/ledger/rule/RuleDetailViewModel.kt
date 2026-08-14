package com.myapp.feature.ledger.rule

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.myapp.core.common.contract.LedgerWriter
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.data.CustomRuleDraft
import com.myapp.feature.ledger.data.LedgerPrefsStore
import com.myapp.feature.ledger.data.RuleRepository
import com.myapp.feature.ledger.data.UnrecognizedItem
import com.myapp.feature.ledger.notification.PaymentParseResult
import com.myapp.feature.ledger.notification.PaymentParser
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

enum class RuleEditResult { Saved, Deleted }

/**
 * 规则编辑页 VM（PRD 3.6.1 Phase 3）。
 *
 * 两种进入路径：
 * - 普通新建/编辑（[Route.RuleDetail] 的 id 决定新建 vs 编辑）
 * - 从未识别队列跳来（[Route.RuleDetail.presetUnrecognizedId] 非 0）：
 *   预填 previewText 让用户在原通知上调关键词；保存成功后用新规则 parse 该未识别项，
 *   parse 成功就落账并出队，parse 失败仅存规则不落账（用户可后续手动补录）。
 */
@HiltViewModel
class RuleDetailViewModel @Inject constructor(
    private val repository: RuleRepository,
    private val prefs: LedgerPrefsStore,
    private val ledgerWriter: LedgerWriter,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route: Route.RuleDetail = savedStateHandle.toRoute()
    private val ruleId: Long = route.id
    private val presetUnrecognizedId: Long = route.presetUnrecognizedId

    private val _draft = MutableStateFlow(CustomRuleDraft(id = ruleId))
    val draft: StateFlow<CustomRuleDraft> = _draft.asStateFlow()

    /** 预览输入框文本；默认填未识别项原文（若从不识别队列跳来）。 */
    private val _previewText = MutableStateFlow("")
    val previewText: StateFlow<String> = _previewText.asStateFlow()

    /**
     * 预览用的通知**标题**。
     *
     * 必须与正文分开，因为规则匹配的第一步就是「标题包含全部关键词」。
     * 之前预览把标题写死成空串，于是只要规则填了标题关键词，预览就永远显示「未匹配」——
     * 哪怕规则完全正确。用户看到的是一个说谎的预览，自然会觉得「写完不知道对不对」。
     */
    private val _previewTitle = MutableStateFlow("")
    val previewTitle: StateFlow<String> = _previewTitle.asStateFlow()

    private val _loaded = MutableStateFlow(ruleId == 0L && presetUnrecognizedId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _results = Channel<RuleEditResult>(Channel.BUFFERED)
    val results: Flow<RuleEditResult> = _results.receiveAsFlow()

    init {
        viewModelScope.launch {
            if (ruleId != 0L) {
                _draft.value = repository.loadDraft(ruleId)
            }
            if (presetUnrecognizedId != 0L) {
                val item = prefs.unrecognized.first().firstOrNull { it.id == presetUnrecognizedId }
                if (item != null) {
                    _previewText.value = item.text
                    _previewTitle.value = item.title
                    if (ruleId == 0L && _draft.value.name.isBlank()) {
                        // 给个默认名字方便用户保存
                        _draft.value = _draft.value.copy(name = item.title.take(20))
                    }
                }
            }
            _loaded.value = true
        }
    }

    fun updateName(value: String) = update { it.copy(name = value) }
    fun updateChannel(value: String?) = update { it.copy(channel = value) }
    fun updateDirection(value: String) = update { it.copy(direction = value) }
    fun updateTitleKeywords(value: String) = update { it.copy(titleKeywords = value) }
    fun updateAmountKeyword(value: String) = update { it.copy(amountKeyword = value) }
    fun updateMerchantKeyword(value: String) = update { it.copy(merchantKeyword = value) }
    fun updateMerchantBeforeAmount(value: Boolean) = update { it.copy(merchantBeforeAmount = value) }
    fun updatePreviewText(value: String) { _previewText.value = value }
    fun updatePreviewTitle(value: String) { _previewTitle.value = value }

    fun save() {
        val current = _draft.value
        if (!current.canSave) return
        viewModelScope.launch {
            repository.save(current)
            // 从未识别队列跳来时，尝试用新规则把这条原通知落账
            if (presetUnrecognizedId != 0L) {
                tryAutoRecord(current.toCustomRule().toPaymentRule())
            }
            _results.send(RuleEditResult.Saved)
        }
    }

    fun delete() {
        if (ruleId == 0L) return
        viewModelScope.launch {
            repository.delete(ruleId)
            _results.send(RuleEditResult.Deleted)
        }
    }

    /**
     * 用新规则 parse 预填的未识别通知；成功则落账并出队，失败则仅存规则不落账。
     * 沿用 UnrecognizedViewModel.recordManual 范式：分类 null，occurredAt 用原通知时间。
     */
    private suspend fun tryAutoRecord(rule: com.myapp.feature.ledger.notification.PaymentRule) {
        val item = prefs.unrecognized.first().firstOrNull { it.id == presetUnrecognizedId } ?: return
        val result = PaymentParser.parse(item.channel, item.title, item.text, listOf(rule))
        if (result is PaymentParseResult.Success) {
            val raw = "${item.title}\n${item.text}"
            ledgerWriter.recordExpense(
                amountCents = result.amountCents,
                merchant = result.merchant,
                category = null,
                occurredAt = item.occurredAt,
                raw = raw,
                direction = result.direction,
            )
            prefs.removeUnrecognized(item.id)
        }
    }

    private fun update(block: (CustomRuleDraft) -> CustomRuleDraft) {
        _draft.update(block)
    }
}
