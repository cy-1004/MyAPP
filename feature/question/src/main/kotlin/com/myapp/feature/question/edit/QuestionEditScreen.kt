package com.myapp.feature.question.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.bringIntoViewOnFocus
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.question.data.QuestionStatus

/**
 * 新建 / 编辑疑问（PRD 3.5）。
 *
 * 新建与编辑共用一个页面：字段一致，差异只在标题文案与「删除」按钮显隐。
 *
 * 状态机：OPEN/RESOLVED/ARCHIVED 三态可自由切换。
 * answer 字段仅在 RESOLVED 时启用并自动聚焦--PRD「解决时填写」。
 * 「转为笔记」入口仅在编辑已解决疑问时出现（PRD 3.5）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditScreen(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuestionEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val answerFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.results.collect { onBack() }
    }
    LaunchedEffect(Unit) {
        viewModel.convertEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = "已存为笔记",
                actionLabel = "查看",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigate(Route.NoteDetail(event.noteId))
            }
        }
    }
    // 切到 RESOLVED 时聚焦 answer 字段（PRD「解决时填写」的引导）
    LaunchedEffect(draft.status) {
        if (draft.status == QuestionStatus.RESOLVED && draft.answer.isBlank()) {
            runCatching { answerFocus.requestFocus() }
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
                        text = if (draft.isNew) "新建疑问" else "编辑疑问",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!draft.isNew && draft.status == QuestionStatus.RESOLVED) {
                        OverflowConvertMenu(onConvert = viewModel::convertToNote)
                    }
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
        if (!loaded) return@Scaffold

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            OutlinedTextField(
                value = draft.content,
                onValueChange = viewModel::updateContent,
                label = { Text("疑问是什么") },
                minLines = 4,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus(),
            )

            OutlinedTextField(
                value = draft.context,
                onValueChange = viewModel::updateContext,
                label = { Text("在哪里遇到的（可选）") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("标签")
            TagEditor(
                tags = draft.tags,
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
            )

            SectionLabel("状态")
            StatusSelector(
                status = draft.status,
                onChange = viewModel::updateStatus,
            )

            if (draft.status == QuestionStatus.RESOLVED) {
                SectionLabel("解答")
                OutlinedTextField(
                    value = draft.answer,
                    onValueChange = viewModel::updateAnswer,
                    label = { Text("写下答案") },
                    minLines = 3,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(answerFocus)
                        .bringIntoViewOnFocus(),
                )
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusSelector(
    status: QuestionStatus,
    onChange: (QuestionStatus) -> Unit,
) {
    val options = listOf(
        QuestionStatus.OPEN to "待解决",
        QuestionStatus.RESOLVED to "已解决",
        QuestionStatus.ARCHIVED to "已归档",
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = status == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Composable
private fun OverflowConvertMenu(onConvert: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("转为笔记") },
                onClick = {
                    expanded = false
                    onConvert()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagEditor(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tag, style = MaterialTheme.typography.labelLarge)
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "删除标签",
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        },
                        shape = MaterialTheme.shapes.small,
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("加标签（回车确认）") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = {
                        onAdd(input)
                        input = ""
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "添加标签")
                    }
                }
            },
        )
    }
}
