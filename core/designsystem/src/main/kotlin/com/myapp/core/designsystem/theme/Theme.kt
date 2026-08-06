package com.myapp.core.designsystem.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 形状（PRD 5.3）：圆角适中，不做胶囊化。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // 标签
    small = RoundedCornerShape(12.dp),       // 按钮、输入框
    medium = RoundedCornerShape(16.dp),      // 卡片（主力）
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),  // 底部弹层
)

/** 间距基准：4dp 栅格。 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp   // 卡片间距
    val lg = 16.dp   // 卡片内边距
    val xl = 20.dp   // 页面左右边距
    val xxl = 32.dp
}

/**
 * Material3 的 ColorScheme 装不下本项目的语义色（描边、警示、成功、三级文字），
 * 用一个扩展色对象补齐，通过 CompositionLocal 提供。
 */
data class AppColors(
    val border: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean,
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(BorderLight, TextSecondary, TextTertiary, Success, Warning, Danger, false)
}

private val OnAccentLight = Color(0xFFFFFFFF)
private val OnAccentDark = Color(0xFF1F1E1D)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = OnAccentLight,
    primaryContainer = AccentContainer,
    onPrimaryContainer = TextPrimary,
    background = Base,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = Danger,
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = OnAccentDark,
    primaryContainer = AccentContainerDark,
    onPrimaryContainer = TextPrimaryDark,
    background = BaseDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = DangerDark,
)

/**
 * 全局主题。
 *
 * 注意这里**不接系统动态取色**（Material You）。默认保持 Claude 风格的品牌感，
 * 动态取色作为设置项后置——这是刻意的设计决定，不是遗漏。
 */
@Composable
fun MyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionLevel: MotionLevel = rememberSystemMotionLevel(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) {
        AppColors(BorderDark, TextSecondaryDark, TextTertiaryDark, SuccessDark, WarningDark, DangerDark, true)
    } else {
        AppColors(BorderLight, TextSecondary, TextTertiary, Success, Warning, Danger, false)
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalMotionLevel provides motionLevel,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/**
 * 尊重系统的「移除动画」无障碍设置（PRD 6.4）。
 * 系统把动画缩放调成 0 时，全部动效降为瞬时。
 */
@Composable
fun rememberSystemMotionLevel(userPreference: MotionLevel = MotionLevel.Full): MotionLevel {
    val context = LocalContext.current
    return remember(userPreference) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )
        if (scale == 0f) MotionLevel.None else userPreference
    }
}

/** 便捷访问扩展色。 */
val MaterialTheme.appColors: AppColors
    @Composable get() = LocalAppColors.current
