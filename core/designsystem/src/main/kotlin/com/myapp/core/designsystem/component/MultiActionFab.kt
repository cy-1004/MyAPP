package com.myapp.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.theme.LocalMotionLevel
import com.myapp.core.designsystem.theme.MotionTokens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class FabAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/**
 * 全局多动作 FAB（PRD 3.11）。
 *
 * 主按钮点击展开 [actions] 个子动作，沿 -45° ~ -135° 弧线扇形展开（stagger 30ms）。
 * 展开时背景 Haze 模糊 + 半透明遮罩，点击遮罩收起。
 *
 * 状态提升：[expanded] 由调用方持有，调用方同时负责 BackHandler 拦截返回键。
 * 这样组件本身不依赖 activity-compose，保持 :core:designsystem 纯 UI。
 *
 * 动效降级时（MotionLevel.None）子按钮瞬时显示，无弹簧动画。
 */
@Composable
fun MultiActionFab(
    actions: List<FabAction>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val motionLevel = LocalMotionLevel.current
    val enableBlur = motionLevel.enableBlur
    val bottomPadding = LocalBottomBarHeight.current + 16.dp

    val progress = remember(actions.size) { List(actions.size) { Animatable(0f) } }

    LaunchedEffect(expanded) {
        if (motionLevel.enableTransitions) {
            // 并发动画：4 个按钮同时启动，各自带 stagger delay，错峰但重叠展开。
            // 之前用 forEachIndexed 顺序 suspend，按钮逐个出现，观感"卡顿"。
            coroutineScope {
                actions.forEachIndexed { index, _ ->
                    launch {
                        delay(index * 15L)
                        progress[index].animateTo(
                            targetValue = if (expanded) 1f else 0f,
                            animationSpec = tween(
                                durationMillis = 220,
                                easing = MotionTokens.EmphasizedEasing,
                            ),
                        )
                    }
                }
            }
        } else {
            actions.forEachIndexed { index, _ ->
                progress[index].snapTo(if (expanded) 1f else 0f)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 背景遮罩
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            val maskModifier = Modifier
                .fillMaxSize()
                .let {
                    if (enableBlur) {
                        it.hazeEffect(
                            hazeState,
                            style = HazeStyle(
                                backgroundColor = Color.Black,
                                tints = emptyList(),
                                blurRadius = 20.dp,
                            ),
                        )
                    } else {
                        it
                    }
                }
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onExpandedChange(false) })
                }
            Box(modifier = maskModifier)
        }

        val density = LocalDensity.current
        val radiusPx = with(density) { 120.dp.toPx() }

        // 子按钮（弧线扇形展开）
        actions.forEachIndexed { index, action ->
            val p = progress[index].value
            val angleRad = Math.toRadians(-45.0 - index * 30.0)
            val offsetX = (radiusPx * cos(angleRad)).toFloat() * p
            val offsetY = (radiusPx * sin(angleRad)).toFloat() * p

            SmallFloatingActionButton(
                onClick = {
                    if (p > 0.5f) {
                        action.onClick()
                        onExpandedChange(false)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomPadding)
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .scale(p)
                    .alpha(p),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(action.icon, contentDescription = action.label)
            }
        }

        // 主 FAB
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomPadding),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = "快捷操作",
            )
        }
    }
}
