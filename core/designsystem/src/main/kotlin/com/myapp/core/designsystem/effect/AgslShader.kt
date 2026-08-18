package com.myapp.core.designsystem.effect

import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.myapp.core.designsystem.theme.LocalMotionLevel

/**
 * 编译一段 AGSL 着色器，**编译不过或用户关了着色器时返回 null**（PRD 6.1）。
 *
 * 两个必须知道的事实：
 *
 * 1. **AGSL 是运行时编译的**。源码写错不会在 Kotlin 编译期报错，
 *    而是在 `RuntimeShader(...)` 构造时抛 `IllegalArgumentException`--
 *    也就是说一个手滑的分号会变成线上崩溃。所以这里一律 `runCatching` 兜住，
 *    失败就退化成「没有这个效果」，绝不让一段装饰性的着色器把页面搞崩。
 * 2. **尊重 [com.myapp.core.designsystem.theme.MotionLevel.enableShader]**：
 *    只有 Full 档跑着色器。Reduced/None 档返回 null，调用方走静态兜底那条路。
 *
 * `minSdk 35` 所以不需要 API 版本判断--`RuntimeShader` 是 API 33 加的。
 *
 * @return 可用的着色器；不可用时 null，**调用方必须提供不依赖着色器的兜底渲染**
 */
@Composable
fun rememberAgslShaderOrNull(source: String): RuntimeShader? {
    val enabled = LocalMotionLevel.current.enableShader
    return remember(source, enabled) {
        if (!enabled) return@remember null
        runCatching { RuntimeShader(source) }.getOrNull()
    }
}
