package com.myapp.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.effect.rememberAgslShaderOrNull

/** 指示器占位高度。比默认圆形指示器高一些，液滴才有下坠的余地。 */
private val INDICATOR_HEIGHT = 72.dp

/**
 * 液态下拉刷新指示器的 AGSL 源码（PRD 6.1）。
 *
 * 用 metaball（融球）做「一滴水被拉长、快断开」的观感：两个圆各自贡献一个
 * `r / distance` 的场强，相加后取阈值。两球靠近时场强叠加，边界会自然连成
 * 一个带腰身的形状；拉远了腰身变细直至断开--这正是液体被拉伸的样子，
 * 用普通的 Canvas 画不出来（要么画两个圆，要么手算贝塞尔，都很假）。
 *
 * 坐标单位是像素，`uSize` 传控件尺寸。
 */
private const val LIQUID_SHADER = """
uniform float2 uSize;
uniform float uProgress;
uniform float uTime;
layout(color) uniform half4 uColor;

// 单个融球的场强：越靠近球心值越大
float field(float2 p, float2 center, float radius) {
    return radius / max(length(p - center), 0.0001);
}

half4 main(float2 coord) {
    float cx = uSize.x * 0.5;
    float topY = uSize.y * 0.22;

    // 上球固定在顶部；下球随下拉进度下坠，并带一点左右摆动，
    // 摆动幅度乘 uProgress，刚开始拉的时候不晃，拉到底晃得明显
    float wobble = sin(uTime * 6.2831) * 5.0 * uProgress;
    float2 upper = float2(cx, topY);
    float2 lower = float2(cx + wobble, topY + uSize.y * 0.5 * uProgress);

    // 下球越往下越小：体积被「腰身」带走了，符合液体拉伸的直觉
    float rUpper = uSize.y * 0.15;
    float rLower = rUpper * (1.0 - 0.45 * uProgress);

    float f = field(coord, upper, rUpper) + field(coord, lower, rLower);

    // 阈值化。上下界留一点过渡带当抗锯齿，直接 step 会有硬锯齿
    half a = half(smoothstep(0.95, 1.20, f));

    // 返回预乘色：Skia 的 shader 输出按预乘处理，不乘 alpha 会在半透明边缘发亮
    return half4(uColor.rgb * a, uColor.a * a);
}
"""

/**
 * 液态下拉刷新指示器（PRD 6.1）。
 *
 * **着色器不可用时自动退化**成普通的圆形进度指示器--
 * 用户可能把动效强度调到了 Full 以下，AGSL 也可能在某些设备上编译不过
 * （见 [rememberAgslShaderOrNull]）。装饰性效果不能是「有它才能用」。
 *
 * @param distanceFraction 下拉距离占触发阈值的比例，0..1（超过 1 由调用方钳住）
 * @param isRefreshing 已触发、正在刷新
 */
@Composable
fun LiquidRefreshIndicator(
    distanceFraction: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    // 刷新中一律按满进度画；否则跟随下拉距离
    val progress = if (isRefreshing) 1f else distanceFraction.coerceIn(0f, 1f)

    // **静止时一个像素都不画**。少了这一步，uProgress = 0 会让上下两球重合成一个实心圆
    // 常驻在列表顶部挡住文章标题--真机上一眼就看出来了，编译期完全发现不了
    if (progress <= 0.001f) return

    val shader = rememberAgslShaderOrNull(LIQUID_SHADER)
    if (shader == null) {
        FallbackRefreshIndicator(
            distanceFraction = progress,
            isRefreshing = isRefreshing,
            modifier = modifier,
            color = color,
        )
        return
    }

    // 刷新中让液滴持续晃动；只是下拉还没松手时，摆动相位跟着手指走就够了，
    // 不需要额外的时间驱动（省一份持续重绘）
    val transition = rememberInfiniteTransition(label = "liquid")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liquidTime",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(INDICATOR_HEIGHT)
            // 从顶边「探」出来：进度 0 时整块推到可视区之上，进度 1 时完全落位。
            // 不做这个位移的话，液滴会凭空出现在列表中间，没有「从边缘垂下来」的观感
            .graphicsLayer { translationY = -(1f - progress) * size.height },
    ) {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uProgress", progress)
        shader.setFloatUniform("uTime", if (isRefreshing) time else 0f)
        shader.setColorUniform("uColor", color.toArgb())
        drawRect(brush = ShaderBrush(shader))
    }
}

/** 着色器不可用时的兜底：标准圆形指示器，行为与 Material 默认一致。 */
@Composable
private fun FallbackRefreshIndicator(
    distanceFraction: Float,
    isRefreshing: Boolean,
    modifier: Modifier,
    color: Color,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(INDICATOR_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = color)
        } else {
            CircularProgressIndicator(
                progress = { distanceFraction.coerceIn(0f, 1f) },
                modifier = Modifier.size(28.dp),
                color = color,
            )
        }
    }
}
