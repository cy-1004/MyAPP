package com.myapp.feature.period.data

import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.PeriodDao
import com.myapp.core.database.model.PeriodRecordEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** 一次经期记录。[endDate] 为空表示进行中。 */
data class PeriodRecord(
    val id: Long,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val note: String?,
) {
    val isOngoing: Boolean get() = endDate == null

    /** 持续天数，含首尾两天；进行中时为 null。 */
    val durationDays: Int?
        get() = endDate?.let { (ChronoUnit.DAYS.between(startDate, it) + 1).toInt() }

    fun covers(date: LocalDate): Boolean {
        if (date.isBefore(startDate)) return false
        val end = endDate ?: return false
        return !date.isAfter(end)
    }
}

/** 当前状态。UI 的主标题直接由它决定。 */
sealed interface PeriodStatus {
    /** 经期中，[day] 从 1 开始。 */
    data class Ongoing(val day: Int, val recordId: Long) : PeriodStatus

    /** 等待下一次。[daysUntil] 为负表示已经推迟了几天。 */
    data class Waiting(val daysUntil: Long, val predictedStart: LocalDate) : PeriodStatus

    /** 一条记录都没有。 */
    data object NoData : PeriodStatus
}

/**
 * 统计与预测结果。
 *
 * [reliable] 为假时 UI 必须显式标注「样本不足，仅供参考」（PRD 3.2）——
 * 给一个看起来很确定、其实没依据的日期，比不给更糟。
 */
data class PeriodState(
    val records: List<PeriodRecord>,
    val avgCycleDays: Int,
    val avgDurationDays: Int?,
    /** 参与平均的「间隔」个数，= 记录数 - 1（最多取 [CYCLE_SAMPLE_SIZE] 个）。 */
    val cycleSamples: Int,
    val reliable: Boolean,
    val predictedStart: LocalDate?,
    /** 预测的这一次经期区间，用于日历上画浅色块。 */
    val predictedRange: ClosedRange<LocalDate>?,
    val status: PeriodStatus,
) {
    companion object {
        val Empty = PeriodState(
            records = emptyList(),
            avgCycleDays = DEFAULT_CYCLE_DAYS,
            avgDurationDays = null,
            cycleSamples = 0,
            reliable = false,
            predictedStart = null,
            predictedRange = null,
            status = PeriodStatus.NoData,
        )
    }
}

/** 无样本时的默认周期长度（PRD 3.2）。 */
const val DEFAULT_CYCLE_DAYS = 28

/** 无样本时的默认经期天数，仅用于在日历上画预测区间的长度。 */
private const val DEFAULT_DURATION_DAYS = 5

/** 近几次参与平均（PRD 3.2：近 6 次）。 */
private const val CYCLE_SAMPLE_SIZE = 6

/**
 * 超过这个天数还没记录结束，就不再当作「进行中」。
 *
 * 现实中忘记点「结束」比经期真的持续半个月常见得多，
 * 不设这个上限的话，一次漏记会让首页永远显示「经期第 87 天」。
 */
private const val MAX_ONGOING_DAYS = 15

@Singleton
class PeriodRepository @Inject constructor(
    private val dao: PeriodDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    fun observeState(): Flow<PeriodState> = dao.observeAll().map { list ->
        // today 每次订阅取一次即可：跨过零点最多是天数晚一步刷新，
        // 为此挂一个每分钟发射的 ticker 会让整页反复重组，代价远大于收益
        computeState(list.map { it.toDomain() }, AppTime.today())
    }

    /**
     * 记录一次开始。
     * 同一天已有记录就什么都不做——重复点击不该产生两条重叠的记录。
     */
    suspend fun recordStart(date: LocalDate): Unit = withContext(io) {
        if (dao.getByStartDate(date.toEpochDay()) != null) return@withContext
        val now = AppTime.now()
        dao.upsert(
            PeriodRecordEntity(
                startDate = date.toEpochDay(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        // TODO 重排下一次预计开始日的提醒闹钟（ReminderScheduler）
    }

    /**
     * 记录一次结束：落在离 [date] 最近、且尚未结束的那条记录上。
     * 找不到合适的记录就静默跳过，不新建——凭空造一条只有结束日的记录没有意义。
     */
    suspend fun recordEnd(date: LocalDate): Unit = withContext(io) {
        val target = dao.getRecent(CYCLE_SAMPLE_SIZE)
            .map { it.toDomain() }
            .firstOrNull { !it.startDate.isAfter(date) && (it.endDate == null || it.endDate.isBefore(date)) }
            ?: return@withContext
        dao.setEndDate(id = target.id, endDate = date.toEpochDay(), now = AppTime.now())
    }

    /** 直接改一条记录的起止与备注，供日历里长按修改用。 */
    suspend fun update(id: Long, startDate: LocalDate, endDate: LocalDate?, note: String?): Unit =
        withContext(io) {
            val existing = dao.getById(id) ?: return@withContext
            dao.update(
                existing.copy(
                    startDate = startDate.toEpochDay(),
                    endDate = endDate?.toEpochDay(),
                    note = note?.trim()?.ifBlank { null },
                    updatedAt = AppTime.now(),
                ),
            )
        }

    suspend fun delete(id: Long): Unit = withContext(io) {
        dao.softDelete(id, AppTime.now())
    }

    suspend fun restore(id: Long): Unit = withContext(io) {
        dao.restore(id, AppTime.now())
    }
}

private fun PeriodRecordEntity.toDomain(): PeriodRecord = PeriodRecord(
    id = id,
    startDate = LocalDate.ofEpochDay(startDate),
    endDate = endDate?.let { LocalDate.ofEpochDay(it) },
    note = note,
)

/**
 * 统计与预测。
 *
 * 算法刻意保持简单可靠（PRD 3.2 V1）：`下次开始 = 最近一次开始 + round(近 6 次周期均值)`。
 * 不做加权、不做异常值剔除——样本量本来就只有个位数，复杂模型只会放大噪声，
 * 却让用户以为结果更准。
 *
 * 抽成顶层函数是为了能脱离 Room 单测。
 */
internal fun computeState(records: List<PeriodRecord>, today: LocalDate): PeriodState {
    if (records.isEmpty()) return PeriodState.Empty

    // records 已按开始日倒序
    val starts = records.map { it.startDate }
    val cycles = starts.zipWithNext { newer, older -> ChronoUnit.DAYS.between(older, newer) }
        .filter { it in 10..90 } // 剔除明显不可能的间隔（补录历史时容易录错年份）
        .take(CYCLE_SAMPLE_SIZE)

    val avgCycle = if (cycles.isEmpty()) {
        DEFAULT_CYCLE_DAYS
    } else {
        cycles.average().roundToInt()
    }

    val durations = records.mapNotNull { it.durationDays }.take(CYCLE_SAMPLE_SIZE)
    val avgDuration = durations.takeIf { it.isNotEmpty() }?.average()?.roundToInt()

    // 比 PRD 的下限（<2 次样本）更严一档：只有 1 个间隔时「平均」等于那一次本身，
    // 波动会被原样当成预测，还是该提示用户别当真
    val reliable = cycles.size >= 3

    val latest = records.first()
    val predictedStart = latest.startDate.plusDays(avgCycle.toLong())
    val predictedRange = predictedStart..predictedStart.plusDays(
        ((avgDuration ?: DEFAULT_DURATION_DAYS) - 1).toLong().coerceAtLeast(0),
    )

    val ongoingDay = ChronoUnit.DAYS.between(latest.startDate, today)
    val status = when {
        latest.isOngoing && ongoingDay in 0 until MAX_ONGOING_DAYS ->
            PeriodStatus.Ongoing(day = (ongoingDay + 1).toInt(), recordId = latest.id)

        else -> PeriodStatus.Waiting(
            daysUntil = ChronoUnit.DAYS.between(today, predictedStart),
            predictedStart = predictedStart,
        )
    }

    return PeriodState(
        records = records,
        avgCycleDays = avgCycle,
        avgDurationDays = avgDuration,
        cycleSamples = cycles.size,
        reliable = reliable,
        predictedStart = predictedStart,
        predictedRange = predictedRange,
        status = status,
    )
}
