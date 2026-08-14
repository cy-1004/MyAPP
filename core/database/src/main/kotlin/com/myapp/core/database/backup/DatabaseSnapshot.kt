package com.myapp.core.database.backup

import com.myapp.core.database.model.AnniversaryEntity
import com.myapp.core.database.model.BudgetCategoryEntity
import com.myapp.core.database.model.BudgetEntity
import com.myapp.core.database.model.CategoryEntity
import com.myapp.core.database.model.KnowledgeContentEntity
import com.myapp.core.database.model.KnowledgeReviewEntity
import com.myapp.core.database.model.KnowledgeSourceEntity
import com.myapp.core.database.model.NoteEntity
import com.myapp.core.database.model.PeriodDayLogEntity
import com.myapp.core.database.model.PeriodRecordEntity
import com.myapp.core.database.model.QuestionEntity
import com.myapp.core.database.model.RssSourceEntity
import com.myapp.core.database.model.TodoEntity
import com.myapp.core.database.model.TransactionEntity
import kotlinx.serialization.Serializable

/**
 * 一次全量备份的数据体（PRD 3.13）。
 *
 * 直接复用 Room 实体而不是另立一套 DTO：实体已经全是基本类型字段，
 * 再抄一份镜像 DTO 只会带来「加了字段忘了同步」的长期风险。代价是实体多了个
 * @Serializable 注解，可接受。
 *
 * **版本兼容**：编解码用的 Json 开了 `ignoreUnknownKeys`，且所有实体字段都有默认值，
 * 因此新增字段的备份能被旧 App 读（多出的键忽略）、旧备份能被新 App 读（缺的键取默认值）。
 * 真正不兼容的是「改字段类型」或「改表名」，那种情况靠 [schemaVersion] 卡住。
 *
 * 三张 FTS 表与 `rss_article` 不在此列——前者由触发器从主表重建，
 * 后者是可从 RSS 源重新抓取的外部内容且体积占比超过 99%，见 BackupDao 的说明。
 */
@Serializable
data class DatabaseSnapshot(
    /** 产生这份快照时的 Room schema 版本，恢复前用于兼容性判断。 */
    val schemaVersion: Int,
    /** 快照生成时刻（epochMilli）。 */
    val createdAt: Long,
    val todos: List<TodoEntity> = emptyList(),
    val anniversaries: List<AnniversaryEntity> = emptyList(),
    val periodRecords: List<PeriodRecordEntity> = emptyList(),
    val periodDayLogs: List<PeriodDayLogEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val questions: List<QuestionEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val budgetCategories: List<BudgetCategoryEntity> = emptyList(),
    val knowledgeSources: List<KnowledgeSourceEntity> = emptyList(),
    val knowledgeContents: List<KnowledgeContentEntity> = emptyList(),
    val knowledgeReviews: List<KnowledgeReviewEntity> = emptyList(),
    val rssSources: List<RssSourceEntity> = emptyList(),
) {
    /** 总行数，用于「备份了 N 条记录」这类展示与空库保护判断。 */
    val rowCount: Int
        get() = todos.size + anniversaries.size + periodRecords.size + periodDayLogs.size + notes.size +
            questions.size + transactions.size + categories.size + budgets.size +
            budgetCategories.size + knowledgeSources.size + knowledgeContents.size +
            knowledgeReviews.size + rssSources.size
}
