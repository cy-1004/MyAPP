package com.myapp.feature.widget.ui

import androidx.compose.runtime.Composable
import com.myapp.core.designsystem.theme.MyAppTheme

/**
 * 配置页主题。直接复用主 App 的 [MyAppTheme]（橙色品牌主题），
 * 保证配置页与 App 内页面观感一致。
 */
@Composable
fun WidgetConfigTheme(content: @Composable () -> Unit) {
    MyAppTheme(content = content)
}
