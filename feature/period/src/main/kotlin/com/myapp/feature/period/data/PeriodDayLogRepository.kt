package com.myapp.feature.period.data

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.PeriodDayLogDao
import com.myapp.core.database.model.PeriodDayLogEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 每日身体情况记录（PRD 3.2）。
 *
 * 与 [PeriodRepository] 分开：那边管「一次月经」并且还要负责提醒排程，
 * 这边只是一张按日期存取的小表，混在一起只会让那个类继续膨胀。
 */
@Singleton
class PeriodDayLogRepository @Inject constructor(
    private val dao: PeriodDayLogDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 按日期索引，日历直接查表；表很小，整表读回来比按月查简单。 */
    fun observeByDate(): Flow<Map<LocalDate, PeriodDayLog>> =
        dao.observeAll().map { list -> list.associate { it.date() to it.toDomain() } }

    suspend fun get(date: LocalDate): PeriodDayLog? = withContext(io) {
        dao.getByDate(date.toEpochDay())?.toDomain()
    }

    /**
     * 保存一天的记录。
     *
     * **标签与文本都空 = 删除这一天**：用户把内容清光再点保存，意思就是「这天没什么好记的」，
     * 留一条空记录会让日历上多一个什么都点不出来的标记。
     */
    suspend fun save(log: PeriodDayLog): Unit = withContext(io) {
        val epochDay = log.date.toEpochDay()
        if (log.isEmpty) {
            dao.deleteByDate(epochDay)
            return@withContext
        }
        val now = AppTime.now()
        val existing = dao.getByDate(epochDay)
        dao.upsert(
            existing?.copy(
                tags = DayLogTag.join(log.tags),
                note = log.note.trim().ifBlank { null },
                updatedAt = now,
            ) ?: PeriodDayLogEntity(
                date = epochDay,
                tags = DayLogTag.join(log.tags),
                note = log.note.trim().ifBlank { null },
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun delete(date: LocalDate): Unit = withContext(io) {
        dao.deleteByDate(date.toEpochDay())
    }
}

private fun PeriodDayLogEntity.date(): LocalDate = LocalDate.ofEpochDay(date)

private fun PeriodDayLogEntity.toDomain(): PeriodDayLog = PeriodDayLog(
    date = LocalDate.ofEpochDay(date),
    tags = DayLogTag.parse(tags),
    note = note.orEmpty(),
)
