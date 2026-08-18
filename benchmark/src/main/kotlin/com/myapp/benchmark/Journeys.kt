package com.myapp.benchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/** 被测应用的包名。release 变体没有 `.debug` 后缀（见 :app 的 buildTypes）。 */
const val TARGET_PACKAGE = "com.myapp"

/** 等一个界面稳定下来的上限。真机冷启动 + Compose 首帧偶尔会慢，给足余量。 */
private const val WAIT_TIMEOUT_MS = 10_000L

/**
 * 首次安装会进「保活自检」向导（`MainActivity` 里 `keepAliveChecked` 为 false 时的分支）。
 *
 * 基准测试跑在**全新安装**的 release 包上，每次都会撞到它，
 * 不点掉就永远进不到首页，测出来的「启动」也只是向导的启动。
 * 向导已经走完时找不到这个按钮，直接返回，不当异常。
 */
fun UiDevice.dismissKeepAliveWizardIfPresent() {
    val done = wait(Until.findObject(By.text("完成")), WAIT_TIMEOUT_MS) ?: return
    done.click()
    waitForIdle()
}

/** 等首页真的画出来。用底栏的「首页」文案当锚点--它只在主界面出现。 */
fun UiDevice.waitForHome() {
    wait(Until.hasObject(By.text("首页")), WAIT_TIMEOUT_MS)
    waitForIdle()
}

/**
 * 首页上下滚一个来回。
 *
 * `setGestureMargin` 收掉边缘：不收的话手势会被系统的返回手势区吃掉，
 * 表现为「滚动没反应」，而基准测试不会报错，只会安静地测出一堆没滚动的帧。
 */
fun UiDevice.scrollHomeUpAndDown() {
    val content = findObject(By.scrollable(true)) ?: return
    content.setGestureMargin(displayWidth / 5)
    repeat(2) { content.fling(Direction.DOWN) }
    repeat(2) { content.fling(Direction.UP) }
    waitForIdle()
}
