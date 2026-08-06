package com.myapp.feature.anniversary.data

import com.myapp.core.common.contract.ReminderSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface AnniversaryReminderModule {
    @Binds
    @IntoSet
    fun bindAnniversaryReminderSource(repository: AnniversaryRepository): ReminderSource
}
