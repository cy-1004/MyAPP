package com.myapp.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import org.junit.Rule
import org.junit.Test

/**
 * 生成 Baseline Profile（PRD 4.5）。
 *
 * 产物是一份「这些类和方法在启动路径上被用到了」的清单，
 * 安装时由 `ProfileInstaller` 交给 ART 预编译，省掉首次运行的解释执行/JIT 预热。
 * 收益全在**冷启动和首屏滚动**，跑起来之后的稳态性能不受影响。
 *
 * 跑法：`gradle :benchmark:generateReleaseBaselineProfile`
 * 产物会被 `androidx.baselineprofile` 插件自动写进
 * `app/src/release/generated/baselineProfiles/`，**要提交进 git**--
 * 它是构建输入，不是构建产物，CI/换电脑重新构建时不会自己重新生成。
 *
 * `includeInStartupProfile = true`：除了普通 profile 再额外产出一份启动专用的
 * startup-prof，ART 会把这部分方法排在最前面编译。
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // 首启会撞上保活自检向导，不点掉就采不到首页的类
        device.dismissKeepAliveWizardIfPresent()
        device.waitForHome()

        // 采一段滚动：首页卡片的组合/布局代码也是冷启动后立刻要跑的
        device.scrollHomeUpAndDown()
    }
}
