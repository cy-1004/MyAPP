package com.myapp.core.designsystem.effect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.myapp.core.designsystem.theme.Accent
import com.myapp.core.designsystem.theme.AccentContainer
import com.myapp.core.designsystem.theme.LocalMotionLevel
import com.myapp.core.designsystem.theme.Success
import com.myapp.core.designsystem.theme.Warning
import java.util.concurrent.TimeUnit
import nl.dionsegijn.konfetti.compose.KonfettiView
import nl.dionsegijn.konfetti.compose.OnParticleSystemUpdateListener
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.PartySystem
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Rotation
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size

/**
 * 撒花庆祝（PRD 6.1）：待办全部完成 / 预算周期内不超支 / 纪念日当天，
 * 三处触发点共用同一份视觉配置，各 feature 不直接碰 konfetti 的 API。
 *
 * 「轻量点缀」定档（用户 2026-08-19 定稿，非「热闹」那档）：
 *   - 约 25 颗，100ms 内一次性发完（视觉上是「一下子撒开」而不是持续喷）；
 *   - 1.5s 内衰减完（`timeToLive` + `fadeOutEnabled`）；
 *   - 配色取 [Accent]/[AccentContainer]/[Success]/[Warning]--
 *     这几个都是设计里本来就在用的克制色，不引入饱和撞色，跟 App 视觉语言一致。
 *
 * 从屏幕顶部整条边落下（[Position.Relative] 横跨 0~1），适合叠在整页内容之上。
 * 调用方只管铺满一个 Box 当遮罩层，不需要关心粒子系统内部生命周期--
 * 播完自动清空自己（靠 [OnParticleSystemUpdateListener]），不会挡住后面的点击
 * （没有 `clickable`，Glance/Compose 里未消费的触摸会穿透到下层）。
 *
 * @param trigger 每次想撒花就传一个新值（比如自增计数器、时间戳），
 *   同一个值不会重复触发--[LaunchedEffect] 按这个值去重。null 表示不触发。
 *   为什么不用 `Boolean`：同一次「true」如果调用方忘了复位会导致只触发一次；
 *   要求「每次都是新值」把去重逻辑交给调用方，调用方语义上更清楚「这是第几次庆祝」。
 */
@Composable
fun ConfettiOverlay(trigger: Any?, modifier: Modifier = Modifier) {
    if (!LocalMotionLevel.current.enableConfetti) return

    var parties by remember { mutableStateOf<List<Party>>(emptyList()) }
    LaunchedEffect(trigger) {
        if (trigger != null) {
            parties = confettiParties()
        }
    }

    if (parties.isNotEmpty()) {
        KonfettiView(
            modifier = modifier.fillMaxSize(),
            parties = parties,
            updateListener = object : OnParticleSystemUpdateListener {
                override fun onParticleSystemEnded(system: PartySystem, activeSystems: Int) {
                    if (activeSystems == 0) parties = emptyList()
                }
            },
        )
    }
}

private fun confettiParties(): List<Party> {
    val colors = listOf(Accent, AccentContainer, Success, Warning).map { it.toArgb() }
    return listOf(
        Party(
            angle = 270,
            spread = 60,
            speed = 4f,
            maxSpeed = 10f,
            damping = 0.92f,
            size = listOf(Size.SMALL, Size.SMALL, Size.MEDIUM),
            colors = colors,
            shapes = listOf(Shape.Square, Shape.Circle),
            timeToLive = 1500L,
            fadeOutEnabled = true,
            position = Position.Relative(0.0, 0.0).between(Position.Relative(1.0, 0.0)),
            delay = 0,
            rotation = Rotation(),
            emitter = Emitter(100L, TimeUnit.MILLISECONDS).max(25),
        ),
    )
}
