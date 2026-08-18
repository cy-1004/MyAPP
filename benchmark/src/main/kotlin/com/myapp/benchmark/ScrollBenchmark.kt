package com.myapp.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * 首页滚动的帧耗时（PRD 6.4 的验收标准是 144Hz 下丢帧率 < 1%，同样从来没量过）。
 *
 * [FrameTimingMetric] 给的是 `frameDurationCpuMs` 的 P50/P90/P95/P99。
 * **怎么判断达标**：这台机器是 144Hz，一帧的预算是 1000/144 ≈ 6.94ms。
 * 看 P99--P99 超过 6.94ms 就意味着超过 1% 的帧掉了，正好对上 PRD 那条标准。
 * 不要只看 P50，它再漂亮也盖不住卡顿，用户感知到的恰恰是长尾那几帧。
 *
 * `StartupMode.WARM`：这里量的是滚动不是启动，不需要每次冷启；
 * 冷启的抖动反而会污染前几帧。
 */
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollHome() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            device.dismissKeepAliveWizardIfPresent()
            device.waitForHome()
        },
    ) {
        device.scrollHomeUpAndDown()
    }
}
