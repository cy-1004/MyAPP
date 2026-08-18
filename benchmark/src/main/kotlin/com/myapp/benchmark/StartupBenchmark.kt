package com.myapp.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

/**
 * 冷启动耗时（PRD 6.4 的验收标准是 < 800ms，在此之前**从来没人量过**）。
 *
 * 两个用例是一组对照，只有放在一起看才有意义：
 * - [startupNoCompilation] 是**下限**：完全不预编译，模拟最差情况
 * - [startupBaselineProfile] 是**真实体验**：装了 Baseline Profile 之后的样子
 *
 * 两者的差值就是 Baseline Profile 到底值不值。只测后者的话，
 * 拿到一个数字也无从判断它是好是坏。
 *
 * 跑法：`gradle :benchmark:connectedReleaseAndroidTest`
 * 结果看 `benchmark/build/outputs/connected_android_test_additional_output/`。
 * 注意 `iterations = 10` 每条要装卸多次，整组跑完是分钟级的，不是秒级。
 */
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = measureStartup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = measureStartup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    /**
     * `StartupMode.COLD`：每次都杀进程重来，量的是真·冷启动。
     *
     * `startActivityAndWait()` 等到首帧画完为止，所以这里的数字包含
     * splash 那段「等 DataStore 读完 onboarding 标志」的时间--
     * 那本来就是用户盯着屏幕在等的时间，不该从指标里摘出去。
     */
    private fun measureStartup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}
