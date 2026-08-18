package com.myapp.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.designsystem.theme.LocalNavAnimatedVisibilityScope

/**
 * 和 `composable<T>` 一样注册一个导航目的地，额外把该目的地的过渡作用域
 * 通过 [LocalNavAnimatedVisibilityScope] 提供给整棵子树（PRD 6.2 共享元素转场）。
 *
 * **只有参与共享元素的目的地需要换成它**，其余目的地继续用 `composable<T>` 即可--
 * 没提供作用域的地方，`Modifier.sharedBoundsOrNothing` 会自动退化成无操作。
 * 想给某个页面加共享元素时，把它的 `composable<Route.X>` 改成
 * `sharedElementComposable<Route.X>`，再在两端标同一个 key，就这两步。
 *
 * 作用域必须在**目的地内部**提供，不能在 NavHost 外面统一provide：
 * 每个目的地的 `AnimatedContentScope` 是各自独立的，共享的只有外层
 * `SharedTransitionScope`（那个才在 `:app` 的 NavHost 外面提供一次）。
 */
inline fun <reified T : Any> NavGraphBuilder.sharedElementComposable(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        // 先把接收者取出来：进了 CompositionLocalProvider 的 lambda 之后
        // `this` 就不再是 AnimatedContentScope 了
        val animatedContentScope = this
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides animatedContentScope) {
            animatedContentScope.content(entry)
        }
    }
}
