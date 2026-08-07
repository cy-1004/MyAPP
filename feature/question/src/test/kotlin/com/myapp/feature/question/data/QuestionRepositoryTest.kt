package com.myapp.feature.question.data

import com.myapp.core.common.contract.NoteWriter
import com.myapp.core.database.dao.QuestionDao
import com.myapp.core.database.model.QuestionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QuestionRepository 的核心逻辑测试。
 *
 * 重点守三件事：
 *   1. [buildNoteContent] 的 Markdown 格式（PRD 3.5「转为笔记」的产物）
 *   2. [QuestionDraft.canSave] 的状态机不变式（RESOLVED 必须有 answer）
 *   3. [QuestionRepository.save] 对 `resolved_at` 的设置/清空/保留逻辑
 *   4. [QuestionRepository.convertToNote] 只对 RESOLVED 疑问生效
 */
class QuestionRepositoryTest {

    @Test
    fun `buildNoteContent 正常情况`() {
        assertEquals(
            "# 疑问\n\n什么是 SOLID\n\n# 解答\n\n五大原则",
            buildNoteContent("什么是 SOLID", "五大原则"),
        )
    }

    @Test
    fun `buildNoteContent answer 为 null 时解答段留空`() {
        assertEquals(
            "# 疑问\n\n问题\n\n# 解答\n\n",
            buildNoteContent("问题", null),
        )
    }

    @Test
    fun `canSave - OPEN 状态 content 非空即可`() {
        assertTrue(QuestionDraft(content = "x", status = QuestionStatus.OPEN).canSave)
    }

    @Test
    fun `canSave - content 空白不可保存`() {
        assertFalse(QuestionDraft(content = "   ", status = QuestionStatus.OPEN).canSave)
        assertFalse(QuestionDraft(content = "", status = QuestionStatus.OPEN).canSave)
    }

    @Test
    fun `canSave - RESOLVED 必须有 answer`() {
        assertFalse(QuestionDraft(content = "x", status = QuestionStatus.RESOLVED, answer = "").canSave)
        assertFalse(QuestionDraft(content = "x", status = QuestionStatus.RESOLVED, answer = "  ").canSave)
        assertTrue(QuestionDraft(content = "x", status = QuestionStatus.RESOLVED, answer = "y").canSave)
    }

    @Test
    fun `canSave - ARCHIVED 不要求 answer`() {
        assertTrue(QuestionDraft(content = "x", status = QuestionStatus.ARCHIVED, answer = "").canSave)
    }

    @Test
    fun `save 新建 RESOLVED 时 resolved_at 等于 updated_at`() = runTest {
        val fake = FakeQuestionDao()
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), FakeNoteWriter())

        val id = repo.save(
            QuestionDraft(
                content = "问题",
                status = QuestionStatus.RESOLVED,
                answer = "答案",
            ),
        )

        val saved = fake.byId(id)!!
        assertEquals("RESOLVED", saved.status)
        assertEquals(saved.updatedAt, saved.resolvedAt)
    }

    @Test
    fun `save OPEN 转 RESOLVED 时设置 resolved_at`() = runTest {
        val fake = FakeQuestionDao()
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), FakeNoteWriter())

        val id = repo.save(QuestionDraft(content = "问题", status = QuestionStatus.OPEN))
        val openEntity = fake.byId(id)!!
        assertNull(openEntity.resolvedAt)

        repo.save(
            QuestionDraft(
                id = id,
                uuid = openEntity.uuid,
                content = "问题",
                status = QuestionStatus.RESOLVED,
                answer = "答案",
            ),
        )

        val resolvedEntity = fake.byId(id)!!
        assertEquals("RESOLVED", resolvedEntity.status)
        assertTrue("resolved_at 应被设置", resolvedEntity.resolvedAt != null)
        assertEquals(resolvedEntity.updatedAt, resolvedEntity.resolvedAt)
    }

    @Test
    fun `save RESOLVED 转 OPEN 时清空 resolved_at 但保留 answer`() = runTest {
        val fake = FakeQuestionDao()
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), FakeNoteWriter())

        val id = repo.save(
            QuestionDraft(content = "问题", status = QuestionStatus.RESOLVED, answer = "答案"),
        )
        val resolvedEntity = fake.byId(id)!!
        assertTrue(resolvedEntity.resolvedAt != null)

        repo.save(
            QuestionDraft(
                id = id,
                uuid = resolvedEntity.uuid,
                content = "问题",
                status = QuestionStatus.OPEN,
                answer = "",
            ),
        )

        val reopenedEntity = fake.byId(id)!!
        assertEquals("OPEN", reopenedEntity.status)
        assertNull("resolved_at 应清空", reopenedEntity.resolvedAt)
        assertEquals("答案", reopenedEntity.answer)
    }

    @Test
    fun `save 保持 RESOLVED 时 resolved_at 保留原值不刷新`() = runTest {
        val fake = FakeQuestionDao()
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), FakeNoteWriter())

        val id = repo.save(
            QuestionDraft(content = "问题", status = QuestionStatus.RESOLVED, answer = "答案"),
        )
        val firstResolved = fake.byId(id)!!
        val firstResolvedAt = firstResolved.resolvedAt!!

        // 再保存一次（改答案），仍是 RESOLVED
        repo.save(
            QuestionDraft(
                id = id,
                uuid = firstResolved.uuid,
                content = "问题",
                status = QuestionStatus.RESOLVED,
                answer = "更好的答案",
            ),
        )

        val secondResolved = fake.byId(id)!!
        assertEquals("resolved_at 不应被刷新", firstResolvedAt, secondResolved.resolvedAt)
        assertEquals("更好的答案", secondResolved.answer)
    }

    @Test
    fun `convertToNote - RESOLVED 疑问调用 NoteWriter 并返回新笔记 id`() = runTest {
        val fake = FakeQuestionDao()
        val noteWriter = FakeNoteWriter(returnsId = 42L)
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), noteWriter)

        val id = repo.save(
            QuestionDraft(
                content = "什么是 SOLID",
                tags = listOf("架构"),
                status = QuestionStatus.RESOLVED,
                answer = "五大原则",
            ),
        )

        val noteId = repo.convertToNote(id)
        assertEquals(42L, noteId)
        assertEquals(1, noteWriter.calls.size)
        assertEquals("# 疑问\n\n什么是 SOLID\n\n# 解答\n\n五大原则", noteWriter.calls[0].content)
        assertEquals(listOf("架构", "疑问"), noteWriter.calls[0].tags)
    }

    @Test
    fun `convertToNote - OPEN 疑问返回 null 不调 NoteWriter`() = runTest {
        val fake = FakeQuestionDao()
        val noteWriter = FakeNoteWriter(returnsId = 99L)
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), noteWriter)

        val id = repo.save(QuestionDraft(content = "问题", status = QuestionStatus.OPEN))

        assertNull(repo.convertToNote(id))
        assertEquals(0, noteWriter.calls.size)
    }

    @Test
    fun `convertToNote - 不存在的 id 返回 null`() = runTest {
        val fake = FakeQuestionDao()
        val noteWriter = FakeNoteWriter(returnsId = 99L)
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), noteWriter)

        assertNull(repo.convertToNote(9999L))
        assertEquals(0, noteWriter.calls.size)
    }

    @Test
    fun `convertToNote - 疑问无标签时仅加疑问标签`() = runTest {
        val fake = FakeQuestionDao()
        val noteWriter = FakeNoteWriter(returnsId = 1L)
        val repo = QuestionRepository(fake, StandardTestDispatcher(testScheduler), noteWriter)

        val id = repo.save(
            QuestionDraft(content = "问题", status = QuestionStatus.RESOLVED, answer = "答案"),
        )

        repo.convertToNote(id)
        assertEquals(listOf("疑问"), noteWriter.calls[0].tags)
    }
}

/** 简化 NoteWriter：记录所有调用，返回预设 id。 */
private class FakeNoteWriter(private val returnsId: Long = 1L) : NoteWriter {
    data class Call(val content: String, val tags: List<String>)
    val calls = mutableListOf<Call>()

    override suspend fun createNote(content: String, tags: List<String>): Long {
        calls += Call(content, tags)
        return returnsId
    }
}

/** 内存版 QuestionDao，只实现 getById/upsert/update，Flow 方法返回 emptyFlow。 */
private class FakeQuestionDao : QuestionDao {
    private val store = mutableMapOf<Long, QuestionEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<QuestionEntity>> = flowOf(store.values.toList())
    override fun observeByTag(tag: String): Flow<List<QuestionEntity>> = emptyFlow()
    override fun search(query: String): Flow<List<QuestionEntity>> = emptyFlow()
    override fun observeAllTags(): Flow<List<String>> = emptyFlow()
    override fun observeRandomPending(): Flow<QuestionEntity?> = emptyFlow()
    override fun observeById(id: Long): Flow<QuestionEntity?> = emptyFlow()

    override suspend fun getById(id: Long): QuestionEntity? = store[id]

    override suspend fun upsert(question: QuestionEntity): Long {
        val id = if (question.id == 0L) nextId++ else question.id
        val entity = question.copy(id = id)
        store[id] = entity
        return id
    }

    override suspend fun update(question: QuestionEntity) {
        store[question.id] = question
    }

    override suspend fun softDelete(id: Long, now: Long) {
        store[id]?.let { store[id] = it.copy(deletedAt = now, updatedAt = now) }
    }

    override suspend fun restore(id: Long, now: Long) {
        store[id]?.let { store[id] = it.copy(deletedAt = null, updatedAt = now) }
    }

    fun byId(id: Long): QuestionEntity? = store[id]
}
