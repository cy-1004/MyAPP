package com.myapp.feature.todo.data

import com.myapp.core.common.contract.ReminderRequest
import com.myapp.core.common.contract.ReminderScheduler
import com.myapp.core.common.contract.ReminderSource
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.TodoDao
import com.myapp.core.database.model.TodoEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 领域模型：与数据库实体分开，避免 UI 直接依赖表结构。 */
data class Todo(
    val id: Long,
    val title: String,
    val note: String?,
    val dueAt: Long?,
    val priority: Int,
    val repeatRule: String?,
    val done: Boolean,
    val doneAt: Long?,
    val isOverdue: Boolean,
)

/** 编辑页的可变草稿。与 [Todo] 分开，免得把「只读展示字段」混进表单。 */
data class TodoDraft(
    val id: Long = 0L,
    val title: String = "",
    val note: String = "",
    val dueAt: Long? = null,
    val priority: Int = Priority.NORMAL,
    val repeatRule: String = RepeatRule.NONE,
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = title.isNotBlank()
}

object Priority {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2

    fun label(value: Int): String = when (value) {
        HIGH -> "高"
        LOW -> "低"
        else -> "中"
    }
}

/** 待办列表的四个视图（PRD 3.3）。 */
enum class TodoFilter(val label: String) {
    TODAY("今天"),
    WEEK("最近 7 天"),
    ALL("全部"),
    DONE("已完成"),
}

private fun TodoEntity.toDomain(now: Long): Todo {
    // 先取到局部变量再比较：dueAt 是别的模块（:core:database）里的 public 属性，
    // Kotlin 不对跨模块的 public 属性做智能转换（它无法保证对方不是自定义 getter）。
    val due = dueAt
    return Todo(
        id = id,
        title = title,
        note = note,
        dueAt = due,
        priority = priority,
        repeatRule = repeatRule,
        done = done,
        doneAt = doneAt,
        isOverdue = due != null && !done && due < now,
    )
}

/** 待办提醒的业务唯一键，:app 的 [ReminderScheduler] 与本仓库共用同一套约定。 */
fun todoReminderKey(id: Long): String = "todo:$id"

@Singleton
class TodoRepository @Inject constructor(
    private val dao: TodoDao,
    private val reminderScheduler: ReminderScheduler,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ReminderSource {

    /**
     * 按视图取列表。
     *
     * `now` 在每次订阅时取一次即可：过了零点最多是「逾期标记」晚一步，
     * 而为此维持一个每分钟发射的 ticker，会让整个列表反复重组——
     * 在 144Hz 屏上这个代价远大于收益。切回前台时会重新订阅、自然刷新。
     */
    fun observe(filter: TodoFilter): Flow<List<Todo>> {
        val now = AppTime.now()
        val source = when (filter) {
            TodoFilter.TODAY -> dao.observeUndoneBefore(now = now, before = AppTime.todayRange().last + 1)
            TodoFilter.WEEK -> dao.observeUndoneBefore(now = now, before = endOfWeek())
            TodoFilter.ALL -> dao.observeActive(now = now)
            TodoFilter.DONE -> dao.observeCompleted()
        }
        return source.map { list -> list.map { it.toDomain(now) } }
    }

    /** 首页卡片用：今日未完成 + 逾期。 */
    fun observeToday(): Flow<List<Todo>> = observe(TodoFilter.TODAY)

    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    fun observeById(id: Long): Flow<Todo?> {
        val now = AppTime.now()
        return dao.observeById(id).map { entity -> entity?.toDomain(now) }
    }

    suspend fun loadDraft(id: Long): TodoDraft = withContext(io) {
        if (id == 0L) return@withContext TodoDraft()
        val entity = dao.getById(id) ?: return@withContext TodoDraft()
        TodoDraft(
            id = entity.id,
            title = entity.title,
            note = entity.note.orEmpty(),
            dueAt = entity.dueAt,
            priority = entity.priority,
            repeatRule = entity.repeatRule.orEmpty(),
        )
    }

    /** 新建或更新，返回条目 id。 */
    suspend fun save(draft: TodoDraft): Long = withContext(io) {
        val now = AppTime.now()
        if (draft.isNew) {
            dao.upsert(
                TodoEntity(
                    title = draft.title.trim(),
                    note = draft.note.trim().ifBlank { null },
                    dueAt = draft.dueAt,
                    priority = draft.priority,
                    repeatRule = draft.repeatRule.ifBlank { null },
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            // 读改写而非整体 REPLACE：uuid / createdAt / done 这些字段不属于表单，
            // 直接构造实体会把它们悄悄清掉（uuid 丢了将来同步就对不上了）。
            val existing = dao.getById(draft.id) ?: return@withContext draft.id
            dao.update(
                existing.copy(
                    title = draft.title.trim(),
                    note = draft.note.trim().ifBlank { null },
                    dueAt = draft.dueAt,
                    priority = draft.priority,
                    repeatRule = draft.repeatRule.ifBlank { null },
                    updatedAt = now,
                ),
            )
            draft.id
        }
    }

    suspend fun add(title: String, dueAt: Long? = null, priority: Int = Priority.NORMAL): Long =
        save(TodoDraft(title = title, dueAt = dueAt, priority = priority))

    /**
     * 勾选 / 取消勾选。
     *
     * 完成一条重复任务时，顺带按规则生成下一次——生成的是新条目而不是改旧条目的日期，
     * 这样「已完成」视图里能看到完整的历史记录。
     */
    suspend fun setDone(id: Long, done: Boolean): Unit = withContext(io) {
        val now = AppTime.now()
        dao.setDone(id = id, done = done, doneAt = if (done) now else null, now = now)
        if (done) spawnNextOccurrence(id, now)
    }

    /**
     * 完成一条带重复规则的待办后，生成下一次。
     * 任一前提不满足就静默跳过——重复规则是可选功能，不该让主流程分叉。
     */
    private suspend fun spawnNextOccurrence(id: Long, now: Long) {
        val entity = dao.getById(id) ?: return
        val rule = entity.repeatRule
        val due = entity.dueAt
        if (rule.isNullOrBlank() || due == null) return

        // 从「原定截止时间」而不是「完成时间」推算，否则拖延几天会把整条链往后推
        val nextDue = RepeatRule.nextDueAt(rule, due) ?: return
        val newId = dao.upsert(
            entity.copy(
                // id 归零让 Room 分配新主键；uuid 也必须重新生成，
                // 否则新条目会撞上 uuid 唯一索引
                id = 0,
                uuid = UUID.randomUUID().toString(),
                dueAt = nextDue,
                done = false,
                doneAt = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        reminderScheduler.schedule(
            key = todoReminderKey(newId),
            triggerAtMillis = nextDue,
            title = entity.title,
            body = "待办到期",
        )
    }

    suspend fun delete(id: Long): Unit = withContext(io) {
        dao.softDelete(id, AppTime.now())
        reminderScheduler.cancel(todoReminderKey(id))
    }

    /** 撤销删除。软删除保留了整行数据，恢复是无损的。 */
    suspend fun restore(id: Long): Unit = withContext(io) {
        dao.restore(id, AppTime.now())
    }

    private fun endOfWeek(): Long = with(AppTime) {
        today().plusDays(7).toEpochMilliAtStartOfDay()
    }

    /** 开机重建用：全部未完成、有截止时间且尚未过期的待办（DAO 查询已保证 due_at 非空）。 */
    override suspend fun pendingReminders(): List<ReminderRequest> = withContext(io) {
        dao.getPendingReminders(after = AppTime.now()).map { entity ->
            ReminderRequest(
                key = todoReminderKey(entity.id),
                triggerAtMillis = requireNotNull(entity.dueAt),
                title = entity.title,
                body = "待办到期",
            )
        }
    }
}
