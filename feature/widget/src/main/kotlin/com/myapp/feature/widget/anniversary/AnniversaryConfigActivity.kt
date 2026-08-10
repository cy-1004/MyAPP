package com.myapp.feature.widget.anniversary

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.AppWidgetId
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.database.model.AnniversaryEntity
import com.myapp.feature.widget.data.WidgetPrefsStore
import com.myapp.feature.widget.di.WidgetDataProvider
import com.myapp.feature.widget.ui.WidgetConfigTheme
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * W4 配置页：选择小组件盯哪一条纪念日。
 *
 * 配置 Activity 由启动器启动，读 EXTRA_APPWIDGET_ID；保存后必须自己触发一次
 * update（带 configure 的小组件在添加时不会走 receiver 的 onUpdate），
 * 再以 RESULT_OK + 同一 extra 结束。
 */
@AndroidEntryPoint
class AnniversaryConfigActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: WidgetPrefsStore

    private val appWidgetId: Int
        get() = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            WidgetConfigTheme {
                // Surface 必须包一层：M3 的 MaterialTheme 不提供 LocalContentColor，
                // 主 App 靠 Scaffold 提供，这里不包的话所有默认色文字会渲染成纯黑
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ConfigScreen()
                }
            }
        }
    }

    @Composable
    private fun ConfigScreen() {
        val entry = remember {
            EntryPointAccessors.fromApplication(applicationContext, WidgetDataProvider::class.java)
        }
        val dao = entry.anniversaryDao()
        var items by remember { mutableStateOf<List<AnniversaryEntity>>(emptyList()) }
        var selectedId by remember { mutableStateOf<Long?>(null) }

        LaunchedEffect(Unit) {
            items = dao.getAllActive().sortedBy { it.date }
            selectedId = prefs.w4SelectedAnniversaryId(appWidgetId).first()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // 边到边窗口必须自己处理 insets，否则标题会被状态栏盖住
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 20.dp),
        ) {
            Text(
                text = "选择要盯的纪念日",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "「自动选择」按 置顶 → 最近的下一个 → 最早创建 的顺序取一条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 列表限高而非 weight(1f) 撑满：条目少时整体紧凑收拢，不出现大片空白
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                item(key = "auto") {
                    SelectableRow(
                        title = "自动选择",
                        subtitle = null,
                        selected = selectedId == null,
                        onClick = { selectedId = null },
                    )
                }
                items(items, key = { it.id }) { item ->
                    SelectableRow(
                        title = item.title,
                        subtitle = item.summaryLine(),
                        selected = selectedId == item.id,
                        onClick = { selectedId = item.id },
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    lifecycleScope.launch {
                        prefs.setW4SelectedAnniversaryId(appWidgetId, selectedId)
                        AnniversaryCountdownWidget().update(applicationContext, AppWidgetId(appWidgetId))
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("完成")
            }
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun AnniversaryEntity.summaryLine(): String {
    val dateText = LocalDate.ofEpochDay(date).format(AppFormatters.date)
    val repeat = when (repeatType) {
        "CUMULATIVE" -> "累计天数"
        "ONCE" -> "只有一次"
        else -> "每年"
    }
    return if (pinned) "置顶 · $repeat · $dateText" else "$repeat · $dateText"
}
