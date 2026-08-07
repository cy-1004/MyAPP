package com.myapp.feature.note.data

import android.content.Context
import android.net.Uri
import com.myapp.core.common.contract.NoteWriter
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.NoteDao
import com.myapp.core.database.model.NoteEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 领域模型：与数据库实体分开，避免 UI 直接依赖表结构。 */
data class Note(
    val id: Long,
    val uuid: String,
    val content: String,
    val tags: List<String>,
    val images: List<String>,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * 编辑页的可变草稿。与 [Note] 分开，免得把「只读展示字段」混进表单。
 *
 * [uuid] 在新建时就生成：图片在保存前就要复制到 `filesDir/notes/<uuid>/`，
 * 必须先有 uuid 才能建目录。新建保存时把 draft.uuid 写进实体，覆盖默认值。
 */
data class NoteDraft(
    val id: Long = 0L,
    val uuid: String = UUID.randomUUID().toString(),
    val content: String = "",
    val tags: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val pinned: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = content.isNotBlank()
}

private fun NoteEntity.toDomain(): Note = Note(
    id = id,
    uuid = uuid,
    content = content,
    tags = if (tags.isBlank()) emptyList() else tags.split(","),
    images = if (imagesJson.isBlank()) emptyList() else imagesJson.split(SEPARATOR),
    pinned = pinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** [NoteEntity.imagesJson] 用的分隔符，与 [com.myapp.core.database.Converters] 一致。 */
internal const val SEPARATOR = ""

@Singleton
class NoteRepository @Inject constructor(
    private val dao: NoteDao,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : NoteWriter {

    /**
     * 列表查询。三种模式：
     *   - query 非空：FTS 全文搜索
     *   - tag 非空：按标签筛选
     *   - 都空：全部
     *
     * query 与 tag 同时非空时取 query 优先（搜索是更强的意图）。
     * 组合查询 V1 不做，避免 FTS MATCH 与 LIKE 的转义叠加。
     */
    fun observe(query: String?, tag: String?): Flow<List<Note>> {
        val source = when {
            !query.isNullOrBlank() -> dao.search(escapeFtsQuery(query))
            !tag.isNullOrBlank() -> dao.observeByTag(tag)
            else -> dao.observeAll()
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    fun observeById(id: Long): Flow<Note?> = dao.observeById(id).map { it?.toDomain() }

    /**
     * 全部已用过的标签。DAO 返回的是逗号分隔字符串列表，这里拆分去重给 UI 做 chip。
     */
    fun observeAllTags(): Flow<List<String>> = dao.observeAllTags().map { rows ->
        rows.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    suspend fun loadDraft(id: Long): NoteDraft = withContext(io) {
        if (id == 0L) return@withContext NoteDraft()
        val entity = dao.getById(id) ?: return@withContext NoteDraft()
        NoteDraft(
            id = entity.id,
            uuid = entity.uuid,
            content = entity.content,
            tags = if (entity.tags.isBlank()) emptyList() else entity.tags.split(","),
            images = if (entity.imagesJson.isBlank()) emptyList() else entity.imagesJson.split(SEPARATOR),
            pinned = entity.pinned,
        )
    }

    /** 新建或更新，返回条目 id。 */
    suspend fun save(draft: NoteDraft): Long = withContext(io) {
        val now = AppTime.now()
        if (draft.isNew) {
            dao.upsert(
                NoteEntity(
                    uuid = draft.uuid,
                    content = draft.content,
                    tags = draft.tags.joinToString(","),
                    imagesJson = draft.images.joinToString(SEPARATOR),
                    pinned = draft.pinned,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            // 读改写：uuid / createdAt 不属于表单，整体构造会清掉它们
            val existing = dao.getById(draft.id) ?: return@withContext draft.id
            dao.update(
                existing.copy(
                    content = draft.content,
                    tags = draft.tags.joinToString(","),
                    imagesJson = draft.images.joinToString(SEPARATOR),
                    pinned = draft.pinned,
                    updatedAt = now,
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

    suspend fun togglePinned(id: Long, pinned: Boolean): Unit = withContext(io) {
        dao.togglePinned(id, pinned, AppTime.now())
    }

    /**
     * 把 Photo Picker 返回的 [Uri] 列表复制到 `filesDir/notes/<uuid>/`，
     * 返回相对路径列表（与 [Note.images] 约定一致）。
     *
     * 串行执行：Uri 只在 Activity 生命周期内有效，并发复制可能在 Activity 销毁后
     * 才访问 Uri，触发 SecurityException。串行 + 立刻复制，把窗口缩到最短。
     * 文件名带 index 防同毫秒覆盖。复制失败的单张静默跳过。
     */
    suspend fun importImages(uuid: String, uris: List<Uri>): List<String> = withContext(io) {
        if (uris.isEmpty()) return@withContext emptyList()
        val dir = File(context.filesDir, "notes/$uuid").apply { mkdirs() }
        val ts = System.currentTimeMillis()
        val results = mutableListOf<String>()
        uris.forEachIndexed { index, uri ->
            val dest = File(dir, "$ts-$index.jpg")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                } != null
            }.getOrDefault(false)
            if (copied) {
                results += "notes/$uuid/${dest.name}"
            }
        }
        results
    }

    /**
     * 实现 [NoteWriter]：跨模块「存为笔记」入口（如资讯页的「存为笔记」按钮）。
     * 不暴露 [NoteDraft] 给外部模块，外部只传 content + tags。
     */
    override suspend fun createNote(content: String, tags: List<String>): Long =
        save(NoteDraft(content = content, tags = tags))

    /**
     * FTS MATCH 转义：用户输入直接做 MATCH 会把空格当 AND、把 `* : "` 当操作符。
     * 用 `"..."` 短语包裹，并把内部的 `"` 双写转义，是最简单的安全做法。
     */
    private fun escapeFtsQuery(query: String): String {
        val escaped = query.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
