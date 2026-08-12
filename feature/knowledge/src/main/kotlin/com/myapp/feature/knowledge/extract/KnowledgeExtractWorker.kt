package com.myapp.feature.knowledge.extract

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.KnowledgeContentDao
import com.myapp.core.database.dao.KnowledgeSourceDao
import com.myapp.core.database.model.KnowledgeContentEntity
import com.myapp.feature.knowledge.data.KnowledgeFetchStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * 单个知识源的一次性正文提取任务（PRD 3.7）。
 *
 * 只做一次性任务（[androidx.work.OneTimeWorkRequest]），不用周期性 WorkManager——
 * `MyApplication.kt` 里已经写明 ColorOS 等厂商 ROM 会冻结后台，周期任务不可靠（PRD 9.3）。
 * 触发时机是「新建/编辑知识源保存后」和「阅读页手动点刷新」两处，见
 * [com.myapp.feature.knowledge.data.KnowledgeRepository]。
 *
 * 提取失败（[ExtractResult.Failed]/[ExtractResult.LoginRequired]）只更新 `fetchStatus`，
 * 不影响已有缓存正文——旧缓存仍然可用，好过被清空。
 */
@HiltWorker
class KnowledgeExtractWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val extractor: FeishuContentExtractor,
    private val sourceDao: KnowledgeSourceDao,
    private val contentDao: KnowledgeContentDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        if (sourceId <= 0) return Result.failure()
        val source = sourceDao.getById(sourceId) ?: return Result.failure()

        val now = AppTime.now()
        when (val extracted = extractor.extract(source.url)) {
            is ExtractResult.Success -> {
                contentDao.deleteBySourceId(sourceId)
                contentDao.upsert(
                    KnowledgeContentEntity(
                        sourceId = sourceId,
                        sectionTitle = extracted.title.ifBlank { null },
                        contentText = extracted.contentText,
                        fetchedAt = now,
                    ),
                )
                sourceDao.updateFetchStatus(sourceId, KnowledgeFetchStatus.SUCCESS.stored, now)
                if (extracted.title.isNotBlank()) {
                    sourceDao.fillTitleIfBlank(sourceId, extracted.title, now)
                }
            }

            ExtractResult.LoginRequired ->
                sourceDao.updateFetchStatus(sourceId, KnowledgeFetchStatus.LOGIN_REQUIRED.stored, now)

            ExtractResult.Failed ->
                sourceDao.updateFetchStatus(sourceId, KnowledgeFetchStatus.FAILED.stored, now)
        }
        return Result.success()
    }

    companion object {
        const val KEY_SOURCE_ID = "sourceId"
    }
}
