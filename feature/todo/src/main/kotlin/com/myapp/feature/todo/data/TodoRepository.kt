package com.myapp.feature.todo.data

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.TodoDao
import com.myapp.core.database.model.TodoEntity
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
    val done: Boolean,
    val isOverdue: Boolean,
)

private fun TodoEntity.toDomain(now: Long) = Todo(
    id = id,
    title = title,
    note = note,
    dueAt = dueAt,
    priority = priority,
    done = done,
    isOverdue = dueAt != null && !done && dueAt < now,
)

@Singleton
class TodoRepository @Inject constructor(
    private val dao: TodoDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeToday(): Flow<List<Todo>> {
        val range = AppTime.todayRange()
        val now = AppTime.now()
        return dao.observeTodayAndOverdue(now = now, endOfToday = range.last + 1)
            .map { list -> list.map { it.toDomain(now) } }
    }

    fun observeActive(): Flow<List<Todo>> {
        val now = AppTime.now()
        return dao.observeActive().map { list -> list.map { it.toDomain(now) } }
    }

    fun observeActiveCount(): Flow<Int> = dao.observeActiveCount()

    suspend fun add(title: String, dueAt: Long? = null, priority: Int = 1): Long =
        withContext(io) {
            val now = AppTime.now()
            dao.upsert(
                TodoEntity(
                    title = title,
                    dueAt = dueAt,
                    priority = priority,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

    suspend fun setDone(id: Long, done: Boolean) = withContext(io) {
        val now = AppTime.now()
        dao.setDone(id = id, done = done, doneAt = if (done) now else null, now = now)
        // TODO 重复任务：完成后生成下一条（PRD 3.3）
        // TODO 完成/取消完成时同步取消或重建提醒闹钟
    }

    suspend fun delete(id: Long) = withContext(io) {
        dao.softDelete(id, AppTime.now())
    }
}
