package com.myapp.core.common.reminder

import com.myapp.core.common.contract.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface ReminderModule {
    @Binds
    fun bindReminderScheduler(scheduler: AlarmReminderScheduler): ReminderScheduler
}
