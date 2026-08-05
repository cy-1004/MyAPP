package com.myapp.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.myapp.core.database.dao.TodoDao
import com.myapp.core.database.model.TodoEntity

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
        // 后续按 PRD 交付计划逐个加入：
        // PeriodRecordEntity, AnniversaryEntity, NoteEntity, NoteFtsEntity,
        // QuestionEntity, TransactionEntity, BudgetEntity, BudgetCategoryEntity,
        // MerchantCategoryMapEntity, ParseRuleEntity,
        // KnowledgeSourceEntity, KnowledgeContentEntity, KnowledgeReviewEntity,
        // RssFeedEntity, RssArticleEntity,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MyAppDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
}

internal const val DATABASE_NAME = "myapp.db"
