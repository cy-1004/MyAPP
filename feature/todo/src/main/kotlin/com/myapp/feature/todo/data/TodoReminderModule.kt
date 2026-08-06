package com.myapp.feature.todo.data

import com.myapp.core.common.contract.ReminderSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface TodoReminderModule {
    @Binds
    @IntoSet
    fun bindTodoReminderSource(repository: TodoRepository): ReminderSource
}
