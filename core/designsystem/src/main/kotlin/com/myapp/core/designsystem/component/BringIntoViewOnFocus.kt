package com.myapp.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay

/**
 * 聚焦时自动请求父滚动容器把当前组件滚到可见区域。
 *
 * 配合 [androidx.compose.foundation.layout.imePadding] 使用：
 * imePadding 把内容区缩到键盘上方，本修饰符把聚焦的输入框滚进缩小的视口。
 *
 * delay 等 IME 动画走一段再请求--聚焦瞬间视口还没缩，立刻请求滚了也白滚。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bringIntoViewOnFocus(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) {
            delay(250)
            requester.bringIntoView()
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged { focused = it.isFocused }
}
