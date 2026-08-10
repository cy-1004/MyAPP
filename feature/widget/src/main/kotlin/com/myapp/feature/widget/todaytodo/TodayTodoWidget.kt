package com.myapp.feature.widget.todaytodo

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.model.TodoEntity
import com.myapp.feature.widget.ToggleTodoCallback
import com.myapp.feature.widget.WidgetIntents
import com.myapp.feature.widget.data.WidgetScreens
import com.myapp.feature.widget.di.WidgetDataProvider
import com.myapp.feature.widget.ui.WidgetPalette
import com.myapp.feature.widget.ui.WidgetTextStyles
import com.myapp.feature.widget.ui.widgetPalette
import dagger.hilt.android.EntryPointAccessors

/**
 * W3 今日待办（2×2，PRD 3.10）。
 *
 * 展示今天（含逾期）未完成的待办，最多 4 条；圆圈勾选走
 * [ToggleTodoCallback]——重复任务的行为与 App 内一致。全部完成时显示完成态，
 * 一条都没有且今天也没完成过时显示「今天没有安排」，两者有区分
 * （完成态是肯定语气，空态是引导）。
 */
class TodayTodoWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataProvider::class.java,
        )
        val dao = entry.todoDao()
        val today = AppTime.todayRange()
        val todos = dao.getUndoneBefore(now = AppTime.now(), before = today.last + 1).take(4)
        val doneToday = dao.countDoneInRange(start = today.first, endExclusive = today.last + 1)
        provideContent {
            TodayTodoContent(todos, doneToday)
        }
    }
}

class TodayTodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = TodayTodoWidget()
}

@Composable
private fun TodayTodoContent(todos: List<TodoEntity>, doneToday: Int) {
    val palette = LocalContext.current.widgetPalette()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.surface)
            .padding(12.dp),
    ) {
        Header(palette, todos.size)
        if (todos.isEmpty()) {
            // defaultWeight 是 ColumnScope 成员，只能在 Column 的 lambda 里调用
            EmptyState(palette, doneToday, GlanceModifier.defaultWeight())
        } else {
            todos.forEach { TodoRow(it, palette) }
        }
    }
}

@Composable
private fun Header(palette: WidgetPalette, undoneCount: Int) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.TODO)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "今日待办",
            style = WidgetTextStyles.title.copy(color = ColorProvider(palette.textPrimary)),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            text = if (undoneCount > 0) "$undoneCount 项" else "",
            style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textTertiary)),
        )
    }
}

@Composable
private fun EmptyState(palette: WidgetPalette, doneToday: Int, modifier: GlanceModifier) {
    val allDone = doneToday > 0
    Text(
        text = if (allDone) "全部完成" else "今天没有安排",
        style = WidgetTextStyles.body.copy(
            color = ColorProvider(if (allDone) palette.success else palette.textSecondary),
            textAlign = TextAlign.Center,
        ),
        modifier = modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
private fun TodoRow(todo: TodoEntity, palette: WidgetPalette) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "○",
            style = TextStyle(fontSize = 14.sp, color = ColorProvider(palette.accent)),
            modifier = GlanceModifier
                .clickable(ToggleTodoCallback.toggle(todo.id))
                .padding(end = 6.dp),
        )
        Text(
            text = todo.title,
            style = WidgetTextStyles.body.copy(color = ColorProvider(palette.textPrimary)),
            maxLines = 1,
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.TODO)),
        )
        todo.dueAt?.let { due ->
            val overdue = due < AppTime.now()
            Text(
                text = if (overdue) "已逾期" else AppTime.run { due.toLocalDateTime().format(AppFormatters.time) },
                style = WidgetTextStyles.caption.copy(
                    color = ColorProvider(if (overdue) palette.accent else palette.textTertiary),
                ),
            )
        }
    }
}
