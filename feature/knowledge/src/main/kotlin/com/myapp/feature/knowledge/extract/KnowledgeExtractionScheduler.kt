package com.myapp.feature.knowledge.extract

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 排一次 [KnowledgeExtractWorker] 任务。
 *
 * 只要求联网（不强制 WiFi/充电）：本 V1 唯一的两个触发点——新建/编辑保存、阅读页手动刷新——
 * 都是用户当下在等结果，加 WiFi/充电门槛只会让任务一直 PENDING 却没人知道为什么。
 * PRD 原文「充电 + WiFi 优先」针对的是自动周期性批量刷新，这次没做周期任务（见
 * [KnowledgeExtractWorker] 的说明），所以这里不加那两个约束。
 *
 * `enqueueUniqueWork` + `REPLACE`：同一个 source 重复点刷新只保留最新一次，
 * 不会排队攒出一堆过期任务。
 */
@Singleton
class KnowledgeExtractionScheduler @Inject constructor(
    private val workManager: WorkManager,
) {
    fun enqueue(sourceId: Long) {
        val request = OneTimeWorkRequestBuilder<KnowledgeExtractWorker>()
            .setInputData(workDataOf(KnowledgeExtractWorker.KEY_SOURCE_ID to sourceId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(
            "knowledge_extract_$sourceId",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
