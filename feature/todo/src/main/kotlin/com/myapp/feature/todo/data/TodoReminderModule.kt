package com.myapp.feature.todo.data

import com.myapp.core.common.contract.ReminderSource
import com.myapp.core.common.contract.TodoToggleWriter
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

    /** 桌面小组件圆圈勾选 → 同一套 setDone 逻辑（含重复任务生成下一次）。 */
    @Binds
    fun bindTodoToggleWriter(repository: TodoRepository): TodoToggleWriter
}
