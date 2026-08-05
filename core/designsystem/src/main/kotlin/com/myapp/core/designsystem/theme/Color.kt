package com.myapp.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 色板（PRD 5.1）。
 *
 * 三条不可动摇的规则：
 *   1. 不用纯白 #FFFFFF 作背景，不用纯黑 #000000 作深色底——一律暖调；
 *   2. 层级靠「底色差 + 1px 描边」表达，不靠阴影；
 *   3. 一屏最多一个 Accent 焦点。
 */

// ---------- 浅色 ----------
val Base = Color(0xFFFAF9F5)          // 页面底色，暖米白
val Surface = Color(0xFFFFFFFF)       // 卡片
val SurfaceVariant = Color(0xFFF3F1EA) // 次级容器：输入框底、选中态
val BorderLight = Color(0xFFE8E5DC)   // 1px 描边
val TextPrimary = Color(0xFF191917)
val TextSecondary = Color(0xFF6B6960)
val TextTertiary = Color(0xFF9A978D)

// ---------- 深色（AMOLED 友好的暖深色，非纯黑）----------
val BaseDark = Color(0xFF1F1E1D)
val SurfaceDark = Color(0xFF262624)
val SurfaceVariantDark = Color(0xFF302F2C)
val BorderDark = Color(0xFF3A3937)
val TextPrimaryDark = Color(0xFFF5F4EF)
val TextSecondaryDark = Color(0xFFA3A099)
val TextTertiaryDark = Color(0xFF75726B)

// ---------- 强调色：陶土橙 ----------
val Accent = Color(0xFFD97757)
val AccentDark = Color(0xFFE08A6C)
val AccentContainer = Color(0xFFF7E6DF)
val AccentContainerDark = Color(0xFF4A2E24)

// ---------- 语义色 ----------
val Success = Color(0xFF5C8A5C)
val SuccessDark = Color(0xFF7FA97F)
val Warning = Color(0xFFC9A227)   // 预算 70~90%
val WarningDark = Color(0xFFD9B84A)
val Danger = Color(0xFFC0553C)    // 超支、逾期、删除
val DangerDark = Color(0xFFD4674C)

/**
 * 分类色板：低饱和莫兰迪色系，与 Accent 同色温。
 * 用于记账分类饼图、标签——**绝不用彩虹色**，那是廉价感的主要来源。
 */
object CategoryPalette {
    val colors = listOf(
        Color(0xFFD97757), // 陶土
        Color(0xFF8C9A7E), // 橄榄
        Color(0xFF7E93A8), // 雾蓝
        Color(0xFFC2A25E), // 芥末
        Color(0xFFA88C9A), // 藕荷
        Color(0xFF6F8F86), // 松绿
        Color(0xFFB5806B), // 赭石
        Color(0xFF8A8577), // 灰褐
    )

    /** 按稳定 key 取色，保证同一分类每次颜色一致。 */
    fun colorFor(key: String): Color =
        colors[(key.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % colors.size]
}

/** 预算进度条颜色：随消耗比例过渡（PRD 3.6.1）。 */
fun budgetColor(ratio: Float, dark: Boolean): Color = when {
    ratio >= 1.0f -> if (dark) DangerDark else Danger
    ratio >= 0.9f -> if (dark) DangerDark else Danger
    ratio >= 0.7f -> if (dark) WarningDark else Warning
    else -> if (dark) AccentDark else Accent
}
