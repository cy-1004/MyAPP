package com.myapp.feature.widget.navigation

import com.myapp.feature.widget.data.WidgetNavTarget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * :app 层取小组件导航深链的入口。
 * MainActivity 直接 @Inject 写入；MyApp（Compose 层）经这个 EntryPoint 收集。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetGraphEntryPoint {
    fun widgetNavTarget(): WidgetNavTarget
}
