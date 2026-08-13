package com.myapp.feature.settings.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myapp.core.datastore.AppPreferences
import com.myapp.feature.settings.backup.cloud.CloudBackupException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * 每日一次的云备份任务（PRD 3.13）。
 *
 * 明确**不是实时同步**：数据变更不触发上传，只按天整份快照上传一次。
 * 这与「私人备份盘，不是同步引擎」的定位一致，也避免了冲突合并那一整套复杂度。
 */
@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: CloudBackupRepository,
    private val preferences: AppPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferences.cloudBackupEnabled.first()) return Result.success()
        if (!repository.isSignedIn || !repository.hasPassphrase) return Result.success()

        return try {
            repository.backupNow()
            Result.success()
        } catch (e: CloudBackupException) {
            // 登录失效要用户介入，重试多少次都没用；其余（网络问题等）值得重试。
            // 失败原因已由 repository 落进偏好设置，设置页会显示出来。
            if (e.needsReauth) Result.failure() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK = "cloud_backup_daily"
        const val UNIQUE_ONE_TIME_WORK = "cloud_backup_now"
    }
}
