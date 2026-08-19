package com.myapp.feature.widget.overview

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.BudgetCycle
import com.myapp.core.database.model.BudgetEntity
import com.myapp.core.database.model.TodoEntity
import com.myapp.core.designsystem.theme.budgetColor
import com.myapp.feature.widget.ToggleTodoCallback
import com.myapp.feature.widget.WidgetIntents
import com.myapp.feature.widget.data.WidgetScreens
import com.myapp.feature.widget.di.WidgetDataProvider
import com.myapp.feature.widget.ui.WidgetPalette
import com.myapp.feature.widget.ui.WidgetTextStyles
import com.myapp.feature.widget.ui.widgetPalette
import com.myapp.feature.widget.ui.weekdayCn
import com.myapp.feature.widget.ui.yuanGrouped
import com.myapp.feature.widget.ui.yuanWithSymbol
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * W1 今日概览（默认 4×2，可拖高变大，PRD 7「小组件多尺寸」）。
 *
 * 日期 + 支出/预算（可在配置页隐藏，不想让旁人看到钱的场景）+ 待办。
 * 「还有 N 项」中的 N 是显示条数之外的数量；没有待办时区分
 * 「全部完成」与「今天没有安排」。
 *
 * **两档尺寸**（[SizeMode.Responsive]）：默认 4×2 大小横排最多 3 条待办
 * （原有布局，不变）；拖高之后换成竖排列表最多 6 条，每条带完成时间/逾期标记--
 * 横排在窄行里塞不下这些信息，拖高多出来的是纵向空间，改列表才用得上它。
 */
class TodayOverviewWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 200.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataProvider::class.java,
        )
        val appWidgetId = id.toString().toIntOrNull()
        val prefs = entry.widgetPrefsStore()
        val showExpense = appWidgetId?.let { prefs.w1ShowExpense(it).first() } ?: true

        val today = AppTime.todayRange()
        val now = AppTime.now()
        val dao = entry.todoDao()
        // 拿两档尺寸里较大的那个上限（6）：provideGlance 只跑一次，LocalSize 只在
        // Composable 里才能读到，所以在这里把两档都可能用到的数据一次性备好，
        // 具体展示几条留给下面的 Composable 按当前尺寸截取
        val todos = dao.getUndoneBefore(now = now, before = today.last + 1).take(6)
        val todoTotal = dao.countUndoneBefore(before = today.last + 1)
        val doneToday = dao.countDoneInRange(start = today.first, endExclusive = today.last + 1)

        val txnDao = entry.transactionDao()
        val todayExpense = txnDao.sumExpenseInRange(today.first, today.last + 1)
        val budget = entry.budgetDao().getCurrent()
        val cycleExpense = budget?.let { b ->
            val cycle = BudgetCycle.currentCycleRange(b.cycleStartDay)
            txnDao.sumExpenseInRange(cycle.first, cycle.last + 1)
        }
        provideContent {
            OverviewContent(
                todos = todos,
                todoTotal = todoTotal,
                doneToday = doneToday,
                showExpense = showExpense,
                todayExpense = todayExpense,
                budget = budget,
                cycleExpense = cycleExpense,
            )
        }
    }
}

class TodayOverviewWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = TodayOverviewWidget()
}

@Composable
private fun OverviewContent(
    todos: List<TodoEntity>,
    todoTotal: Int,
    doneToday: Int,
    showExpense: Boolean,
    todayExpense: Long,
    budget: BudgetEntity?,
    cycleExpense: Long?,
) {
    val palette = LocalContext.current.widgetPalette()
    // 拖高之后（PRD 7「小组件多尺寸」）换成竖排待办列表，见 TodoListExpanded 的说明
    val expanded = LocalSize.current.height >= 160.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.surface)
            .padding(12.dp),
    ) {
        DateRow(palette)
        if (showExpense) {
            ExpenseRow(palette, todayExpense, budget, cycleExpense)
        }
        if (expanded) {
            Spacer(GlanceModifier.height(4.dp))
            TodoListExpanded(palette, todos, todoTotal, doneToday)
        } else {
            Spacer(GlanceModifier.defaultWeight())
            TodoRow(palette, todos.take(3), todoTotal, doneToday)
        }
    }
}

@Composable
private fun DateRow(palette: WidgetPalette) {
    val context = LocalContext.current
    val today = AppTime.today()
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${today.format(AppFormatters.date)} ${today.weekdayCn()}",
            style = WidgetTextStyles.title.copy(color = ColorProvider(palette.textPrimary)),
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.HOME)),
        )
        Text(
            text = "＋",
            style = TextStyle(fontSize = 18.sp, color = ColorProvider(palette.accent)),
            modifier = GlanceModifier.clickable(WidgetIntents.openLedgerNew(context)),
        )
    }
}

@Composable
private fun ExpenseRow(
    palette: WidgetPalette,
    todayExpense: Long,
    budget: BudgetEntity?,
    cycleExpense: Long?,
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.LEDGER)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "今日支出 ${todayExpense.yuanWithSymbol()}",
            style = WidgetTextStyles.body.copy(
                color = ColorProvider(palette.accent),
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        val remaining = budget?.let { b ->
            cycleExpense?.let { b.totalAmount - it }
        }
        Text(
            text = if (remaining == null) "未设预算" else "剩余 ${remaining.yuanGrouped()}",
            style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textSecondary)),
        )
    }
    if (budget != null && cycleExpense != null) {
        val ratio = if (budget.totalAmount > 0) {
            cycleExpense.toFloat() / budget.totalAmount.toFloat()
        } else {
            1f
        }
        val barColor = budgetColor(ratio, palette.isDark)
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .width(84.dp)
                    .height(4.dp)
                    .background(palette.track),
            ) {
                Box(
                    modifier = GlanceModifier
                        .width((84f * ratio.coerceIn(0f, 1f)).dp)
                        .height(4.dp)
                        .background(barColor),
                    content = {},
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "${(ratio * 100).roundToInt().coerceAtLeast(0)}%",
                style = WidgetTextStyles.label.copy(color = ColorProvider(barColor)),
            )
        }
    }
}

@Composable
private fun TodoRow(palette: WidgetPalette, todos: List<TodoEntity>, todoTotal: Int, doneToday: Int) {
    val context = LocalContext.current
    val tail = when {
        todoTotal == 0 -> if (doneToday > 0) "全部完成" else "今天没有安排"
        todoTotal > 3 -> "还有 ${todoTotal - 3} 项"
        else -> "$todoTotal 项"
    }
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (todos.isEmpty()) {
            Text(
                text = tail,
                style = WidgetTextStyles.caption.copy(
                    color = ColorProvider(if (doneToday > 0) palette.success else palette.textTertiary),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
        } else {
            todos.forEach { todo ->
                Row(
                    modifier = GlanceModifier.defaultWeight().padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "○",
                        style = TextStyle(fontSize = 12.sp, color = ColorProvider(palette.accent)),
                        modifier = GlanceModifier.clickable(ToggleTodoCallback.toggle(todo.id)),
                    )
                    Text(
                        text = todo.title,
                        style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textPrimary)),
                        maxLines = 1,
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.TODO)),
                    )
                }
            }
            Text(
                text = tail,
                style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textTertiary)),
            )
        }
    }
}

/**
 * 拖高之后的竖排待办列表（PRD 7「小组件多尺寸」），最多 6 条，一行一条。
 *
 * 默认尺寸的 [TodoRow] 是横排、每条挤在一个 `defaultWeight()` 格子里，
 * 塞不下时间/逾期这类次要信息；拖高多出来的是纵向空间，换成竖排列表正好用上，
 * 顺带能带上每条的到期时间，跟 W3 今日待办小组件的单条样式保持一致。
 */
@Composable
private fun TodoListExpanded(palette: WidgetPalette, todos: List<TodoEntity>, todoTotal: Int, doneToday: Int) {
    val context = LocalContext.current
    if (todos.isEmpty()) {
        val allDone = doneToday > 0
        Text(
            text = if (allDone) "全部完成" else "今天没有安排",
            style = WidgetTextStyles.body.copy(
                color = ColorProvider(if (allDone) palette.success else palette.textTertiary),
            ),
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.TODO)),
        )
        return
    }
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        todos.forEach { todo ->
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "○",
                    style = TextStyle(fontSize = 12.sp, color = ColorProvider(palette.accent)),
                    modifier = GlanceModifier
                        .clickable(ToggleTodoCallback.toggle(todo.id))
                        .padding(end = 6.dp),
                )
                Text(
                    text = todo.title,
                    style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textPrimary)),
                    maxLines = 1,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.TODO)),
                )
                todo.dueAt?.let { due ->
                    val overdue = due < AppTime.now()
                    Text(
                        text = if (overdue) "已逾期" else AppTime.run { due.toLocalDateTime().format(AppFormatters.time) },
                        style = WidgetTextStyles.label.copy(
                            color = ColorProvider(if (overdue) palette.accent else palette.textTertiary),
                        ),
                    )
                }
            }
        }
        if (todoTotal > todos.size) {
            Text(
                text = "还有 ${todoTotal - todos.size} 项",
                style = WidgetTextStyles.label.copy(color = ColorProvider(palette.textTertiary)),
                modifier = GlanceModifier.padding(top = 4.dp),
            )
        }
    }
}
