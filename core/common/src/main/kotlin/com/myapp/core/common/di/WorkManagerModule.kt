package com.myapp.core.common.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [WorkManager] 的唯一提供处。
 *
 * 它没有 @Inject 构造函数，必须手动 @Provides；而 SingletonComponent 里同一类型只能有
 * 一个绑定，所以**不要**在任何 feature 模块里再 @Provides 一次——:feature:knowledge
 * 曾经这么做过，第二个 feature（:settings 的每日云备份）接入时会直接编译失败。
 *
 * 注意本项目对 WorkManager 的定位（PRD 4.3 / 9.3）：只承载「晚点跑也无所谓」的任务。
 * ColorOS 会冻结后台，周期任务不保证准时，定时提醒一律走 AlarmManager 精确闹钟。
 */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
