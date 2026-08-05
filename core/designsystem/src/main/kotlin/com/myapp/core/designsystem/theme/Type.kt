package com.myapp.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 排版（PRD 5.2）。
 *
 * 字体族目前先用系统默认占位。接入自定义字体时：
 *   1. 把 ttf 放到 core/designsystem/src/main/res/font/
 *   2. 替换下面两个 FontFamily
 * 计划：标题用衬线（Noto Serif SC），正文用无衬线（MiSans，免费商用）。
 * 衬线标题是「文档感/高级感」的主要来源，不要省。
 */
val SerifFamily = FontFamily.Serif   // TODO 替换为 Noto Serif SC
val SansFamily = FontFamily.Default  // TODO 替换为 MiSans

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

/** 等宽数字：金额、倒数天数必须用，否则数字变化时宽度会跳。 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

val AppTypography = Typography(
    // ---- 标题：衬线 ----
    displaySmall = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineMedium = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = lineHeightStyle,
    ),
    headlineSmall = TextStyle(
        fontFamily = SerifFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    // ---- 正文 / UI：无衬线 ----
    titleMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        lineHeightStyle = lineHeightStyle,
    ),
    bodyLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = lineHeightStyle,
    ),
    bodyMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.01.em,
        lineHeightStyle = lineHeightStyle,
    ),
    labelLarge = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)
