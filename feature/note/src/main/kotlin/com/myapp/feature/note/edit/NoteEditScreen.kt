package com.myapp.feature.note.edit

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.myapp.core.designsystem.component.bringIntoViewOnFocus
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import java.io.File

/**
 * 新建 / 编辑笔记（PRD 3.4）。
 *
 * 新建与编辑共用一个页面：字段一致，差异只在标题文案与「删除」按钮显隐。
 *
 * Markdown 用 raw 文本输入（不做实时预览）--V1 自用项目，编辑后看列表/详情渲染
 * 即可，实时预览会增加排版复杂度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.results.collect { onBack() }
    }
    LaunchedEffect(Unit) {
        viewModel.imageErrors.collect { failed ->
            snackbarHostState.showSnackbar(
                message = "$failed 张图片导入失败",
                duration = SnackbarDuration.Short,
            )
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_IMAGES),
    ) { uris: List<Uri> ->
        viewModel.addImages(uris)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (draft.isNew) "新建笔记" else "编辑笔记",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::togglePinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = if (draft.pinned) "取消置顶" else "置顶",
                            tint = if (draft.pinned) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.appColors.textTertiary
                            },
                        )
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
                label = { Text("写点什么") },
                minLines = 6,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewOnFocus(),
            )

            SectionLabel("标签")
            TagEditor(
                tags = draft.tags,
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
            )

            SectionLabel("图片")
            ImageRow(
                images = draft.images,
                filesDir = context.filesDir,
                onAdd = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemove = viewModel::removeImage,
            )

            SectionLabel("置顶")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (draft.pinned) "已置顶，列表顶部显示" else "未置顶",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (draft.pinned) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.appColors.textSecondary
                    },
                )
                Switch(checked = draft.pinned, onCheckedChange = { viewModel.togglePinned() })
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

@Composable
private fun ImageRow(
    images: List<String>,
    filesDir: File,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        images.forEach { path ->
            Box(modifier = Modifier.size(80.dp)) {
                AsyncImage(
                    model = File(filesDir, path),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .clickable { onRemove(path) }
                        .padding(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "删除图片",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        // 末尾「+」按钮
        if (images.size < MAX_IMAGES) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加图片",
                    tint = MaterialTheme.appColors.textSecondary,
                )
            }
        }
    }
}

private const val MAX_IMAGES = 9
