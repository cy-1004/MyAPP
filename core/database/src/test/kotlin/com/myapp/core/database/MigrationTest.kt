package com.myapp.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 数据库迁移回归测试（PRD 4.7.7，对应交接文档「未完成」P2 项）。
 *
 * **为什么必须有这套测试**：本项目数据无云端备份，丢一次就永久没了。每次改表都靠
 * 手写 SQL 迁移，差一个字符（反引号、NOT NULL 位置、触发器名大小写）就会在运行时
 * 崩，且只在用户升级那一刻才暴露。这套测试把 v1 -> v4 全链路用 Room 的
 * `runMigrationsAndValidate` 自动比对 schemas/<version>.json，把"运行时崩"前移到 CI。
 *
 * **测试什么**：
 * 1. schema 正确：`runMigrationsAndValidate` 第三参 `validateDroppedTables=true`
 *    让 Room 拿迁移后的 db 与 schemas/<version>.json 逐字符对比（含表/索引/触发器）
 * 2. 数据保留：每个迁移前后都插一条 todo，迁移后查回来，证明 ALTER/CREATE 不动旧表数据
 * 3. 新表就绪：迁移后新表存在且为空（用 SELECT COUNT(*) = 0 间接验证）
 *
 * **覆盖范围**：v1->v2 / v2->v3 / v3->v4 / v4->v5 / v5->v6 / v6->v7 单步 +
 * v1->v4 / v1->v5 / v1->v6 / v1->v7 全链路。
 *
 * **运行环境**：Robolectric（JVM 单测，无需真机）。@Config(sdk = [35]) 是因为
 * Robolectric 4.14.1 支持到 SDK 35，targetSdk 36 还没支持，显式降一档跑。
 *
 * @see Migrations.kt 的「纪律」注释
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MyAppDatabase::class.java,
    )

    @Test
    fun `v1_to_v2_新增anniversary与period_record表_旧todo数据保留`() {
        helper.createDatabase(dbName, 1).apply {
            insertTodo(uuid = "todo-1", title = "买菜", createdAt = 1_000L)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        // 旧数据保留
        db.query("SELECT title FROM todo WHERE uuid = 'todo-1'").use { c ->
            assertTrue("todo 应保留", c.moveToFirst())
            assertEquals("买菜", c.getString(0))
        }
        // 新表已建好且为空
        assertEquals(0, db.count("anniversary"))
        assertEquals(0, db.count("period_record"))
        db.close()
    }

    @Test
    fun `v2_to_v3_新增note与note_fts_旧数据全保留`() {
        helper.createDatabase(dbName, 2).apply {
            insertTodo(uuid = "todo-2", title = "写周报", createdAt = 2_000L)
            insertAnniversary(uuid = "ann-1", title = "相识纪念日", date = 9_000L)
            insertPeriodRecord(uuid = "per-1", startDate = 8_000L)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        // 旧数据保留
        db.query("SELECT title FROM todo WHERE uuid = 'todo-2'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("写周报", c.getString(0))
        }
        db.query("SELECT title FROM anniversary WHERE uuid = 'ann-1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("相识纪念日", c.getString(0))
        }
        db.query("SELECT start_date FROM period_record WHERE uuid = 'per-1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals(8_000L, c.getLong(0))
        }
        // 新表已建好且为空
        assertEquals(0, db.count("note"))
        assertEquals(0, db.count("note_fts"))
        db.close()
    }

    @Test
    fun `v3_to_v4_新增question表_旧note与todo保留`() {
        helper.createDatabase(dbName, 3).apply {
            insertTodo(uuid = "todo-3", title = "整理笔记", createdAt = 3_000L)
            insertNote(uuid = "note-1", content = "# 测试", createdAt = 3_100L)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        // 旧数据保留
        db.query("SELECT title FROM todo WHERE uuid = 'todo-3'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("整理笔记", c.getString(0))
        }
        db.query("SELECT content FROM note WHERE uuid = 'note-1'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("# 测试", c.getString(0))
        }
        // 新表已建好且为空
        assertEquals(0, db.count("question"))
        db.close()
    }

    @Test
    fun `v1_to_v4_全链路迁移_数据完整保留`() {
        helper.createDatabase(dbName, 1).apply {
            insertTodo(uuid = "todo-full", title = "全链路验证", createdAt = 1L)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName, 4, true, *ALL_MIGRATIONS,
        )

        // 跨 4 个版本迁移后，最早的 todo 数据仍在
        db.query("SELECT title FROM todo WHERE uuid = 'todo-full'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("全链路验证", c.getString(0))
        }
        // 各新表都已就绪
        assertEquals(0, db.count("anniversary"))
        assertEquals(0, db.count("period_record"))
        assertEquals(0, db.count("note"))
        assertEquals(0, db.count("note_fts"))
        assertEquals(0, db.count("question"))
        db.close()
    }

    @Test
    fun `v4_to_v5_新增transaction_category_budget表_旧数据保留`() {
        helper.createDatabase(dbName, 4).apply {
            insertTodo(uuid = "todo-4", title = "买菜", createdAt = 4_000L)
            insertNote(uuid = "note-4", content = "# v4 笔记", createdAt = 4_100L)
            insertQuestion(uuid = "q-4", content = "v4 疑问", createdAt = 4_200L)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)

        // v4 已有的旧表数据完整保留
        db.query("SELECT title FROM todo WHERE uuid = 'todo-4'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("买菜", c.getString(0))
        }
        db.query("SELECT content FROM note WHERE uuid = 'note-4'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("# v4 笔记", c.getString(0))
        }
        db.query("SELECT content FROM question WHERE uuid = 'q-4'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("v4 疑问", c.getString(0))
        }
        // v5 三张新表都已就绪且为空（内置分类由 CategorySeeder 在运行时灌，不在迁移里）
        assertEquals(0, db.count("transaction_record"))
        assertEquals(0, db.count("category"))
        assertEquals(0, db.count("budget"))
        db.close()
    }

    @Test
    fun `v5_to_v6_新增question_fts_旧question数据保留且可被搜到`() {
        helper.createDatabase(dbName, 5).apply {
            insertQuestion(uuid = "q-5", content = "v5 遗留疑问", createdAt = 5_000L)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        // 旧数据保留
        db.query("SELECT content FROM question WHERE uuid = 'q-5'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("v5 遗留疑问", c.getString(0))
        }
        // 迁移里的 rebuild 命令必须把老数据也建进索引，否则升级前的疑问永远搜不到
        db.query("SELECT content FROM question_fts WHERE question_fts MATCH 'v5'").use { c ->
            assertTrue("迁移前的旧疑问应该已经建进 FTS 索引", c.moveToFirst())
            assertEquals("v5 遗留疑问", c.getString(0))
        }
        db.close()
    }

    @Test
    fun `v6_to_v7_新增knowledge_source与knowledge_content_旧数据不受影响`() {
        helper.createDatabase(dbName, 6).apply {
            insertQuestion(uuid = "q-6", content = "v6 疑问", createdAt = 6_000L)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        // 旧数据保留
        db.query("SELECT content FROM question WHERE uuid = 'q-6'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("v6 疑问", c.getString(0))
        }
        // 新表已建好且为空（knowledge_source/knowledge_content 是同一条迁移里一起建的新表，
        // 不像 question_fts 那样有历史数据要 rebuild）
        assertEquals(0, db.count("knowledge_source"))
        assertEquals(0, db.count("knowledge_content"))
        assertEquals(0, db.count("knowledge_content_fts"))
        db.close()
    }

    @Test
    fun `v7_to_v8_新增rss_source与rss_article_旧数据不受影响`() {
        helper.createDatabase(dbName, 7).apply {
            insertQuestion(uuid = "q-7", content = "v7 疑问", createdAt = 7_000L)
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        db.query("SELECT content FROM question WHERE uuid = 'q-7'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("v7 疑问", c.getString(0))
        }
        assertEquals(0, db.count("rss_source"))
        assertEquals(0, db.count("rss_article"))
        db.close()
    }

    @Test
    fun `v1_to_v8_全链路迁移_数据完整保留`() {
        helper.createDatabase(dbName, 1).apply {
            insertTodo(uuid = "todo-v8", title = "跨八版验证", createdAt = 1L)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            dbName, 8, true, *ALL_MIGRATIONS,
        )

        // 跨 8 个版本迁移后，最早的 todo 数据仍在
        db.query("SELECT title FROM todo WHERE uuid = 'todo-v8'").use { c ->
            assertTrue(c.moveToFirst()); assertEquals("跨八版验证", c.getString(0))
        }
        // 各新表都已就绪
        assertEquals(0, db.count("anniversary"))
        assertEquals(0, db.count("period_record"))
        assertEquals(0, db.count("note"))
        assertEquals(0, db.count("note_fts"))
        assertEquals(0, db.count("question"))
        assertEquals(0, db.count("question_fts"))
        assertEquals(0, db.count("transaction_record"))
        assertEquals(0, db.count("category"))
        assertEquals(0, db.count("budget"))
        assertEquals(0, db.count("knowledge_source"))
        assertEquals(0, db.count("knowledge_content"))
        assertEquals(0, db.count("knowledge_content_fts"))
        assertEquals(0, db.count("rss_source"))
        assertEquals(0, db.count("rss_article"))
        db.close()
    }

    // ---------- 辅助：往各版本 db 插入测试数据 ----------
    //
    // 写成 SupportSQLiteDatabase 扩展，这样在 `helper.createDatabase(...).apply { insertTodo(...) }`
    // 块内 this 就是 db，直接调用即可。SQL 列名/反引号写法与 schemas/<version>.json 的 createSql 一致，
    // NOT NULL 字段全填，可空字段省略让 SQLite 用 NULL。
    //
    // 用 execSQL 而不是 insert(table, conflictAlgorithm, ContentValues)：前者更直观，
    // 后者的 conflictAlgorithm 参数对纯插入测试没意义。测试数据写死，无注入风险。

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertTodo(
        uuid: String,
        title: String,
        createdAt: Long,
        updatedAt: Long = createdAt,
    ) = execSQL(
        "INSERT INTO `todo` (`uuid`, `title`, `priority`, `tags`, `done`, `created_at`, `updated_at`) " +
            "VALUES ('$uuid', '$title', 1, '', 0, $createdAt, $updatedAt)",
    )

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertAnniversary(
        uuid: String,
        title: String,
        date: Long,
    ) = execSQL(
        "INSERT INTO `anniversary` (`uuid`, `title`, `date`, `is_lunar`, `repeat_type`, " +
            "`remind_days_before`, `pinned`, `created_at`, `updated_at`) " +
            "VALUES ('$uuid', '$title', $date, 0, 'ONCE', 0, 0, $date, $date)",
    )

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertPeriodRecord(
        uuid: String,
        startDate: Long,
    ) = execSQL(
        "INSERT INTO `period_record` (`uuid`, `start_date`, `created_at`, `updated_at`) " +
            "VALUES ('$uuid', $startDate, $startDate, $startDate)",
    )

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertNote(
        uuid: String,
        content: String,
        createdAt: Long,
    ) = execSQL(
        "INSERT INTO `note` (`uuid`, `content`, `tags`, `images_json`, `pinned`, " +
            "`created_at`, `updated_at`) " +
            "VALUES ('$uuid', '$content', '', '', 0, $createdAt, $createdAt)",
    )

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertQuestion(
        uuid: String,
        content: String,
        createdAt: Long,
    ) = execSQL(
        "INSERT INTO `question` (`uuid`, `content`, `tags`, `status`, " +
            "`created_at`, `updated_at`) " +
            "VALUES ('$uuid', '$content', '', 'OPEN', $createdAt, $createdAt)",
    )
}

/** 查表行数的简写，避免每个测试都写 query+moveToFirst+getInt 三连。 */
private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
    query("SELECT COUNT(*) FROM `$table`").use { c ->
        c.moveToFirst()
        c.getInt(0)
    }
