package com.myapp.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * 底部导航栏的实际高度（含手势导航 inset）。
 *
 * 由顶层 [com.myapp.ui.MyApp] 在底栏可见时测量并提供；底栏不可见时为 0.dp。
 * 各列表页 Scaffold 用它把 FAB 上抬，避免被底栏遮挡。
 */
val LocalBottomBarHeight = compositionLocalOf { 0.dp }
