package com.myapp.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 排版（PRD 5.2）。
 *
 * 字体族目前先用系统默认占位。接入自定义字体时：
 *   1. 把 ttf 放到 core/designsystem/src/main/res/font/
 *   2. 替换下面两个 FontFamily
 * 计划：标题用衬线（Noto Serif SC），正文用无衬线（MiSans，免费商用）。
 * 衬线标题是「文档感/高级感」的主要来源，不要省。
 *
 * **两条硬性约束，改这个文件前必读**（有 TypographyTest 守着）：
 *
 * 1. `letterSpacing` **一律用 `.sp`，绝不能用 `.em`**。
 *    Compose 在组件间做样式插值时（典型场景：OutlinedTextField 的 label
 *    在聚焦/失焦之间从 bodyLarge 过渡到 bodySmall）会调用 `TextStyle.lerp`，
 *    而 `lerp` 无法在 Em 与 Sp 之间换算，会直接抛
 *    `IllegalArgumentException: Cannot perform operation for Em and Sp` 崩掉整个页面。
 *    只要有一个样式用了 em、与它配对的另一个用了 sp，就会踩中。
 *
 * 2. **15 个样式全部显式定义**，不留给 Material 默认值。
 *    留空的样式会退回 Material 的默认字体族与 sp 单位，
 *    既让上面那条约束变得不可控，也会在正式字体接入后出现「一半页面没换字体」。
 */
val SerifFamily = FontFamily.Serif   // TODO 替换为 Noto Serif SC
val SansFamily = FontFamily.Default  // TODO 替换为 MiSans

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** 等宽数字：金额、倒数天数必须用，否则数字变化时宽度会跳。 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

private fun serif(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = SerifFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = lineHeightStyle,
)

private fun sans(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = SansFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = lineHeightStyle,
)

val AppTypography = Typography(
    // ---- 大标题：衬线。中文不用 Bold 700 以上，会糊成一团 ----
    displayLarge = serif(size = 40, lineHeight = 48, letterSpacing = 0.4),
    displayMedium = serif(size = 36, lineHeight = 44, letterSpacing = 0.35),
    displaySmall = serif(size = 32, lineHeight = 40, letterSpacing = 0.3),
    headlineLarge = serif(size = 28, lineHeight = 36, letterSpacing = 0.28),
    headlineMedium = serif(size = 24, lineHeight = 32, letterSpacing = 0.24),
    headlineSmall = serif(size = 20, lineHeight = 28, weight = FontWeight.Medium),

    // ---- 标题 / UI：无衬线 ----
    titleLarge = sans(size = 20, lineHeight = 28, weight = FontWeight.SemiBold),
    titleMedium = sans(size = 16, lineHeight = 24, weight = FontWeight.SemiBold),
    titleSmall = sans(size = 14, lineHeight = 20, weight = FontWeight.Medium),

    // ---- 正文 ----
    bodyLarge = sans(size = 16, lineHeight = 24, letterSpacing = 0.16),
    bodyMedium = sans(size = 14, lineHeight = 21, letterSpacing = 0.14),
    bodySmall = sans(size = 12, lineHeight = 18, letterSpacing = 0.12),

    // ---- 标签 ----
    labelLarge = sans(size = 14, lineHeight = 20, weight = FontWeight.Medium),
    labelMedium = sans(size = 12, lineHeight = 16),
    labelSmall = sans(size = 11, lineHeight = 16),
)
