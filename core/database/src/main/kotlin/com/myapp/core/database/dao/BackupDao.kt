package com.myapp.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

/**
 * 全量备份/恢复专用 DAO（PRD 3.13 / 4.6）。
 *
 * 单独开一个 DAO 而不是往 12 个业务 DAO 里各加三个方法，原因有二：
 * 1. 备份要的是**整表原样**，业务 DAO 的 `getAll()` 一律带 `deleted_at IS NULL`，
 *    直接复用会把软删除墓碑丢掉——换机恢复后已删的数据会「诈尸」复活。
 * 2. 备份是一个横切关注点，集中在一处便于核对「表加全了没有」：
 *    **新增业务表时必须来这里补一组方法**，否则新表不会进备份，且不会有任何编译错误提醒。
 *
 * **不含三张 FTS 表**（`note_fts` / `question_fts` / `knowledge_content_fts`）：
 * 它们是 external content 表，内容由 Room 生成的触发器从主表同步，
 * 恢复主表数据时会自动重建；直接写 FTS 表反而会造成索引与主表不一致。
 *
 * **也不含 `rss_article`**：实测它占了整份备份的 99.99%（5000+ 篇文章的正文与摘要
 * 近 8MB，而其余 11 张表的全部个人数据合计只有几百字节）。资讯正文是从 RSS 源抓来的
 * 外部内容，恢复后后台任务会重新抓回来，没有必要每天上传一次。
 * 订阅源本身（`rss_source`）是用户配置，仍然要备份。
 *
 * 注意 [clearRssArticles] 保留着：恢复时要把本机的文章缓存清掉。
 * 因为 `rss_source` 会被整表覆盖，留着旧文章会让它们的 `source_id` 指向对不上的订阅源。
 */
@Dao
interface BackupDao {

    // ---------- 读：整表导出，不加任何过滤条件 ----------

    @Query("SELECT * FROM todo")
    suspend fun allTodos(): List<TodoEntity>

    @Query("SELECT * FROM anniversary")
    suspend fun allAnniversaries(): List<AnniversaryEntity>

    @Query("SELECT * FROM period_record")
    suspend fun allPeriodRecords(): List<PeriodRecordEntity>

    @Query("SELECT * FROM period_day_log")
    suspend fun allPeriodDayLogs(): List<PeriodDayLogEntity>

    @Query("SELECT * FROM note")
    suspend fun allNotes(): List<NoteEntity>

    @Query("SELECT * FROM question")
    suspend fun allQuestions(): List<QuestionEntity>

    @Query("SELECT * FROM transaction_record")
    suspend fun allTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM category")
    suspend fun allCategories(): List<CategoryEntity>

    @Query("SELECT * FROM budget")
    suspend fun allBudgets(): List<BudgetEntity>

    /** 分类预算上限（PRD 3.6.2），用户配置，需要备份。不含 budget_alert_state——那是纯提醒去重状态。 */
    @Query("SELECT * FROM budget_category")
    suspend fun allBudgetCategories(): List<BudgetCategoryEntity>

    @Query("SELECT * FROM knowledge_source")
    suspend fun allKnowledgeSources(): List<KnowledgeSourceEntity>

    @Query("SELECT * FROM knowledge_content")
    suspend fun allKnowledgeContents(): List<KnowledgeContentEntity>

    /** 间隔复习进度（M7）：不备份的话换机后「已掌握」的知识点会全部重新推一遍。 */
    @Query("SELECT * FROM knowledge_review")
    suspend fun allKnowledgeReviews(): List<KnowledgeReviewEntity>

    @Query("SELECT * FROM rss_source")
    suspend fun allRssSources(): List<RssSourceEntity>

    // ---------- 写：批量插入，REPLACE 语义 ----------
    //
    // 注意 RssArticleDao.insertAll 用的是 IGNORE（抓取去重场景），这里必须是 REPLACE：
    // 恢复是「以备份为准覆盖本机」，IGNORE 会让本机残留行悄悄赢过备份行。

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTodos(items: List<TodoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAnniversaries(items: List<AnniversaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePeriodRecords(items: List<PeriodRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePeriodDayLogs(items: List<PeriodDayLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreNotes(items: List<NoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreQuestions(items: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreTransactions(items: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBudgets(items: List<BudgetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreBudgetCategories(items: List<BudgetCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreKnowledgeSources(items: List<KnowledgeSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreKnowledgeContents(items: List<KnowledgeContentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreKnowledgeReviews(items: List<KnowledgeReviewEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreRssSources(items: List<RssSourceEntity>)

    // ---------- 清空：恢复前先抹掉本机数据 ----------
    //
    // 必须和插入放在同一个事务里（见 BackupRepository.restore），
    // 否则中途失败会留下一个空库——比不恢复还糟。

    @Query("DELETE FROM todo")
    suspend fun clearTodos()

    @Query("DELETE FROM anniversary")
    suspend fun clearAnniversaries()

    @Query("DELETE FROM period_record")
    suspend fun clearPeriodRecords()

    @Query("DELETE FROM period_day_log")
    suspend fun clearPeriodDayLogs()

    @Query("DELETE FROM note")
    suspend fun clearNotes()

    @Query("DELETE FROM question")
    suspend fun clearQuestions()

    @Query("DELETE FROM transaction_record")
    suspend fun clearTransactions()

    @Query("DELETE FROM category")
    suspend fun clearCategories()

    @Query("DELETE FROM budget")
    suspend fun clearBudgets()

    @Query("DELETE FROM budget_category")
    suspend fun clearBudgetCategories()

    @Query("DELETE FROM knowledge_source")
    suspend fun clearKnowledgeSources()

    @Query("DELETE FROM knowledge_content")
    suspend fun clearKnowledgeContents()

    @Query("DELETE FROM knowledge_review")
    suspend fun clearKnowledgeReviews()

    @Query("DELETE FROM rss_source")
    suspend fun clearRssSources()

    @Query("DELETE FROM rss_article")
    suspend fun clearRssArticles()
}
