package com.myapp.feature.widget.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle

/**
 * 小组件设计令牌。与主 App 设计系统同源（core/designsystem/theme/Color.kt），
 * 但 Glance 无法读 CompositionLocal，按深/浅两套直接硬编码。
 *
 * 视觉约束（PRD 3.10）：不画 1px 描边（小组件本身就是一块卡片）、
 * 整块只用一个强调色（进度条 / 勾选态 / 倒数数字共用 Accent）、不放图标装饰。
 */
object WidgetColors {
    val Surface = Color(0xFFFFFFFF)
    val SurfaceDark = Color(0xFF262624)
    val TextPrimary = Color(0xFF191917)
    val TextPrimaryDark = Color(0xFFF5F4EF)
    val TextSecondary = Color(0xFF6B6960)
    val TextSecondaryDark = Color(0xFFA3A099)
    val TextTertiary = Color(0xFF9A978D)
    val TextTertiaryDark = Color(0xFF75726B)
    val Accent = Color(0xFFD97757)
    val AccentDark = Color(0xFFE08A6C)
    val Track = Color(0xFFE8E5DC)
    val TrackDark = Color(0xFF3A3937)
    val Success = Color(0xFF5C8A5C)
    val SuccessDark = Color(0xFF7FA97F)
}

/** 单套配色快照，随系统深/浅模式取用。 */
data class WidgetPalette(
    val isDark: Boolean,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val track: Color,
    val success: Color,
)

fun Context.widgetPalette(): WidgetPalette {
    val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    return if (dark) {
        WidgetPalette(
            isDark = true,
            surface = WidgetColors.SurfaceDark,
            textPrimary = WidgetColors.TextPrimaryDark,
            textSecondary = WidgetColors.TextSecondaryDark,
            textTertiary = WidgetColors.TextTertiaryDark,
            accent = WidgetColors.AccentDark,
            track = WidgetColors.TrackDark,
            success = WidgetColors.SuccessDark,
        )
    } else {
        WidgetPalette(
            isDark = false,
            surface = WidgetColors.Surface,
            textPrimary = WidgetColors.TextPrimary,
            textSecondary = WidgetColors.TextSecondary,
            textTertiary = WidgetColors.TextTertiary,
            accent = WidgetColors.Accent,
            track = WidgetColors.Track,
            success = WidgetColors.Success,
        )
    }
}

object WidgetTextStyles {
    /** 标题 / 日期行。 */
    val title = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    /** 大金额数字。Glance 不支持自定义字体，用系统衬线近似 Noto Serif SC。 */
    val amount = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Serif)
    /** 倒数大数字。 */
    val countdown = TextStyle(fontSize = 38.sp, fontFamily = FontFamily.Serif)
    /** 待办条目正文。 */
    val body = TextStyle(fontSize = 13.sp)
    /** 次要说明。 */
    val caption = TextStyle(fontSize = 12.sp)
    /** 元信息 / 时刻。 */
    val label = TextStyle(fontSize = 11.sp)
}
