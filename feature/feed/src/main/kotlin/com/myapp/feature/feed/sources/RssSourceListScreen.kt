package com.myapp.feature.feed.sources

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.component.EmptyState
import com.myapp.core.designsystem.component.LocalBottomBarHeight
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.core.ui.navigation.Route
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * RSS 订阅源管理列表（PRD 3.9）：新建/编辑/启停/删除/排序，从 [RssArticleListScreen] 顶栏入口进。
 *
 * 与 `KnowledgeListScreen` 同一套交互语言：左滑删除 + Snackbar 撤销、上移/下移箭头调顺序，
 * 卡片内标题/URL 单独占一行、操作图标另起一行右对齐——避免图标固定宽度撑爆卡片导致
 * 标题被挤压到 0 宽度不可见（M6 真机验证时踩过的坑，这里从一开始就按正确布局写）。
 */
@Composable
fun RssSourceListScreen(
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RssSourceListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = "已删除「${event.title}」",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(event.id)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.importEvents.collect { event ->
            val message = if (event.added == 0 && event.skipped == 0) {
                "导入失败，不是合法的 OPML 文件"
            } else {
                "已导入 ${event.added} 个，跳过 ${event.skipped} 个（已存在）"
            }
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // OPML 导出：CreateDocument 拿到用户选的 Uri 后再问 ViewModel 要文本写进去——
    // 写文件这一步离不开 Activity 生命周期内才有效的 Uri，只能放在 UI 层，不能下沉到 Repository。
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val opml = viewModel.exportOpml()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(opml.toByteArray()) }
            }.onSuccess {
                snackbarHostState.showSnackbar("OPML 已导出", duration = SnackbarDuration.Short)
            }.onFailure {
                snackbarHostState.showSnackbar("导出失败", duration = SnackbarDuration.Short)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.importOpml(uri)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("订阅源管理", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/xml", "text/x-opml", "*/*")) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "导入 OPML")
                    }
                    IconButton(onClick = { exportLauncher.launch(defaultOpmlFileName()) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导出 OPML")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Route.RssSourceDetail()) },
                modifier = Modifier.padding(bottom = LocalBottomBarHeight.current),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加订阅源")
            }
        },
    ) { innerPadding ->
        if (state.loaded && state.groups.isEmpty()) {
            EmptyState(
                text = "还没有订阅源，点 + 添加一个 RSS/Atom 地址",
                actionLabel = "添加",
                onAction = { onNavigate(Route.RssSourceDetail()) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, top = Spacing.sm, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.name}") {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.appColors.textSecondary,
                        modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs),
                    )
                }
                items(items = group.rows, key = { it.source.id }) { row ->
                    RssSourceRowItem(
                        row = row,
                        onEdit = { onNavigate(Route.RssSourceDetail(row.source.id)) },
                        onDelete = { viewModel.delete(row) },
                        onToggleEnabled = { on -> viewModel.setEnabled(row.source.id, on) },
                        onMove = { delta -> viewModel.move(row.source.id, delta) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RssSourceRowItem(
    row: RssSourceRow,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val currentDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) currentDelete()
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.appColors.danger, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.xl),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        },
    ) {
        AppCard(onClick = onEdit) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.source.title.ifBlank { row.source.url },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = row.source.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = { onMove(-1) }, enabled = !row.isFirst) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "上移")
                }
                IconButton(onClick = { onMove(1) }, enabled = !row.isLast) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下移")
                }
                Switch(checked = row.source.enabled, onCheckedChange = onToggleEnabled)
            }
        }
    }
}

private fun defaultOpmlFileName(): String =
    "myapp-rss-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.opml"
