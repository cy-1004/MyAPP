package com.myapp.feature.question.data

import com.myapp.core.common.contract.NoteWriter
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.QuestionDao
import com.myapp.core.database.model.QuestionEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 疑问状态。stored 是数据库里的字符串值，加新状态不用迁移（与 AnniversaryRepeatType 同口径）。 */
enum class QuestionStatus(val stored: String) {
    OPEN("OPEN"),
    RESOLVED("RESOLVED"),
    ARCHIVED("ARCHIVED");

    companion object {
        fun from(stored: String?): QuestionStatus =
            entries.firstOrNull { it.stored == stored } ?: OPEN
    }
}

/** 领域模型：与数据库实体分开，避免 UI 直接依赖表结构。 */
data class Question(
    val id: Long,
    val uuid: String,
    val content: String,
    val context: String?,
    val tags: List<String>,
    val status: QuestionStatus,
    val answer: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val resolvedAt: Long?,
)

/**
 * 编辑页的可变草稿。与 [Question] 分开，免得把「只读展示字段」混进表单。
 *
 * [answer] 在草稿里始终是 String（不是 String?），便于 UI 双向绑定。
 * 保存时若 status != RESOLVED，把 answer 透传给 entity（不主动清空）--
 * 已写过答案的疑问再切回 OPEN 时不丢内容，符合用户预期。
 */
data class QuestionDraft(
    val id: Long = 0L,
    val uuid: String = UUID.randomUUID().toString(),
    val content: String = "",
    val context: String = "",
    val tags: List<String> = emptyList(),
    val status: QuestionStatus = QuestionStatus.OPEN,
    val answer: String = "",
) {
    val isNew: Boolean get() = id == 0L

    /** content 必填；RESOLVED 状态必须有 answer，否则无法保存。 */
    val canSave: Boolean get() = content.isNotBlank() &&
        (status != QuestionStatus.RESOLVED || answer.isNotBlank())
}

private fun QuestionEntity.toDomain(): Question = Question(
    id = id,
    uuid = uuid,
    content = content,
    context = context,
    tags = if (tags.isBlank()) emptyList() else tags.split(","),
    status = QuestionStatus.from(status),
    answer = answer,
    createdAt = createdAt,
    updatedAt = updatedAt,
    resolvedAt = resolvedAt,
)

@Singleton
class QuestionRepository @Inject constructor(
    private val dao: QuestionDao,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val noteWriter: NoteWriter,
) {

    /**
     * 列表查询。三种模式（与 NoteRepository 同口径）：
     *   - query 非空：FTS 全文搜索
     *   - tag 非空：按标签筛选
     *   - 都空：全部
     *
     * Repository 不分组：ViewModel 在内存里按 status 分待解决/已解决/已归档。
     */
    fun observe(query: String?, tag: String?): Flow<List<Question>> {
        val source = when {
            !query.isNullOrBlank() -> dao.search(escapeFtsQuery(query))
            !tag.isNullOrBlank() -> dao.observeByTag(tag)
            else -> dao.observeAll()
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    fun observeById(id: Long): Flow<Question?> = dao.observeById(id).map { it?.toDomain() }

    fun observeAllTags(): Flow<List<String>> = dao.observeAllTags().map { rows ->
        rows.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    /** 随机一条待解决疑问，首页卡片用。 */
    fun observeRandomPending(): Flow<Question?> = dao.observeRandomPending().map { it?.toDomain() }

    suspend fun loadDraft(id: Long): QuestionDraft? = withContext(io) {
        if (id == 0L) return@withContext QuestionDraft()
        val entity = dao.getById(id) ?: return@withContext null
        QuestionDraft(
            id = entity.id,
            uuid = entity.uuid,
            content = entity.content,
            context = entity.context.orEmpty(),
            tags = if (entity.tags.isBlank()) emptyList() else entity.tags.split(","),
            status = QuestionStatus.from(entity.status),
            answer = entity.answer.orEmpty(),
        )
    }

    /** 新建或更新，返回条目 id。 */
    suspend fun save(draft: QuestionDraft): Long = withContext(io) {
        val now = AppTime.now()
        if (draft.isNew) {
            dao.upsert(
                QuestionEntity(
                    uuid = draft.uuid,
                    content = draft.content,
                    context = draft.context.ifBlank { null },
                    tags = draft.tags.joinToString(","),
                    status = draft.status.stored,
                    answer = draft.answer.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                    resolvedAt = if (draft.status == QuestionStatus.RESOLVED) now else null,
                ),
            )
        } else {
            // 读改写：uuid / createdAt 不属于表单，整体构造会清掉它们
            val existing = dao.getById(draft.id) ?: return@withContext draft.id
            val wasResolved = existing.status == QuestionStatus.RESOLVED.stored
            val nowResolved = draft.status == QuestionStatus.RESOLVED
            // resolved_at：进 RESOLVED 时设置；离开时清空；保持 RESOLVED 时保留原值
            val resolvedAt = when {
                nowResolved && !wasResolved -> now
                !nowResolved -> null
                else -> existing.resolvedAt
            }
            dao.update(
                existing.copy(
                    content = draft.content,
                    context = draft.context.ifBlank { null },
                    tags = draft.tags.joinToString(","),
                    status = draft.status.stored,
                    // answer 不主动清空：切回 OPEN 时保留已写答案，再切回 RESOLVED 时不丢
                    answer = draft.answer.ifBlank { existing.answer },
                    updatedAt = now,
                    resolvedAt = resolvedAt,
                ),
            )
            draft.id
        }
    }

    suspend fun delete(id: Long): Unit = withContext(io) {
        dao.softDelete(id, AppTime.now())
    }

    suspend fun restore(id: Long): Unit = withContext(io) {
        dao.restore(id, AppTime.now())
    }

    /**
     * 把已解决的疑问转为一条笔记（PRD 3.5）。
     *
     * 调用 [NoteWriter] 跨模块契约，:feature:question 不直接依赖 :feature:note。
     * Question 状态**保持 RESOLVED**，不自动归档--用户可能想多次转换或继续编辑答案。
     *
     * @return 新笔记的 id；疑问不存在或未 RESOLVED 时返回 null
     */
    suspend fun convertToNote(questionId: Long): Long? = withContext(io) {
        val question = dao.getById(questionId) ?: return@withContext null
        if (question.status != QuestionStatus.RESOLVED.stored) return@withContext null
        val tags = if (question.tags.isBlank()) listOf("疑问") else {
            question.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() } + "疑问"
        }
        noteWriter.createNote(
            content = buildNoteContent(question.content, question.answer),
            tags = tags,
        )
    }

    /**
     * FTS MATCH 构造，与 NoteRepository 同一套做法：用户输入直接做 MATCH 会把
     * 空格当 AND、把 `* : "` 当操作符。每个词用 `"..."` 短语包裹（内部的 `"` 双写转义），
     * 再在引号内加 `*` 做前缀匹配（FTS4 只认引号内的星号，`"S*"`；放外面 `"S"*` 不生效，
     * FTS5 则相反）。不加前缀则整 token 相等，用户必须打完整词才命中。多词之间仍是 AND。
     */
    private fun escapeFtsQuery(query: String): String = query
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { "\"${it.replace("\"", "\"\"")}*\"" }
}

/**
 * 把疑问 + 答案合成 Markdown 笔记正文。
 * 用 `# 疑问` / `# 解答` 二级段落，与 [com.myapp.feature.note.ui.MarkdownRenderer] 已支持的 `#` 语法一致。
 */
internal fun buildNoteContent(content: String, answer: String?): String =
    "# 疑问\n\n$content\n\n# 解答\n\n${answer ?: ""}"
