package com.myapp.feature.widget.todayexpense

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.BudgetCycle
import com.myapp.core.database.model.BudgetEntity
import com.myapp.core.designsystem.theme.budgetColor
import com.myapp.feature.widget.WidgetIntents
import com.myapp.feature.widget.data.WidgetScreens
import com.myapp.feature.widget.di.WidgetDataProvider
import com.myapp.feature.widget.ui.WidgetPalette
import com.myapp.feature.widget.ui.WidgetTextStyles
import com.myapp.feature.widget.ui.widgetPalette
import com.myapp.feature.widget.ui.yuanGrouped
import com.myapp.feature.widget.ui.yuanWithSymbol
import dagger.hilt.android.EntryPointAccessors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * W2 今日支出（2×2，PRD 3.10）。
 *
 * 今日支出金额 + 本期预算进度条。进度条用 Box 模拟（Glance 无
 * LinearProgressIndicator）：定宽底轨 + 按比例填充的强调色块。
 * 未设预算时只显示金额与「未设预算」提示，不画进度。
 */
class TodayExpenseWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataProvider::class.java,
        )
        val today = AppTime.todayRange()
        val todayExpense = entry.transactionDao().sumExpenseInRange(today.first, today.last + 1)
        val budget = entry.budgetDao().getCurrent()
        val cycleExpense = budget?.let { b ->
            val cycle = BudgetCycle.currentCycleRange(b.cycleStartDay)
            entry.transactionDao().sumExpenseInRange(cycle.first, cycle.last + 1)
        }
        provideContent {
            TodayExpenseContent(todayExpense, budget, cycleExpense)
        }
    }
}

class TodayExpenseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = TodayExpenseWidget()
}

@Composable
private fun TodayExpenseContent(todayExpense: Long, budget: BudgetEntity?, cycleExpense: Long?) {
    val palette = LocalContext.current.widgetPalette()
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.surface)
            .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.LEDGER))
            .padding(12.dp),
    ) {
        Text(
            text = "今日支出",
            style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textTertiary)),
        )
        Text(
            text = todayExpense.yuanWithSymbol(),
            style = WidgetTextStyles.amount.copy(color = ColorProvider(palette.accent)),
            modifier = GlanceModifier.padding(top = 2.dp),
        )
        Spacer(GlanceModifier.defaultWeight())
        if (budget != null && cycleExpense != null) {
            BudgetBar(palette, budget, cycleExpense)
        } else {
            Text(
                text = "未设预算",
                style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textTertiary)),
            )
        }
    }
}

@Composable
private fun BudgetBar(palette: WidgetPalette, budget: BudgetEntity, cycleExpense: Long) {
    val ratio = if (budget.totalAmount > 0) {
        cycleExpense.toFloat() / budget.totalAmount.toFloat()
    } else {
        1f
    }
    val barColor = budgetColor(ratio, palette.isDark)
    val percent = (ratio * 100).roundToInt().coerceAtLeast(0)
    val remaining = budget.totalAmount - cycleExpense

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 定宽轨道（84dp）+ 按比例填充，Glance 1.1 无 fillMaxWidth(fraction)
            Box(
                modifier = GlanceModifier
                    .width(84.dp)
                    .height(6.dp)
                    .background(palette.track),
            ) {
                Box(
                    modifier = GlanceModifier
                        .width((84f * ratio.coerceIn(0f, 1f)).dp)
                        .height(6.dp)
                        .background(barColor),
                    content = {},
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "$percent%",
                style = WidgetTextStyles.label.copy(color = ColorProvider(barColor)),
            )
        }
        Text(
            text = if (remaining >= 0) "剩余 ${remaining.yuanGrouped()}" else "已超支 ${abs(remaining).yuanGrouped()}",
            style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textSecondary)),
            modifier = GlanceModifier.padding(top = 4.dp),
        )
    }
}
