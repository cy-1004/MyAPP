package com.myapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager 走 Hilt 注入，这样 Worker 里可以直接拿 Repository。
     *
     * 提醒：**只有可延迟的任务（RSS 拉取、知识池刷新、数据清理）才用 WorkManager**。
     * 定时提醒必须用 AlarmManager 的 setExactAndAllowWhileIdle——
     * ColorOS 会冻结后台，WorkManager 的周期任务在这台机器上不可靠（PRD 9.3）。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
