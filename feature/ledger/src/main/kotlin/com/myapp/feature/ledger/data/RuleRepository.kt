package com.myapp.feature.ledger.data

import com.myapp.feature.ledger.notification.CustomRule
import com.myapp.feature.ledger.notification.PaymentRule
import com.myapp.feature.ledger.notification.builtinPaymentRules
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * 规则编辑器的草稿（编辑页双向绑定用）。与 [CustomRule] 的区别：id 为 0 表示新建，
 * 没有持久化身份；保存时由 [RuleRepository.save] 分配 id。
 */
data class CustomRuleDraft(
    val id: Long = 0L,
    val name: String = "",
    val channel: String? = null,
    val direction: String = CustomRule.DIRECTIONS.first(),
    val titleKeywords: String = "",
    val amountKeyword: String = "",
    val merchantKeyword: String = "",
    val merchantBeforeAmount: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L

    /** 保存按钮启用条件：名称 + 金额关键词必填，渠道/方向有默认值。 */
    val canSave: Boolean get() = name.isNotBlank() && amountKeyword.isNotBlank()

    /** 把逗号/顿号分隔的关键词字符串切成列表，去空白与空项。 */
    fun parsedTitleKeywords(): List<String> =
        titleKeywords.split(",", "，", "、", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun toCustomRule(): CustomRule = CustomRule(
        id = id,
        name = name.trim(),
        channel = channel,
        direction = direction,
        titleKeywords = parsedTitleKeywords(),
        amountKeyword = amountKeyword.trim(),
        merchantKeyword = merchantKeyword.trim().ifBlank { null },
        merchantBeforeAmount = merchantBeforeAmount,
    )

    companion object {
        fun from(rule: CustomRule): CustomRuleDraft = CustomRuleDraft(
            id = rule.id,
            name = rule.name,
            channel = rule.channel,
            direction = rule.direction,
            titleKeywords = rule.titleKeywords.joinToString(","),
            amountKeyword = rule.amountKeyword,
            merchantKeyword = rule.merchantKeyword.orEmpty(),
            merchantBeforeAmount = rule.merchantBeforeAmount,
        )
    }
}

/**
 * 规则仓库：合并自定义规则与内置规则，给 [com.myapp.feature.ledger.notification.PaymentParser] 喂「当前生效规则集」。
 *
 * 自定义规则在前（用户改的优先级更高），内置规则在后；内置规则里被 [disabledBuiltinIds] 命中的跳过。
 * 规则更新后下条通知自动生效，无需重启 NotificationListenerService。
 */
@Singleton
class RuleRepository @Inject constructor(
    private val store: RuleStore,
) {
    /** 当前生效的规则集：自定义在前，内置未停用的在后。 */
    val activeRules: Flow<List<PaymentRule>> = combine(
        store.customRules,
        store.disabledBuiltinIds,
    ) { custom, disabled ->
        custom.map { it.toPaymentRule() } + builtinPaymentRules.filterNot { it.builtinId in disabled }
    }

    val customRules: Flow<List<CustomRule>> = store.customRules

    val disabledBuiltinIds: Flow<Set<String>> = store.disabledBuiltinIds

    suspend fun save(draft: CustomRuleDraft): Long {
        val rule = draft.toCustomRule()
        return if (draft.isNew) {
            store.add(rule)
        } else {
            store.update(rule)
            rule.id
        }
    }

    suspend fun delete(id: Long) = store.delete(id)

    /** 撤销删除：把之前删掉的规则原样塞回（保留原 id）。 */
    suspend fun restore(rule: CustomRule) {
        store.add(rule)
    }

    suspend fun toggleBuiltin(id: String, on: Boolean) = store.setBuiltinEnabled(id, on)

    /** 读已有规则成草稿；id 为 0 或不存在返回空草稿。 */
    suspend fun loadDraft(id: Long): CustomRuleDraft {
        if (id == 0L) return CustomRuleDraft()
        val list = store.customRules.first()
        val rule = list.firstOrNull { it.id == id } ?: return CustomRuleDraft()
        return CustomRuleDraft.from(rule)
    }
}
