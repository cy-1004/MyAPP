package com.myapp.feature.widget.overview

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.lifecycle.lifecycleScope
import com.myapp.feature.widget.data.WidgetPrefsStore
import com.myapp.feature.widget.ui.WidgetConfigTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * W1 配置页：切换显示/隐藏支出区。
 *
 * 「不想让旁人看到钱的场景」（PRD 3.10）：关闭后小组件只显示日期与待办。
 * 与 W4 配置页同构：保存 → 触发一次 update → RESULT_OK 结束。
 */
@AndroidEntryPoint
class OverviewConfigActivity : ComponentActivity() {

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
        var showExpense by remember { mutableStateOf(true) }
        var loaded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            showExpense = prefs.w1ShowExpense(appWidgetId).first()
            loaded = true
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
                text = "小组件设置",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = showExpense,
                    onCheckedChange = { showExpense = it },
                    modifier = Modifier.padding(end = 12.dp),
                )
                Column {
                    Text("显示支出与预算", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "关闭后只显示日期与待办，适合不想让旁人看到余额的场景。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    lifecycleScope.launch {
                        prefs.setW1ShowExpense(appWidgetId, showExpense)
                        TodayOverviewWidget().update(applicationContext, AppWidgetId(appWidgetId))
                        setResult(
                            RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    }
                },
                enabled = loaded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("完成")
            }
        }
    }
}
