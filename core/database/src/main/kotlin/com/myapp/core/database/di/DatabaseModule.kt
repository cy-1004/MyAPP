package com.myapp.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.myapp.core.database.ALL_MIGRATIONS
import com.myapp.core.database.Converters
import com.myapp.core.database.DATABASE_NAME
import com.myapp.core.database.MyAppDatabase
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
import com.myapp.core.database.seed.CategorySeeder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): MyAppDatabase = Room.databaseBuilder(
        context,
        MyAppDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(*ALL_MIGRATIONS)
        // 内置分类种子（PRD 3.6.1）：DB 打开时检查 category 表，为空则灌入 10 个
        // 默认分类。onOpen 同时覆盖新装（onCreate 后）与升级（MIGRATION_4_5 后）两种场景。
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                db.query("SELECT COUNT(*) FROM `category`").use { c ->
                    c.moveToFirst()
                    if (c.getInt(0) == 0) {
                        CategorySeeder.seedSync(db, context)
                    }
                }
            }
        })
        // 刻意不写 fallbackToDestructiveMigration()：
        // 无云端备份，宁可迁移失败崩溃暴露问题，也不能静默清空用户数据。
        // Converters 通过 @TypeConverters 注解自动注册（无依赖，自动实例化即可）。
        .build()

    @Provides
    fun provideTodoDao(db: MyAppDatabase): TodoDao = db.todoDao()

    @Provides
    fun provideAnniversaryDao(db: MyAppDatabase): AnniversaryDao = db.anniversaryDao()

    @Provides
    fun providePeriodDao(db: MyAppDatabase): PeriodDao = db.periodDao()

    @Provides
    fun provideNoteDao(db: MyAppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideQuestionDao(db: MyAppDatabase): QuestionDao = db.questionDao()

    @Provides
    fun provideTransactionDao(db: MyAppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: MyAppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideBudgetDao(db: MyAppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideKnowledgeSourceDao(db: MyAppDatabase): KnowledgeSourceDao = db.knowledgeSourceDao()

    @Provides
    fun provideKnowledgeContentDao(db: MyAppDatabase): KnowledgeContentDao = db.knowledgeContentDao()

    @Provides
    fun provideKnowledgeReviewDao(db: MyAppDatabase): KnowledgeReviewDao = db.knowledgeReviewDao()

    @Provides
    fun provideBudgetCategoryDao(db: MyAppDatabase): BudgetCategoryDao = db.budgetCategoryDao()

    @Provides
    fun provideBudgetAlertStateDao(db: MyAppDatabase): BudgetAlertStateDao = db.budgetAlertStateDao()

    @Provides
    fun provideRssSourceDao(db: MyAppDatabase): RssSourceDao = db.rssSourceDao()

    @Provides
    fun provideRssArticleDao(db: MyAppDatabase): RssArticleDao = db.rssArticleDao()

    @Provides
    fun provideBackupDao(db: MyAppDatabase): BackupDao = db.backupDao()
}
