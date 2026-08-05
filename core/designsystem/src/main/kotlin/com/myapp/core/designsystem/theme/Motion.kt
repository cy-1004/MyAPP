package com.myapp.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntOffset

/**
 * 动效令牌（PRD 6.3）。
 *
 * 为什么必须集中定义：144Hz 屏帧预算只有 6.9ms，各处随手写 tween(300)
 * 会让全局手感不一致，且很难统一降级。所有动画一律引用这里。
 *
 * 底座说明：Material 3 Expressive 提供了 MotionScheme（基于弹簧物理的全局动效方案）。
 * 待项目升级到含该 API 的 material3 版本后，应在 MyAppTheme 中接入
 * `MaterialTheme(motionScheme = MotionScheme.expressive())`，本对象只保留项目特有的补充令牌。
 */
object MotionTokens {

    // ---------- 时长基准 ----------
    const val DurationMicro = 120      // 微反馈：按压、勾选
    const val DurationStandard = 280   // 常规过渡：淡入淡出、展开收起
    const val DurationEmphasized = 450 // 大位移：共享元素、页面转场
    const val DurationCounter = 700    // 数值滚动：金额、进度条
    const val StaggerDelayMs = 40      // 列表错峰入场

    // ---------- 缓动 ----------
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // ---------- 弹簧：入场带轻微过冲，是「高级感」的主要来源 ----------
    fun <T> enterSpring() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 位移类专用（IntOffset 需要 visibilityThreshold）。 */
    fun enterSpringOffset() = spring<IntOffset>(
        dampingRatio = 0.8f,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold,
    )

    /** 退场要快——用户已经做完决定了，不该等动画。 */
    fun <T> exitTween() = tween<T>(
        durationMillis = 180,
        easing = FastOutLinearInEasing,
    )

    fun <T> standardTween() = tween<T>(
        durationMillis = DurationStandard,
        easing = FastOutSlowInEasing,
    )

    fun <T> emphasizedTween() = tween<T>(
        durationMillis = DurationEmphasized,
        easing = EmphasizedEasing,
    )

    /** 金额滚动、预算进度推进。 */
    fun <T> counterTween() = tween<T>(
        durationMillis = DurationCounter,
        easing = EmphasizedEasing,
    )
}

/**
 * 动效强度（PRD 6.4）。
 * 用户可在设置里三档切换；系统「移除动画」无障碍设置会强制降为 None。
 */
enum class MotionLevel {
    /** 完整：含毛玻璃、着色器、粒子。 */
    Full,

    /** 精简：保留位移与淡入淡出，去掉高开销效果。 */
    Reduced,

    /** 关闭：全部瞬时切换。 */
    None;

    val enableBlur: Boolean get() = this == Full
    val enableShader: Boolean get() = this == Full
    val enableConfetti: Boolean get() = this == Full
    val enableTransitions: Boolean get() = this != None

    /** 按当前档位缩放时长，None 档直接归零。 */
    fun scale(durationMillis: Int): Int = when (this) {
        Full -> durationMillis
        Reduced -> (durationMillis * 0.7f).toInt()
        None -> 0
    }
}

val LocalMotionLevel = staticCompositionLocalOf { MotionLevel.Full }
