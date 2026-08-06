package com.myapp.core.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排版约束的守卫测试。
 *
 * 起因是一次真机崩溃：`bodyLarge` 用了 `0.01.em`、`bodySmall` 走 Material 默认的 `0.4.sp`，
 * OutlinedTextField 的 label 在聚焦动画里对这两个样式做 `TextStyle.lerp`，
 * 直接抛 `Cannot perform operation for Em and Sp`——**每一个带 label 的输入框都会崩**。
 *
 * 这类问题编译期发现不了、Preview 里也不一定触发（要聚焦才插值），
 * 所以用测试把规则钉死。
 */
class TypographyTest {

    private val allStyles: List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to AppTypography.displayLarge,
        "displayMedium" to AppTypography.displayMedium,
        "displaySmall" to AppTypography.displaySmall,
        "headlineLarge" to AppTypography.headlineLarge,
        "headlineMedium" to AppTypography.headlineMedium,
        "headlineSmall" to AppTypography.headlineSmall,
        "titleLarge" to AppTypography.titleLarge,
        "titleMedium" to AppTypography.titleMedium,
        "titleSmall" to AppTypography.titleSmall,
        "bodyLarge" to AppTypography.bodyLarge,
        "bodyMedium" to AppTypography.bodyMedium,
        "bodySmall" to AppTypography.bodySmall,
        "labelLarge" to AppTypography.labelLarge,
        "labelMedium" to AppTypography.labelMedium,
        "labelSmall" to AppTypography.labelSmall,
    )

    @Test
    fun `letterSpacing 一律用 sp 不能用 em`() {
        allStyles.forEach { (name, style) ->
            val spacing = style.letterSpacing
            assertTrue(
                "$name 的 letterSpacing 用了 ${spacing.type}，" +
                    "与其他样式插值时会抛 Cannot perform operation for Em and Sp",
                spacing.type == TextUnitType.Unspecified || spacing.type == TextUnitType.Sp,
            )
        }
    }

    @Test
    fun `fontSize 一律用 sp`() {
        allStyles.forEach { (name, style) ->
            assertTrue(
                "$name 的 fontSize 用了 ${style.fontSize.type}",
                style.fontSize.type == TextUnitType.Unspecified || style.fontSize.type == TextUnitType.Sp,
            )
        }
    }

    /**
     * 15 个样式全部显式定义。留空的会退回 Material 默认字体族，
     * 正式中文字体接入后就会出现「一半页面没换字体」。
     */
    @Test
    fun `所有样式都指定了字体族`() {
        allStyles.forEach { (name, style) ->
            assertNotNull("$name 没有指定 fontFamily", style.fontFamily)
            assertTrue(
                "$name 的字体族既不是衬线也不是无衬线，多半是漏定义了",
                style.fontFamily == SerifFamily || style.fontFamily == SansFamily,
            )
        }
    }

    @Test
    fun `标题用衬线 正文用无衬线`() {
        listOf(
            AppTypography.displaySmall,
            AppTypography.headlineMedium,
            AppTypography.headlineSmall,
        ).forEach { assertEquals(SerifFamily, it.fontFamily) }

        listOf(
            AppTypography.bodyLarge,
            AppTypography.bodyMedium,
            AppTypography.labelLarge,
        ).forEach { assertEquals(SansFamily, it.fontFamily) }
    }
}
