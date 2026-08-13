package com.myapp.core.database.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myapp.core.database.DATABASE_SCHEMA_VERSION
import com.myapp.core.database.MyAppDatabase
import com.myapp.core.database.model.AnniversaryEntity
import com.myapp.core.database.model.CategoryEntity
import com.myapp.core.database.model.NoteEntity
import com.myapp.core.database.model.RssArticleEntity
import com.myapp.core.database.model.RssSourceEntity
import com.myapp.core.database.model.TodoEntity
import com.myapp.core.database.model.TransactionEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 整库导出/恢复的真实往返测试（PRD 3.13）。
 *
 * 用内存 Room 库而不是真机上的用户数据：恢复是「清空再灌入」的破坏性操作，
 * 拿真实数据试错的代价太高，而这里可以随便重跑。
 */
@RunWith(RobolectricTestRunner::class)
class BackupDataSourceTest {

    private lateinit var db: MyAppDatabase
    private lateinit var dataSource: BackupDataSource

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyAppDatabase::class.java,
        )
            // rssArticleCount() 走的是 RoomDatabase.query 原始查询（BackupDao 已不再暴露
            // 读 rss_article 的方法），它是阻塞调用，测试线程上必须显式放行
            .allowMainThreadQueries()
            .build()
        dataSource = BackupDataSource(db, db.backupDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        db.todoDao().upsert(
            TodoEntity(id = 1, uuid = "t1", title = "买牛奶", createdAt = 1, updatedAt = 2),
        )
        // 软删除墓碑
        db.todoDao().upsert(
            TodoEntity(id = 2, uuid = "t2", title = "已删", createdAt = 1, updatedAt = 2, deletedAt = 9),
        )
        db.noteDao().upsert(
            NoteEntity(id = 1, uuid = "n1", content = "正文 🎉", createdAt = 1, updatedAt = 2),
        )
        db.anniversaryDao().upsert(
            AnniversaryEntity(id = 1, uuid = "a1", title = "纪念日", date = 20000, createdAt = 1, updatedAt = 2),
        )
        db.categoryDao().upsert(
            CategoryEntity(
                id = 1, uuid = "c1", name = "餐饮", icon = "food", color = "#F80",
                sortOrder = 1, createdAt = 1, updatedAt = 2,
            ),
        )
        db.transactionDao().upsert(
            TransactionEntity(
                id = 1, uuid = "x1", amount = 1234, direction = "EXPENSE", categoryId = 1,
                occurredAt = 100, status = "CONFIRMED", source = "MANUAL", createdAt = 1, updatedAt = 2,
            ),
        )
        db.rssSourceDao().upsert(
            RssSourceEntity(id = 1, uuid = "r1", url = "https://e.com/f", title = "源", sortOrder = 1, createdAt = 1, updatedAt = 2),
        )
        db.rssArticleDao().insertAll(
            listOf(
                RssArticleEntity(
                    id = 1, sourceId = 1, guid = "g1", link = "https://e.com/a",
                    title = "文章", summary = "摘要", content = "正文", publishedAt = 1, fetchedAt = 1,
                ),
            ),
        )
    }

    @Test
    fun `export captures every backed-up table including tombstones`() = runTest {
        seed()
        val snapshot = dataSource.export(now = 123L)

        assertEquals(DATABASE_SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals(123L, snapshot.createdAt)
        assertEquals(2, snapshot.todos.size)
        assertEquals(9L, snapshot.todos.single { it.id == 2L }.deletedAt)
        assertEquals(1, snapshot.notes.size)
        assertEquals(1, snapshot.anniversaries.size)
        assertEquals(1, snapshot.categories.size)
        assertEquals(1, snapshot.transactions.size)
        assertEquals(1, snapshot.rssSources.size)
    }

    @Test
    fun `restore replaces local data with the snapshot`() = runTest {
        seed()
        val snapshot = dataSource.export(now = 1L)

        // 模拟换机/数据被改：改掉一条、加一条本地独有的
        db.todoDao().upsert(TodoEntity(id = 1, uuid = "t1", title = "被改坏的标题", createdAt = 1, updatedAt = 5))
        db.todoDao().upsert(TodoEntity(id = 99, uuid = "t99", title = "本机多出来的", createdAt = 1, updatedAt = 5))

        dataSource.restore(snapshot)

        val after = dataSource.export(now = 2L)
        assertEquals(2, after.todos.size)
        assertEquals("买牛奶", after.todos.single { it.id == 1L }.title)
        assertTrue("本机多出来的行必须被覆盖掉", after.todos.none { it.id == 99L })
        assertEquals(9L, after.todos.single { it.id == 2L }.deletedAt)
        assertEquals("正文 🎉", after.notes.single().content)
        assertEquals(1234L, after.transactions.single().amount)
    }

    @Test
    fun `restore clears the rss article cache even though it is not backed up`() = runTest {
        seed()
        val snapshot = dataSource.export(now = 1L)

        dataSource.restore(snapshot)

        // 订阅源整表覆盖过，留着旧文章会让 source_id 指向对不上的源
        assertEquals(0, rssArticleCount())
        assertEquals(1, dataSource.export(now = 2L).rssSources.size)
    }

    /** 高版本备份灌进低版本 App，表结构可能对不上，必须挡住而不是灌一半炸掉。 */
    @Test
    fun `restore refuses a snapshot from a newer schema`() {
        val fromFuture = DatabaseSnapshot(
            schemaVersion = DATABASE_SCHEMA_VERSION + 1,
            createdAt = 1L,
        )
        assertThrows(IllegalStateException::class.java) {
            runBlocking { dataSource.restore(fromFuture) }
        }
    }

    private fun rssArticleCount(): Int =
        db.query("SELECT COUNT(*) FROM rss_article", null).use {
            it.moveToFirst()
            it.getInt(0)
        }

    @Test
    fun `restoring an empty snapshot empties the database`() = runTest {
        seed()
        dataSource.restore(DatabaseSnapshot(schemaVersion = DATABASE_SCHEMA_VERSION, createdAt = 1L))
        assertEquals(0, dataSource.export(now = 2L).rowCount)
    }
}
