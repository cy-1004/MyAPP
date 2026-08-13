package com.myapp.core.database.backup

import androidx.room.withTransaction
import com.myapp.core.database.DATABASE_SCHEMA_VERSION
import com.myapp.core.database.MyAppDatabase
import com.myapp.core.database.dao.BackupDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据库整库导出/导入（PRD 3.13 / 4.6）。
 *
 * 放在 :core:database 而不是调用方，是因为恢复必须在 `withTransaction` 里完成，
 * 而 room-ktx 只在本模块的 implementation 依赖里。上层只管拿到/给出 [DatabaseSnapshot]，
 * 不需要知道有多少张表、清空和插入的先后顺序。
 */
@Singleton
class BackupDataSource @Inject constructor(
    private val db: MyAppDatabase,
    private val dao: BackupDao,
) {

    /** 读出当前全库数据。整表读取，包含软删除墓碑。 */
    suspend fun export(now: Long): DatabaseSnapshot = db.withTransaction {
        DatabaseSnapshot(
            schemaVersion = DATABASE_SCHEMA_VERSION,
            createdAt = now,
            todos = dao.allTodos(),
            anniversaries = dao.allAnniversaries(),
            periodRecords = dao.allPeriodRecords(),
            notes = dao.allNotes(),
            questions = dao.allQuestions(),
            transactions = dao.allTransactions(),
            categories = dao.allCategories(),
            budgets = dao.allBudgets(),
            budgetCategories = dao.allBudgetCategories(),
            knowledgeSources = dao.allKnowledgeSources(),
            knowledgeContents = dao.allKnowledgeContents(),
            knowledgeReviews = dao.allKnowledgeReviews(),
            rssSources = dao.allRssSources(),
        )
    }

    /**
     * 用快照**覆盖**本机数据：先清空 14 张表，再灌入快照内容。
     *
     * 整个过程在单个事务里——中途失败会整体回滚，不会留下清空了一半的库。
     * 这也是「导入覆盖恢复」的唯一实现，本地导入和云端恢复共用它。
     *
     * @throws IllegalStateException 快照 schema 版本高于当前 App，字段可能对不上，拒绝恢复。
     */
    suspend fun restore(snapshot: DatabaseSnapshot) {
        check(snapshot.schemaVersion <= DATABASE_SCHEMA_VERSION) {
            "备份来自更新版本的 App（schema v${snapshot.schemaVersion}，当前 v$DATABASE_SCHEMA_VERSION），请先升级 App 再恢复"
        }
        db.withTransaction {
            dao.clearTodos()
            dao.clearAnniversaries()
            dao.clearPeriodRecords()
            dao.clearNotes()
            dao.clearQuestions()
            dao.clearTransactions()
            dao.clearCategories()
            dao.clearBudgets()
            dao.clearBudgetCategories()
            dao.clearKnowledgeSources()
            dao.clearKnowledgeContents()
            dao.clearKnowledgeReviews()
            dao.clearRssSources()
            dao.clearRssArticles()

            dao.restoreTodos(snapshot.todos)
            dao.restoreAnniversaries(snapshot.anniversaries)
            dao.restorePeriodRecords(snapshot.periodRecords)
            dao.restoreNotes(snapshot.notes)
            dao.restoreQuestions(snapshot.questions)
            dao.restoreTransactions(snapshot.transactions)
            dao.restoreCategories(snapshot.categories)
            dao.restoreBudgets(snapshot.budgets)
            dao.restoreBudgetCategories(snapshot.budgetCategories)
            dao.restoreKnowledgeSources(snapshot.knowledgeSources)
            dao.restoreKnowledgeContents(snapshot.knowledgeContents)
            dao.restoreKnowledgeReviews(snapshot.knowledgeReviews)
            dao.restoreRssSources(snapshot.rssSources)
            // rss_article 不在备份里，但本机缓存必须清掉：订阅源刚被整表覆盖，
            // 留着旧文章会让它们的 source_id 指向对不上的订阅源。后台任务会重新抓。
        }
    }
}
