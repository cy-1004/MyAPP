package com.myapp.core.database.di

import android.content.Context
import androidx.room.Room
import com.myapp.core.database.DATABASE_NAME
import com.myapp.core.database.MyAppDatabase
import com.myapp.core.database.dao.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MyAppDatabase = Room.databaseBuilder(
        context,
        MyAppDatabase::class.java,
        DATABASE_NAME,
    )
        // 刻意不写 fallbackToDestructiveMigration()：
        // 无云端备份，宁可迁移失败崩溃暴露问题，也不能静默清空用户数据。
        .build()

    @Provides
    fun provideTodoDao(db: MyAppDatabase): TodoDao = db.todoDao()
}
