package com.myapp.feature.period.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime.toLocalDateTime
import com.myapp.core.designsystem.component.AppCard
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.period.calendar.PeriodAiUiState

/**
 * 经期页的 AI 分析区（PRD 3.14）。
 *
 * 三个状态刻意都做成「卡片一直在」而不是「有结果才出现」：
 * 没开启时它是引导，峰价时它是说明，出错时它保留着上一次的结论。
 * 让一整块 UI 忽隐忽现会让人以为功能坏了。
 */
@Composable
fun PeriodAiCard(
    state: PeriodAiUiState,
    peak: Boolean,
    peakEndsAt: String?,
    onAnalyze: (force: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("AI 分析", style = MaterialTheme.typography.titleSmall)

            when {
                !state.enabled -> Text(
                    text = "AI 分析默认关闭。开启后可以让模型结合你的周期与每日记录给一段解读——" +
                        "开启前会说清楚会发送哪些内容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )

                !state.hasApiKey -> Text(
                    text = "还差一个 DeepSeek API Key，去设置里填一个就能用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.textSecondary,
                )

                peak -> Text(
                    text = "当前为峰价时段，调用费用翻倍" +
                        (peakEndsAt?.let { "，$it 之后转为谷价" } ?: "") +
                        "。仍然可以现在分析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.appColors.warning,
                )
            }

            if (state.text.isNotBlank()) {
                Text(state.text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = buildString {
                        append("更新于 ")
                        append(state.updatedAt.toLocalDateTime().format(AppFormatters.dateTime))
                        // 「这次没真的发请求」值得说出来：否则用户会以为点了没反应
                        if (state.fromCache) append(" · 数据没变，沿用上次结果")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                )
                Text(
                    text = "AI 生成，仅供参考，不能替代医生",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.appColors.textSecondary,
                )
            }

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.appColors.danger)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.enabled || !state.hasApiKey) {
                    Button(onClick = onOpenSettings) { Text("去设置") }
                } else if (state.running) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        // 联网搜索会拖到十几秒，只转圈不说话会让人以为卡死了
                        text = "正在联网查资料并分析，可能要十几秒…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.textSecondary,
                    )
                } else {
                    // 峰价时段的二次确认在调用方（它要弹对话框），这里只管发出意图
                    Button(onClick = { onAnalyze(false) }) {
                        Text(if (state.text.isBlank()) "开始分析" else "重新分析")
                    }
                    // 数据没变时「重新分析」会命中缓存，这个入口用来强制要一份新的
                    if (state.text.isNotBlank()) {
                        TextButton(onClick = { onAnalyze(true) }) { Text("强制刷新") }
                    }
                }
            }
        }
    }
}
