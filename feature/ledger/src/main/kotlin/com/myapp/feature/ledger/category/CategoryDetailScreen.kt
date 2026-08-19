package com.myapp.feature.ledger.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.bringIntoViewOnFocus
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.ledger.data.CategoryDraft
import com.myapp.feature.ledger.data.ManagedCategory
import com.myapp.feature.ledger.ui.categoryColor
import com.myapp.feature.ledger.ui.categoryIcon
import com.myapp.feature.ledger.ui.selectableCategoryColors
import com.myapp.feature.ledger.ui.selectableCategoryIcons

/**
 * 分类编辑页（PRD 3.6 M5 Phase 3）。
 *
 * 名称 + 图标 + 颜色三件套，顶部一张实时预览卡片--选图标/颜色时立刻看到
 * 它在记账列表里长什么样，不用保存完再回来看。
 *
 * 保留项（未分类）名称锁定：那个名字是自动记账的兜底落点，改了会让
 * `LedgerRepository.resolveCategoryId` 找不到分类而抛错。
 */
@Composable
fun CategoryDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CategoryDetailViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val parentOptions by viewModel.parentOptions.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.results.collect { result ->
            when (result) {
                CategoryEditResult.Saved, CategoryEditResult.Deleted -> onBack()
                // 重名不退页：留在原地改名字，输入内容不丢
                CategoryEditResult.DuplicateName ->
                    snackbarHostState.showSnackbar("已有同名分类，换一个名字")
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draft.isNew) "新建分类" else "编辑分类",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 有子分类的父分类不显示删除：先删/挪走子分类才能删它，见 CategoryRepository.delete
                    if (!draft.isNew && !draft.isProtected && !draft.hasChildren) {
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
                CategoryDetailForm(draft = draft, parentOptions = parentOptions, viewModel = viewModel)
            }
        }
    }
}

// FlowRow 在当前 compose-foundation 版本仍是实验 API（build-logic 里只统一开了
// ExperimentalFoundationApi，布局这套是独立的 ExperimentalLayoutApi 开关）
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryDetailForm(
    draft: CategoryDraft,
    parentOptions: List<ManagedCategory>,
    viewModel: CategoryDetailViewModel,
) {
    PreviewCard(draft = draft)

    OutlinedTextField(
        value = draft.name,
        onValueChange = viewModel::updateName,
        label = { Text("分类名称") },
        supportingText = {
            Text(
                text = if (draft.isProtected) {
                    "保留项名称不可修改：自动记账没命中分类时要落到它"
                } else {
                    "最多 ${CategoryDraft.MAX_NAME_LENGTH} 个字，不能与已有分类重名"
                },
            )
        },
        enabled = !draft.isProtected,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )

    OutlinedTextField(
        value = draft.capYuanText,
        onValueChange = viewModel::updateCap,
        label = { Text("分类预算上限（元，可留空）") },
        supportingText = { Text("这个分类本期花超过这个数，预算页会标红提醒——各分类上限之和不用等于本期总预算") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewOnFocus(),
    )

    // 保留项必须稳定在顶级；已经有子分类的不能再挂到别人底下（会出现三级）--
    // 两种情况都不给选择器，给了也会被 Repository.resolveParentId 拦回顶级，
    // 与其让用户选了却被悄悄改回去，不如一开始就不给选
    if (!draft.isProtected && !draft.hasChildren) {
        SectionLabel("所属分类")
        ParentCategoryPicker(
            options = parentOptions,
            selectedId = draft.parentId,
            onSelect = viewModel::updateParent,
        )
    } else if (draft.hasChildren) {
        Text(
            text = "这个分类下面已经有子分类了，不能再挂到别的分类底下",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.appColors.textSecondary,
        )
    }

    SectionLabel("图标")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        selectableCategoryIcons.forEach { key ->
            IconOption(
                iconKey = key,
                colorKey = draft.color,
                selected = key == draft.icon,
                onClick = { viewModel.updateIcon(key) },
            )
        }
    }

    SectionLabel("颜色")
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        selectableCategoryColors.forEach { key ->
            ColorOption(
                colorKey = key,
                selected = key == draft.color,
                onClick = { viewModel.updateColor(key) },
            )
        }
    }
}

/** 顶部预览：选图标/颜色时立刻看到最终效果。 */
@Composable
private fun PreviewCard(draft: CategoryDraft) {
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = categoryColor(draft.color)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(draft.icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column {
                Text(
                    text = draft.name.ifBlank { "未命名分类" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "记账列表里就长这样",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun IconOption(
    iconKey: String,
    colorKey: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint = categoryColor(colorKey)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (selected) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tint else MaterialTheme.appColors.border,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = categoryIcon(iconKey),
            contentDescription = iconKey,
            tint = if (selected) tint else MaterialTheme.appColors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ColorOption(
    colorKey: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color = categoryColor(colorKey)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.appColors.border
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/**
 * 「所属分类」选择器：一个「无（一级分类）」chip + 每个可选顶级分类一个 chip。
 * 复用记账编辑页方向选择器同一套 FilterChip + 横向滚动样式（[selectableCategoryIcons] 那套
 * 图标/颜色选择器视觉更重，这里只是个归属关系，用轻量 chip 就够）。
 */
@Composable
private fun ParentCategoryPicker(
    options: List<ManagedCategory>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text("无（一级分类）", style = MaterialTheme.typography.labelLarge) },
            shape = MaterialTheme.shapes.small,
        )
        options.forEach { option ->
            FilterChip(
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                label = { Text(option.name, style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.small,
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
