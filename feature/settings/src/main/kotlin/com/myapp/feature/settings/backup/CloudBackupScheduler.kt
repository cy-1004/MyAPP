package com.myapp.feature.settings.backup

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日云备份的排程（PRD 3.13）。
 *
 * **为什么周期任务之外还要一个补偿入口**：PRD 9.3 / 4.3 已经写明，
 * ColorOS 会冻结后台，WorkManager 的周期任务在目标机型上不保证准时甚至可能不跑。
 * 备份属于「晚点跑无所谓」的任务，所以用 WorkManager 是对的，
 * 但不能只依赖它——[ensureDailyBackup] 会在 App 启动时检查距上次成功备份是否已超过一天，
 * 超过就立刻补一次。这样即使周期任务被系统吃掉，只要用户偶尔打开 App，备份就不会断。
 */
@Singleton
class CloudBackupScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    /** 注册每日周期任务。已存在则保持原有排期，不重置计时。 */
    fun scheduleDaily() {
        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    // 只要求有网：不加「充电 + 不计费网络」是因为在 ColorOS 上
                    // 约束越多越难满足，备份宁可用点流量也不能长期不跑。
                    // 单份备份是压缩后的密文，量级很小。
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(
            CloudBackupWorker.UNIQUE_PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelDaily() {
        workManager.cancelUniqueWork(CloudBackupWorker.UNIQUE_PERIODIC_WORK)
    }

    /** 立即跑一次备份（设置页手动触发，或启动时的补偿）。 */
    fun backupNow() {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        workManager.enqueueUniqueWork(
            CloudBackupWorker.UNIQUE_ONE_TIME_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * App 启动时调用：距上次成功备份超过一天就补一次。
     * 这是对付 ColorOS 吃掉周期任务的兜底，见类注释。
     */
    fun ensureDailyBackup(lastSuccessAt: Long, now: Long) {
        scheduleDaily()
        if (now - lastSuccessAt >= TimeUnit.DAYS.toMillis(1)) backupNow()
    }
}
