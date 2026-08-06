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

/** v2：加入 M1 的两张表——纪念日与经期记录。 */
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

/** 注册到 Room 的全部迁移。新增迁移后记得加进这个数组。 */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
)
