package com.myapp.feature.widget.anniversary

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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.myapp.core.common.time.AnniversaryCalculator
import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.LunarCalendar
import com.myapp.core.database.model.AnniversaryEntity
import com.myapp.feature.widget.WidgetIntents
import com.myapp.feature.widget.data.WidgetScreens
import com.myapp.feature.widget.di.WidgetDataProvider
import com.myapp.feature.widget.ui.WidgetPalette
import com.myapp.feature.widget.ui.WidgetTextStyles
import com.myapp.feature.widget.ui.widgetPalette
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

/**
 * W4 纪念日倒数（2×2，PRD 3.10）。
 *
 * 盯哪个纪念日，优先级链与 App 内一致（PRD 3.10 定稿）：
 *   1. 配置页选定的一条（WidgetPrefsStore）；
 *   2. 用户置顶的一条；
 *   3. 距今天最近的下一个（不分类型）；
 *   4. 都没有下一次了 → 最早创建的那条；
 *   5. 一条都没有 → 引导卡片「添加纪念日」。
 *
 * 「下一次是哪天」的日期数学在 :core:common 的 [AnniversaryCalculator]，
 * 这里只做领域映射（widget 不能依赖 :feature:anniversary）。
 */
class AnniversaryCountdownWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDataProvider::class.java,
        )
        val dao = entry.anniversaryDao()
        val prefs = entry.widgetPrefsStore()
        val today = AppTime.today()
        val all = dao.getAllActive().map { it.toCountdown(today) }
        val appWidgetId = id.toString().toIntOrNull()
        val selectedId = appWidgetId?.let { prefs.w4SelectedAnniversaryId(it).first() }
        val item = selectedId?.let { sid -> all.firstOrNull { it.id == sid } }
            ?: all.firstOrNull { it.pinned }
            ?: all.filter { it.daysUntil != null }.minByOrNull { it.daysUntil!! }
            ?: all.minByOrNull { it.createdAt }
        provideContent {
            CountdownContent(item)
        }
    }
}

class AnniversaryCountdownWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget
        get() = AnniversaryCountdownWidget()
}

@Composable
private fun CountdownContent(item: CountdownItem?) {
    val palette = LocalContext.current.widgetPalette()
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(palette.surface)
            .clickable(WidgetIntents.openScreenAction(context, WidgetScreens.ANNIVERSARY))
            .padding(12.dp),
    ) {
        if (item == null) {
            GuideCard(palette)
        } else {
            CountdownBody(item, palette)
        }
    }
}

@Composable
private fun GuideCard(palette: WidgetPalette) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = "添加纪念日",
            style = WidgetTextStyles.title.copy(color = ColorProvider(palette.textPrimary)),
        )
        Text(
            text = "记录重要的日子",
            style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textTertiary)),
            modifier = GlanceModifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CountdownBody(item: CountdownItem, palette: WidgetPalette) {
    val next = item.daysUntil
    val cumulative = item.repeatType == CUMULATIVE
    val bigText = when {
        next == null -> item.elapsedDays.toString() // 过完的一次性：已过去
        next == 0L -> "今天"
        else -> next.toString()
    }
    val unitText = when {
        next == 0L -> ""
        next != null -> "天后"
        else -> "天前"
    }
    val headline = when {
        cumulative -> "第 ${item.elapsedDays + 1} 天"
        else -> item.dateLine()
    }

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = bigText,
                style = WidgetTextStyles.countdown.copy(color = ColorProvider(palette.accent)),
            )
            if (unitText.isNotEmpty()) {
                Text(
                    text = unitText,
                    style = WidgetTextStyles.caption.copy(
                        color = ColorProvider(palette.textSecondary),
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(start = 4.dp, top = 10.dp),
                )
            }
        }
        Text(
            text = item.title,
            style = WidgetTextStyles.title.copy(color = ColorProvider(palette.textPrimary)),
            maxLines = 1,
            modifier = GlanceModifier.padding(top = 2.dp),
        )
        Text(
            text = headline,
            style = WidgetTextStyles.caption.copy(color = ColorProvider(palette.textTertiary)),
            modifier = GlanceModifier.padding(top = 2.dp),
        )
    }
}

private data class CountdownItem(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val isLunar: Boolean,
    val repeatType: String,
    val daysUntil: Long?,
    val elapsedDays: Long,
    val pinned: Boolean,
    val createdAt: Long,
) {
    /** 日期行：公历 "8月20日 · 每年"；农历显示农历月日，如 "闰四月初八 · 每年"。 */
    fun dateLine(): String {
        val dateText = if (isLunar && LunarCalendar.isSupported(date)) {
            "农历${LunarCalendar.fromSolar(date).format()}"
        } else {
            date.format(AppFormatters.date)
        }
        val repeat = when (repeatType) {
            ONCE -> "只有一次"
            CUMULATIVE -> "累计"
            else -> "每年"
        }
        return "$dateText · $repeat"
    }
}

private const val ONCE = "ONCE"
private const val CUMULATIVE = "CUMULATIVE"

/** 与 AnniversaryRepository.toDomain 同一套「下一次」推算（widget 不能依赖 feature）。 */
private fun AnniversaryEntity.toCountdown(today: LocalDate): CountdownItem {
    val origin = LocalDate.ofEpochDay(date)
    val elapsed = ChronoUnit.DAYS.between(origin, today)
    val milestone = if (repeatType == CUMULATIVE) {
        AnniversaryCalculator.nextMilestone(origin, today, elapsed)
    } else {
        null
    }
    val next = when (repeatType) {
        CUMULATIVE -> milestone?.date
        ONCE -> origin.takeIf { !it.isBefore(today) }
        else -> AnniversaryCalculator.nextYearlyDate(origin, today, isLunar)
    }
    return CountdownItem(
        id = id,
        title = title,
        date = origin,
        isLunar = isLunar,
        repeatType = repeatType,
        daysUntil = next?.let { ChronoUnit.DAYS.between(today, it) },
        elapsedDays = elapsed,
        pinned = pinned,
        createdAt = createdAt,
    )
}
