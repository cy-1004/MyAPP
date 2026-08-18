package com.myapp.feature.period.data

import com.myapp.core.common.contract.PeriodReminderRefresher
import com.myapp.core.common.contract.ReminderRequest
import com.myapp.core.common.contract.ReminderScheduler
import com.myapp.core.common.contract.ReminderSource
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.PeriodDao
import com.myapp.core.database.model.PeriodRecordEntity
import com.myapp.core.datastore.AppPreferences
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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

/**
 * 只有一个「下一次预计开始」在追踪，用固定 key 而不是按记录 id——
 * 每次新记录都会覆盖上一次的预测提醒，语义上正是想要的效果。
 */
private const val PERIOD_REMINDER_KEY = "period:next"

/** 「预计开始」提醒在当天上午 9 点触发。 */
private const val REMINDER_HOUR = 9

/**
 * 经期中每日关怀提醒（PRD 3.2）只推前 3 天。
 *
 * 前几天通常最难受，也最需要人照顾；推满整个经期的话后几天往往已经缓过来了，
 * 提醒会显得多余，多余的提醒最后的下场就是被随手划掉、连带前几天的一起忽略。
 */
internal const val CARE_REMINDER_DAYS = 3

/**
 * 关怀提醒在傍晚 19:00 触发。
 *
 * 不跟「预计开始」提醒的 09:00 对齐是故意的：这条提醒的目的是让人**做点什么**
 * （带点东西回去、晚上多照顾一下），早上 9 点看到基本干不了什么。
 */
internal const val CARE_REMINDER_HOUR = 19

/** 关怀提醒按「第几天」分成独立的 key，才能各自注册/撤销。 */
private fun careReminderKey(day: Int) = "period:care:$day"

/** 关怀提醒的一次触发。[day] 从 1 开始。 */
internal data class CareReminder(val day: Int, val triggerAtMillis: Long)

/**
 * 这次经期还该发哪几条关怀提醒。纯函数，不碰 DAO/DataStore，方便单测。
 *
 * 三种情况会让某一天被跳过：
 * - 那天的 19:00 已经过了（比如晚上 11 点才补记开始日）--> 不排过去的时间点，
 *   `AlarmManager` 对过去的时间会立刻触发，补记一次就弹一串旧提醒
 * - 已经记了结束日、且那天在结束日之后 --> 她提前结束了，别再提醒
 * - 全都不满足 --> 返回空表，调用方据此把三条闹钟全撤掉
 */
internal fun careReminderPlan(
    startDate: LocalDate,
    endDate: LocalDate?,
    now: Long,
): List<CareReminder> = (1..CARE_REMINDER_DAYS).mapNotNull { day ->
    val date = startDate.plusDays((day - 1).toLong())
    if (endDate != null && date.isAfter(endDate)) return@mapNotNull null
    val triggerAt = with(AppTime) { date.toEpochMilliAtTime(CARE_REMINDER_HOUR) }
    if (triggerAt <= now) return@mapNotNull null
    CareReminder(day = day, triggerAtMillis = triggerAt)
}

/**
 * 第 N 天的提醒文案。
 *
 * **人称是第三人称，收件人是记录者本人**--这个模块是用户替对象记的，
 * 写成「你的经期开始了」就完全搞反了对象。改文案前先看交接文档里这条约定。
 * 每天不重样：三天推一模一样的字，第二天起就自动忽略了。
 */
private fun careReminderBody(day: Int): String = when (day) {
    1 -> "开始了。倒杯热水，少安排冷的吃的。"
    2 -> "今天往往最难受，别安排太累的事。"
    else -> "还没缓过来，问一句今天怎么样。"
}

@Singleton
class PeriodRepository @Inject constructor(
    private val dao: PeriodDao,
    private val reminderScheduler: ReminderScheduler,
    private val appPreferences: AppPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ReminderSource, PeriodReminderRefresher {

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
        rescheduleNextReminder()
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
        // 只重排关怀提醒：结束日不参与「下一次预计开始」的推算（那个只看各次的起始日），
        // 但提前结束意味着后面几天的关怀提醒该撤掉
        rescheduleCareReminders()
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
            // 改的可能是最近一条记录的起始日，预测会跟着变
            rescheduleNextReminder()
        }

    suspend fun delete(id: Long): Unit = withContext(io) {
        dao.softDelete(id, AppTime.now())
        rescheduleNextReminder()
    }

    suspend fun restore(id: Long): Unit = withContext(io) {
        dao.restore(id, AppTime.now())
        rescheduleNextReminder()
    }

    /** 开机重建用：一条「下一次预计开始」+ 本次经期还没到点的关怀提醒。 */
    override suspend fun pendingReminders(): List<ReminderRequest> = withContext(io) {
        listOfNotNull(nextReminderRequest()) + careReminderRequests()
    }

    /** 实现 [PeriodReminderRefresher]：设置页改完提醒相关设置后调，让新设置立刻生效。 */
    override suspend fun refresh(): Unit = withContext(io) {
        rescheduleNextReminder()
    }

    /** 用最近的记录重算预测，覆盖式重排提醒；没有足够数据时取消旧的。 */
    private suspend fun rescheduleNextReminder() {
        val request = nextReminderRequest()
        if (request != null) {
            reminderScheduler.schedule(request.key, request.triggerAtMillis, request.title, request.body)
        } else {
            reminderScheduler.cancel(PERIOD_REMINDER_KEY)
        }
        rescheduleCareReminders()
    }

    /**
     * 重排关怀提醒：**先把三条全撤掉，再按需重排**。
     *
     * 逐条比对差异不如整体覆盖简单可靠--记录被删、起始日改动、提前记了结束日、
     * 开关被关掉，每一种都会让旧的那几条失效，且失效的是哪几条各不相同。
     * 就三条闹钟，全撤全排的代价可以忽略。
     */
    private suspend fun rescheduleCareReminders() {
        (1..CARE_REMINDER_DAYS).forEach { reminderScheduler.cancel(careReminderKey(it)) }
        careReminderRequests().forEach {
            reminderScheduler.schedule(it.key, it.triggerAtMillis, it.title, it.body)
        }
    }

    /**
     * 本次经期还没到点的关怀提醒。
     *
     * 只看最近一条记录：更早的记录要么已经过去、要么被这条覆盖，都不该再提醒。
     * 哪几天该发交给纯函数 [careReminderPlan]，这里只负责读开关、读记录、套文案。
     */
    private suspend fun careReminderRequests(): List<ReminderRequest> {
        if (!appPreferences.periodCareReminderEnabled.first()) return emptyList()
        val latest = dao.getLatest()?.toDomain() ?: return emptyList()
        return careReminderPlan(latest.startDate, latest.endDate, AppTime.now()).map {
            ReminderRequest(
                key = careReminderKey(it.day),
                triggerAtMillis = it.triggerAtMillis,
                title = "她的经期·第 ${it.day} 天",
                body = careReminderBody(it.day),
            )
        }
    }

    private suspend fun nextReminderRequest(): ReminderRequest? {
        // +1 条才能算出 CYCLE_SAMPLE_SIZE 个间隔（PeriodDao.getRecent 的约定）
        val records = dao.getRecent(CYCLE_SAMPLE_SIZE + 1).map { it.toDomain() }
        if (records.isEmpty()) return null
        val predictedStart = computeState(records, AppTime.today()).predictedStart ?: return null
        val leadDays = appPreferences.periodReminderLeadDays.first()
        val triggerDate = predictedStart.minusDays(leadDays.toLong())
        val triggerAt = with(AppTime) { triggerDate.toEpochMilliAtTime(REMINDER_HOUR) }
        return ReminderRequest(
            key = PERIOD_REMINDER_KEY,
            triggerAtMillis = triggerAt,
            title = "经期提醒",
            body = "预计 $leadDays 天后开始",
        )
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
