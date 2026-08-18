package com.myapp.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.effect.rememberAgslShaderOrNull
import com.myapp.core.designsystem.theme.MotionTokens
import com.myapp.core.designsystem.theme.appColors

/** 条子本身的高度，与 ledger 的 `ProgressTrack` 保持一致，换掉时视觉不跳。 */
private val BAR_HEIGHT = 8.dp

/** 容器高度：条子之外的部分留给光晕扩散，不留余量的话光会被裁掉。 */
private val TRACK_BOX_HEIGHT = 28.dp

/**
 * 预算进度条光晕的 AGSL 源码（PRD 6.1）。
 *
 * 思路是「到已填充区域的距离场 + 指数衰减」，而不是画一圈模糊：
 * 先算当前像素到那条实心条（一个圆角矩形，这里按矩形近似就够了）的距离，
 * 再用 `exp(-d / falloff)` 得到亮度。指数衰减比线性衰减更像真实的光--
 * 靠近条子时衰减慢、亮度饱满，远处快速趋近于 0 而不会留下一圈生硬的边。
 *
 * 只在**已填充**的那一段发光：光晕表达的是「你已经花掉的部分」的分量感，
 * 整条都发光的话它就退化成装饰，不携带任何信息。
 */
private const val GLOW_SHADER = """
uniform float2 uSize;
uniform float uFraction;
uniform float uIntensity;
uniform float uBarHalfHeight;
layout(color) uniform half4 uColor;

half4 main(float2 coord) {
    float barW = uSize.x * uFraction;
    // 必须由外部按密度传入：着色器里的单位是**像素**，
    // 写死一个数就等于假设了某个屏幕密度，换台机器光晕的起点就错位
    float halfH = uBarHalfHeight;
    float cy = uSize.y * 0.5;

    // 到已填充矩形的距离：水平方向超出 [0, barW] 的部分 + 垂直方向超出条子厚度的部分
    float dx = max(max(0.0 - coord.x, coord.x - barW), 0.0);
    float dy = max(abs(coord.y - cy) - halfH, 0.0);
    float d = length(float2(dx, dy));

    float falloff = uSize.y * 0.20;
    float glow = exp(-d / falloff) * uIntensity;

    half a = half(clamp(glow, 0.0, 1.0));
    // 预乘：Skia 的 shader 输出按预乘处理，不乘 alpha 会在边缘发白
    return half4(uColor.rgb * a, a);
}
"""

/**
 * 带光晕的线性进度条（PRD 6.1「预算光晕」）。
 *
 * **只给预算页那条主进度条用**。统计页和分类行继续用不发光的 `ProgressTrack`--
 * 一屏十几条全在发光就成了噪声，光晕要有意义就得稀缺。
 *
 * 着色器不可用时（动效强度低于 Full，或 AGSL 编译失败）自动退化成
 * 与 `ProgressTrack` 视觉一致的普通条子，不是「效果没了页面就坏了」。
 *
 * @param fraction 已用比例，0..1（超支由调用方钳到 1 并置 [emphasized]）
 * @param emphasized 超支/接近超支。光晕更强，并开始缓慢呼吸吸引注意
 */
@Composable
fun GlowProgressTrack(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val clamped = fraction.coerceIn(0f, 1f)
    val shader = rememberAgslShaderOrNull(GLOW_SHADER)

    // 只有超支时才开呼吸动画。常驻的无限动画意味着永不停歇的重绘，
    // 144Hz 下一帧预算才 6.9ms，装饰性动画不该白占着
    val pulse = if (emphasized) {
        val transition = rememberInfiniteTransition(label = "budgetGlow")
        val value by transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(MotionTokens.DurationCounter * 2),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "budgetGlowPulse",
        )
        value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TRACK_BOX_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (shader != null && clamped > 0f) {
            val intensity = glowIntensity(clamped, emphasized) * pulse
            Canvas(modifier = Modifier.fillMaxSize()) {
                shader.setFloatUniform("uSize", size.width, size.height)
                shader.setFloatUniform("uFraction", clamped)
                shader.setFloatUniform("uIntensity", intensity)
                // dp -> px：着色器里全是像素，这个换算不能省
                shader.setFloatUniform("uBarHalfHeight", BAR_HEIGHT.toPx() / 2f)
                shader.setColorUniform("uColor", color.toArgb())
                drawRect(brush = ShaderBrush(shader))
            }
        }

        // 实心条永远画：光晕是叠加物，不是替代品
        val shape = RoundedCornerShape(percent = 50)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(shape)
                .background(MaterialTheme.appColors.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clamped)
                    .height(BAR_HEIGHT)
                    .clip(shape)
                    .background(color),
            )
        }
    }
}

/**
 * 光晕强度随已用比例递增：花得越多光越亮，超支时最亮。
 *
 * 阈值与 ledger 的 `progressColor` 同口径（>=90% 危险 / >=70% 警示），
 * 这样颜色变红和光变亮是同一时刻发生的，读起来是一件事而不是两件。
 */
private fun glowIntensity(fraction: Float, emphasized: Boolean): Float = when {
    emphasized || fraction >= 0.9f -> 0.85f
    fraction >= 0.7f -> 0.55f
    else -> 0.30f
}
