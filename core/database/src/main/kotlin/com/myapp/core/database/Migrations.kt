package com.myapp.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移。
 *
 * **纪律**（PRD 4.7.7）：本项目数据无云端备份，丢一次就永久没了。
 * 因此严禁 fallbackToDestructiveMigration()，每次改表都必须在这里补一条 Migration，
 * 并提交 `schemas/` 下新版本的 JSON。
 *
 * 写法约定：SQL 必须与 Room 生成的建表语句**逐字符一致**（含反引号与 NOT NULL 位置），
 * 否则 Room 在启动时的 schema 校验会失败。写完照着 `schemas/<version>.json` 里的
 * `createSql` 字段核对一遍是最快的验证方式。
 */

/** v2：加入 M1 的两张表--纪念日与经期记录。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `anniversary` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`is_lunar` INTEGER NOT NULL, " +
                "`repeat_type` TEXT NOT NULL, " +
                "`remind_days_before` INTEGER NOT NULL, " +
                "`note` TEXT, " +
                "`pinned` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_anniversary_date` ON `anniversary` (`date`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_anniversary_uuid` ON `anniversary` (`uuid`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `period_record` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, " +
                "`start_date` INTEGER NOT NULL, " +
                "`end_date` INTEGER, " +
                "`note` TEXT, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_period_record_start_date` ON `period_record` (`start_date`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_period_record_uuid` ON `period_record` (`uuid`)")
    }
}

/**
 * v3：加入 M3 笔记表 + FTS 全文搜索虚表（PRD 3.4 / 4.2）。
 *
 * FTS 用外部内容表模式（`contentEntity = NoteEntity`），Room 自动生成
 * 4 个同步触发器（BeforeUpdate / BeforeDelete / AfterUpdate / AfterInsert），
 * 把 note.content 的增删改同步到 note_fts。触发器 SQL 必须与
 * `schemas/3.json` 的 `createSql` 逐字符一致（含触发器名大小写与 `docid` 关键字）。
 *
 * `images_json` 存 `` 分隔的相对路径列表，由 [Converters] 转换。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `note` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`tags` TEXT NOT NULL, " +
                "`images_json` TEXT NOT NULL, " +
                "`pinned` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_note_uuid` ON `note` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_pinned` ON `note` (`pinned`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_updated_at` ON `note` (`updated_at`)")

        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `note_fts` USING FTS4(`content` TEXT NOT NULL, content=`note`)",
        )
        // 4 个同步触发器，SQL 与 Room 生成的 MyAppDatabase_Impl 逐字符一致：
        // - 触发器名不加反引号
        // - 用 `rowid` 而非 `id`（FTS contentEntity 模式下 Room 的约定）
        // - `; END` 中间有空格
        // - 除 BeforeUpdate/BeforeDelete/AfterUpdate 外，还有 AfterInsert
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_BEFORE_UPDATE BEFORE UPDATE ON `note` BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_BEFORE_DELETE BEFORE DELETE ON `note` BEGIN DELETE FROM `note_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_AFTER_UPDATE AFTER UPDATE ON `note` BEGIN INSERT INTO `note_fts`(`docid`, `content`) VALUES (NEW.`rowid`, NEW.`content`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_note_fts_AFTER_INSERT AFTER INSERT ON `note` BEGIN INSERT INTO `note_fts`(`docid`, `content`) VALUES (NEW.`rowid`, NEW.`content`); END",
        )
    }
}

/** v4：加入 M4 的疑问表（PRD 3.5）。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `question` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, " +
                "`content` TEXT NOT NULL, " +
                "`context` TEXT, " +
                "`tags` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`answer` TEXT, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER, " +
                "`resolved_at` INTEGER)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_question_uuid` ON `question` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_status` ON `question` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_question_updated_at` ON `question` (`updated_at`)")
    }
}

/**
 * v5：加入 M5 记账三张表（PRD 3.6）。
 *
 * - `transaction_record`：账目流水。金额存「分」Long（PRD 4.2 严禁 Float/Double）。
 *   direction/status/source 存字符串不存枚举，加新值不做迁移（与 anniversary.repeat_type
 *   同约定）。categoryId 引用 category.id，不用 @ForeignKey--分类只软删不硬删。
 * - `category`：记账分类。内置 10 个默认由 CategorySeeder 在首次 DB 创建时灌入
 *   （RoomDatabase.Callback.onCreate，不在迁移里）。isProtected 标记「未分类」保留项。
 * - `budget`：预算。每期一行，effectiveTo=null 表示当前生效。Phase 1 单行。
 *
 * 内置分类不在迁移里灌：迁移只跑一次（v4->v5），但老用户升级到 v5 时 category 表
 * 已存在（虽然空），onCreate 不会触发；新用户首次安装时 onCreate 触发会灌。
 * 两种场景都靠 CategorySeeder.seedIfEmpty() 兜底（LedgerRepository 首次调用时检查）。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `transaction_record` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, " +
                "`amount` INTEGER NOT NULL, " +
                "`direction` TEXT NOT NULL, " +
                "`category_id` INTEGER NOT NULL, " +
                "`merchant` TEXT, " +
                "`channel` TEXT, " +
                "`occurred_at` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`raw_text` TEXT, " +
                "`source` TEXT NOT NULL, " +
                "`note` TEXT, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transaction_record_uuid` ON `transaction_record` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_record_occurred_at` ON `transaction_record` (`occurred_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_record_status` ON `transaction_record` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_record_category_id` ON `transaction_record` (`category_id`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `category` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`uuid` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`icon` TEXT NOT NULL, " +
                "`color` TEXT NOT NULL, " +
                "`sort_order` INTEGER NOT NULL, " +
                "`is_active` INTEGER NOT NULL, " +
                "`is_protected` INTEGER NOT NULL, " +
                "`parent_id` INTEGER, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, " +
                "`deleted_at` INTEGER)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_category_uuid` ON `category` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_is_active` ON `category` (`is_active`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_category_sort_order` ON `category` (`sort_order`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `budget` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cycle_start_day` INTEGER NOT NULL, " +
                "`total_amount` INTEGER NOT NULL, " +
                "`effective_from` INTEGER NOT NULL, " +
                "`effective_to` INTEGER, " +
                "`auto_rollover` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_budget_effective_to` ON `budget` (`effective_to`)")
    }
}

/** 注册到 Room 的全部迁移。新增迁移后记得加进这个数组。 */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
)
