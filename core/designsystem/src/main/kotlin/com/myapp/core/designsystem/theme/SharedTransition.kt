package com.myapp.core.designsystem.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 共享元素转场的两个作用域（PRD 6.2）。
 *
 * **为什么走 CompositionLocal 而不是参数**：`SharedTransitionScope` 由 `:app` 的
 * `SharedTransitionLayout` 提供，`AnimatedVisibilityScope` 由每个导航目的地提供，
 * 而真正要用它们的是各 feature 的屏幕。feature 之间不许互相依赖、
 * 也不该为了一个动效把作用域一层层透传进每个 Composable 的参数表
 * （那会污染所有中间层的签名）。放在 `:core:designsystem` 里当环境值最干净。
 *
 * 两个都默认 null：**没有它们时所有共享元素修饰符自动退化成无操作**。
 * 这一点很重要--Compose Preview、单元测试、以及还没接入的导航目的地
 * 都不在 `SharedTransitionLayout` 里，不能因此崩掉或报错。
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** 当前导航目的地的过渡作用域。由 `:core:ui` 的 `sharedElementComposable` 提供。 */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * 把当前元素标记为共享**容器**：两端内容不一样，只让边界（位置 + 尺寸）连续变形。
 *
 * 典型用法是「列表卡片 -> 详情页的主体区域」：卡片里是标题 + 摘要，
 * 详情页里是输入框，内容完全不同，但用户需要看到「我点的那张卡片长成了这一页」。
 *
 * 内容两端**完全一致**时（同一张图、同一行标题）用 [sharedElementOrNothing]，
 * 它不会做缩放拉伸，视觉上更干净。
 *
 * @param key 两端必须一致。建议由 feature 内部的辅助函数生成，避免两处手写字符串写歪。
 */
@Composable
fun Modifier.sharedBoundsOrNothing(key: Any): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val duration = sharedTransitionDurationOrZero() ?: return this
    return with(sharedScope) {
        this@sharedBoundsOrNothing.sharedBounds(
            rememberSharedContentState(key),
            animatedScope,
            boundsTransform = { _, _ -> tween(duration, easing = MotionTokens.EmphasizedEasing) },
        )
    }
}

/**
 * 把当前元素标记为共享**元素**：两端是同一个东西（同一张封面图、同一行标题），
 * 位置和尺寸连续变化，内容不做缩放。
 *
 * 内容不一致时用它会显得突兀（一端的内容硬套到另一端的尺寸上），那种情况用
 * [sharedBoundsOrNothing]。
 */
@Composable
fun Modifier.sharedElementOrNothing(key: Any): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    val duration = sharedTransitionDurationOrZero() ?: return this
    return with(sharedScope) {
        this@sharedElementOrNothing.sharedElement(
            rememberSharedContentState(key),
            animatedScope,
            boundsTransform = { _, _ -> tween(duration, easing = MotionTokens.EmphasizedEasing) },
        )
    }
}

/**
 * 当前动效强度下共享元素该用多长时间；[MotionLevel.None] 档返回 null 表示「整个效果都别做」。
 *
 * 不是「时长设成 0」而是彻底跳过：时长为 0 的共享元素仍然会把内容提到
 * overlay 层再放回去，白付一份开销，还可能在关闭动效的档位下闪一下。
 */
@Composable
private fun sharedTransitionDurationOrZero(): Int? {
    val motionLevel = LocalMotionLevel.current
    if (!motionLevel.enableTransitions) return null
    return motionLevel.scale(MotionTokens.DurationEmphasized)
}
