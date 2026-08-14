package com.myapp.feature.ledger.rule

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.bringIntoViewOnFocus
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.ledger.data.CustomRuleDraft
import com.myapp.feature.ledger.notification.CustomRule
import com.myapp.feature.ledger.notification.PaymentParseResult
import com.myapp.feature.ledger.notification.PaymentParser
import com.myapp.feature.ledger.notification.RuleMatchDiagnosis

/**
 * 规则编辑页（PRD 3.6.1 Phase 3）。
 *
 * 关键交互：实时预览。用户每改一个字段，立刻用当前草稿生成的规则去 parse 预览文本，
 * 展示提取到的金额/方向/商户或「未匹配」。让用户在保存前确认规则正确。
 */
@Composable
fun RuleDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RuleDetailViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val previewText by viewModel.previewText.collectAsStateWithLifecycle()
    val previewTitle by viewModel.previewTitle.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.results.collect { onBack() }
    }

    // 标题必须真的传进去：写死空串会让「标题关键词」非空的规则永远预览失败（见 VM 注释）
    val preview = remember(draft, previewText, previewTitle) {
        if (previewText.isBlank() && previewTitle.isBlank()) null
        else {
            val rule = draft.toCustomRule().toPaymentRule()
            PaymentParser.parse(draft.channel, previewTitle, previewText, listOf(rule))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draft.isNew) "新建规则" else "编辑规则",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!draft.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.appColors.danger,
                            )
                        }
                    }
                    TextButton(onClick = viewModel::save, enabled = draft.canSave) {
                        Text("保存", style = MaterialTheme.typography.labelLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            if (loaded) {
                RuleDetailForm(
                    draft = draft,
                    previewText = previewText,
                    previewTitle = previewTitle,
                    preview = preview,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun RuleDetailForm(
    draft: CustomRuleDraft,
    previewText: String,
    previewTitle: String,
    preview: PaymentParseResult?,
    viewModel: RuleDetailViewModel,
) {
    // 试跑区放在最上面：先贴一条真实通知、看着结果调下面的字段，
    // 比填完一屏参数最后才发现底部有个预览框顺手得多。
    // 从未识别队列跳进来时这两个框已经预填好原文，用户只要调关键词。
    SectionLabel("先试跑")
    OutlinedTextField(
        value = previewTitle,
        onValueChange = viewModel::updatePreviewTitle,
        label = { Text("通知标题（如「交易提醒」）") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )
    OutlinedTextField(
        value = previewText,
        onValueChange = viewModel::updatePreviewText,
        label = { Text("通知正文（如「你有一笔5.00元的支出」）") },
        minLines = 2,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )

    PreviewResult(
        preview = preview,
        failureHint = remember(draft, previewTitle, previewText) {
            RuleMatchDiagnosis.diagnose(
                rule = draft.toCustomRule().toPaymentRule(),
                channel = draft.channel,
                title = previewTitle,
                text = previewText,
            )
        },
    )

    SectionLabel("规则")
    OutlinedTextField(
        value = draft.name,
        onValueChange = viewModel::updateName,
        label = { Text("规则名称") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )

    SectionLabel("渠道")
    ChipRow(
        options = CustomRule.CHANNELS,
        selected = draft.channel,
        optionKey = { it.first },
        labelOf = { it.second },
        onSelect = { viewModel.updateChannel(it.first) },
    )

    SectionLabel("方向")
    ChipRow(
        options = CustomRule.DIRECTIONS,
        selected = draft.direction,
        optionKey = { it },
        labelOf = { if (it == "EXPENSE") "支出" else "收入" },
        onSelect = viewModel::updateDirection,
    )

    OutlinedTextField(
        value = draft.titleKeywords,
        onValueChange = viewModel::updateTitleKeywords,
        label = { Text("标题必须包含（逗号分隔，可留空）") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )

    OutlinedTextField(
        value = draft.amountKeyword,
        onValueChange = viewModel::updateAmountKeyword,
        label = { Text("金额紧跟在这几个字后面（如「你有一笔」「付款」）") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )

    OutlinedTextField(
        value = draft.merchantKeyword,
        onValueChange = viewModel::updateMerchantKeyword,
        label = { Text("商户名紧跟在这几个字后面（可留空，如「向」）") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )

    if (draft.merchantKeyword.isNotBlank()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("商户在金额前", style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "如「向 星巴克 付款 23.50」，关掉则商户在金额后",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
            Switch(
                checked = draft.merchantBeforeAmount,
                onCheckedChange = viewModel::updateMerchantBeforeAmount,
            )
        }
    }

}

@Composable
private fun PreviewResult(
    preview: PaymentParseResult?,
    /** 匹配失败时的具体原因，比笼统的「未匹配」有用得多。 */
    failureHint: String = "未匹配 —— 检查关键词是否拼对、金额格式是否正确",
) {
    AppCard {
        when (preview) {
            null -> Text(
                text = "在上方输入通知正文，看看规则能不能提取出金额",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.textSecondary,
            )
            is PaymentParseResult.Success -> {
                Text(
                    text = "金额：¥${"%.2f".format(preview.amountCents / 100.0)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "方向：${if (preview.direction == "EXPENSE") "支出" else "收入"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "商户：${preview.merchant ?: "（未识别）"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            PaymentParseResult.Failed -> Text(
                text = failureHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.appColors.danger,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.appColors.textSecondary,
    )
}

@Composable
private fun <T, K> ChipRow(
    options: List<T>,
    selected: K,
    optionKey: (T) -> K,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = optionKey(option) == selected,
                onClick = { onSelect(option) },
                label = { Text(labelOf(option), style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
            )
        }
    }
}
