package com.myapp.feature.period.data

import com.myapp.core.common.contract.PeriodReminderRefresher
import com.myapp.core.common.contract.ReminderSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface PeriodReminderModule {
    @Binds
    @IntoSet
    fun bindPeriodReminderSource(repository: PeriodRepository): ReminderSource

    /** 设置页改完提醒设置后调它重排。不带 @IntoSet：这个是单实现契约，不是插件集合。 */
    @Binds
    fun bindPeriodReminderRefresher(repository: PeriodRepository): PeriodReminderRefresher
}
