package com.myapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.myapp.core.database.dao.AnniversaryDao
import com.myapp.core.database.dao.BackupDao
import com.myapp.core.database.dao.BudgetAlertStateDao
import com.myapp.core.database.dao.BudgetCategoryDao
import com.myapp.core.database.dao.BudgetDao
import com.myapp.core.database.dao.CategoryDao
import com.myapp.core.database.dao.KnowledgeContentDao
import com.myapp.core.database.dao.KnowledgeReviewDao
import com.myapp.core.database.dao.KnowledgeSourceDao
import com.myapp.core.database.dao.NoteDao
import com.myapp.core.database.dao.PeriodDao
import com.myapp.core.database.dao.QuestionDao
import com.myapp.core.database.dao.RssArticleDao
import com.myapp.core.database.dao.RssSourceDao
import com.myapp.core.database.dao.TodoDao
import com.myapp.core.database.dao.TransactionDao
import com.myapp.core.database.model.AnniversaryEntity
import com.myapp.core.database.model.BudgetAlertStateEntity
import com.myapp.core.database.model.BudgetCategoryEntity
import com.myapp.core.database.model.BudgetEntity
import com.myapp.core.database.model.CategoryEntity
import com.myapp.core.database.model.KnowledgeContentEntity
import com.myapp.core.database.model.KnowledgeContentFtsEntity
import com.myapp.core.database.model.KnowledgeReviewEntity
import com.myapp.core.database.model.KnowledgeSourceEntity
import com.myapp.core.database.model.NoteEntity
import com.myapp.core.database.model.NoteFtsEntity
import com.myapp.core.database.model.PeriodRecordEntity
import com.myapp.core.database.model.QuestionEntity
import com.myapp.core.database.model.QuestionFtsEntity
import com.myapp.core.database.model.RssArticleEntity
import com.myapp.core.database.model.RssSourceEntity
import com.myapp.core.database.model.TodoEntity
import com.myapp.core.database.model.TransactionEntity

/**
 * 应用唯一数据库。
 *
 * **关于实体放在这里而不是各 feature 模块的说明**（与 PRD 4.7.1 的偏差，刻意为之）：
 * Room 的 @Database 需要在编译期拿到完整实体列表，无法运行时动态注册。
 * 若坚持让每个 feature 持有自己的实体，就得让 :core:database 反向依赖所有 feature，
 * 依赖方向会乱掉。业界通行做法（含 Google 官方 Now in Android）是把实体与 DAO
 * 集中在数据库模块、按 feature 分包，feature 只依赖 DAO 接口。
 *
 * 扩展性并未因此受损：新增一个 feature 时在 model/ 与 dao/ 下加对应包即可，
 * 不影响任何已有模块；删除 feature 时其表留在库里也不会有副作用。
 *
 * **迁移纪律**：本项目数据无云端备份，丢一次就没了。因此：
 *   - 严禁 fallbackToDestructiveMigration()
 *   - 每次改表必须写 Migration 并提交 schemas/ 下的 JSON
 *   - 迁移必须有对应测试
 */
@Database(
    entities = [
        TodoEntity::class,
        AnniversaryEntity::class,
        PeriodRecordEntity::class,
        NoteEntity::class,
        NoteFtsEntity::class,
        QuestionEntity::class,
        QuestionFtsEntity::class,
        // M5 记账（PRD 3.6）：
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        // M6 知识库（PRD 3.7）：
        KnowledgeSourceEntity::class,
        KnowledgeContentEntity::class,
        KnowledgeContentFtsEntity::class,
        // M8 RSS 资讯（PRD 3.9）：
        RssSourceEntity::class,
        RssArticleEntity::class,
        // M7 知识点每日推送（PRD 3.8）：
        KnowledgeReviewEntity::class,
        // 分类预算 + 预算预警（PRD 3.6.2）：
        BudgetCategoryEntity::class,
        BudgetAlertStateEntity::class,
        // 后续按 PRD 交付计划逐个加入：
        // MerchantCategoryMapEntity, ParseRuleEntity,
    ],
    version = DATABASE_SCHEMA_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MyAppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun anniversaryDao(): AnniversaryDao
    abstract fun periodDao(): PeriodDao
    abstract fun noteDao(): NoteDao
    abstract fun questionDao(): QuestionDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun budgetCategoryDao(): BudgetCategoryDao
    abstract fun budgetAlertStateDao(): BudgetAlertStateDao
    abstract fun knowledgeSourceDao(): KnowledgeSourceDao
    abstract fun knowledgeContentDao(): KnowledgeContentDao
    abstract fun knowledgeReviewDao(): KnowledgeReviewDao
    abstract fun rssSourceDao(): RssSourceDao
    abstract fun rssArticleDao(): RssArticleDao

    /** 全量备份/恢复（PRD 3.13）。新增业务表时记得同步补 BackupDao 里的方法。 */
    abstract fun backupDao(): BackupDao
}

internal const val DATABASE_NAME = "myapp.db"

/**
 * 当前数据库 schema 版本。
 *
 * 公开（DATABASE_NAME 是 internal）是因为备份快照要把它写进元数据：
 * 恢复时先比对版本，避免拿高版本的备份往低版本 App 上灌（表结构对不上会炸）。
 * 直接喂给 @Database(version = ...)，保证两处不会各改各的。
 */
const val DATABASE_SCHEMA_VERSION = 11
