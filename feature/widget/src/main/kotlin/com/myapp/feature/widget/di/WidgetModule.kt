package com.myapp.feature.widget.di

import com.myapp.core.common.contract.WidgetRefreshNotifier
import com.myapp.feature.widget.WidgetUpdateManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    /** 小组件刷新通知契约 → 实现。各 feature 的 Repository 只依赖契约，不依赖 :feature:widget。 */
    @Binds
    @Singleton
    abstract fun bindWidgetRefreshNotifier(impl: WidgetUpdateManager): WidgetRefreshNotifier
}
