package com.myapp.feature.anniversary.data

import com.myapp.core.common.contract.ReminderRequest
import com.myapp.core.common.contract.ReminderScheduler
import com.myapp.core.common.contract.ReminderSource
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.LunarCalendar
import com.myapp.core.database.dao.AnniversaryDao
import com.myapp.core.database.model.AnniversaryEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 重复类型。存字符串而非枚举，后续加新类型不用做数据库迁移。
 */
object AnniversaryRepeat {
    const val ONCE = "ONCE"
    const val YEARLY = "YEARLY"
    const val CUMULATIVE = "CUMULATIVE"

    val all = listOf(YEARLY, CUMULATIVE, ONCE)

    fun label(value: String): String = when (value) {
        CUMULATIVE -> "累计天数"
        ONCE -> "只有一次"
        else -> "每年"
    }

    fun hint(value: String): String = when (value) {
        CUMULATIVE -> "显示「第 X 天」，并倒数下一个整百天或周年"
        ONCE -> "过完就不再倒数，只显示已过去多久"
        else -> "每年重复，倒数到下一次"
    }
}

/** 累计型纪念日的下一个里程碑。 */
data class Milestone(val date: LocalDate, val label: String)

/** 领域模型。所有「距今多少天」都在这里算好，UI 不再做日期运算。 */
data class Anniversary(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val isLunar: Boolean,
    val repeatType: String,
    val remindDaysBefore: Int,
    val note: String?,
    val pinned: Boolean,
    /** 创建时间。「最早创建的那条」是小组件的保底选择，见 [AnniversaryRepository.observeHighlighted]。 */
    val createdAt: Long,
    /** 下一次发生的公历日期；一次性且已过则为 null。 */
    val nextDate: LocalDate?,
    /** [nextDate] 距今天数，0 表示就是今天；[nextDate] 为空时为 null。 */
    val daysUntil: Long?,
    /** 距原始日期已过去的天数，负数表示还没到。 */
    val elapsedDays: Long,
    /** 仅累计型有值。 */
    val milestone: Milestone?,
) {
    val isCumulative: Boolean get() = repeatType == AnniversaryRepeat.CUMULATIVE

    /** 累计型的「第 X 天」——当天算第 1 天，符合中文习惯。 */
    val dayNumber: Long get() = elapsedDays + 1

    val isToday: Boolean get() = daysUntil == 0L

    /** 农历纪念日的农历月日，如「闰四月初八」；公历的返回 null。 */
    val lunarLabel: String?
        get() = if (isLunar && LunarCalendar.isSupported(date)) {
            LunarCalendar.fromSolar(date).format()
        } else {
            null
        }
}

/** 编辑页草稿。 */
data class AnniversaryDraft(
    val id: Long = 0L,
    val title: String = "",
    val date: LocalDate = AppTime.today(),
    val isLunar: Boolean = false,
    val repeatType: String = AnniversaryRepeat.YEARLY,
    val remindDaysBefore: Int = 1,
    val note: String = "",
    val pinned: Boolean = false,
) {
    val isNew: Boolean get() = id == 0L
    val canSave: Boolean get() = title.isNotBlank()
}

/** 纪念日提醒的业务唯一键。 */
fun anniversaryReminderKey(id: Long): String = "anniversary:$id"

/** 提醒统一在当天上午 9 点触发，PRD 未定制到具体时刻，选一个不打扰睡眠的默认值。 */
private const val REMINDER_HOUR = 9

@Singleton
class AnniversaryRepository @Inject constructor(
    private val dao: AnniversaryDao,
    private val reminderScheduler: ReminderScheduler,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ReminderSource {

    /**
     * 全部纪念日，按「还有几天」升序。
     *
     * 排序在内存里做而不是 SQL：真正的排序键要经过农历换算与「每年重复」推算，
     * 写不成 SQL 表达式。纪念日是几十条的量级，代价可以忽略。
     */
    fun observeAll(): Flow<List<Anniversary>> = dao.observeAll().map { list ->
        val today = AppTime.today()
        list.map { it.toDomain(today) }.sortedWith(DisplayOrder)
    }

    /**
     * 首页卡片与桌面小组件 W4 用的「盯哪一个」。**优先级链（PRD 3.10 定稿）**：
     *
     *   1. 用户置顶的那条；
     *   2. 距今天最近的下一个（不分类型）；
     *   3. 都没有下一次了（比如只剩过完的一次性纪念日）→ 最早创建的那条；
     *   4. 一条都没有 → 返回空列表，由 UI 显示引导卡片。
     *
     * 之所以要第 3 条保底：只按「下一次」排会让一个全是历史纪念日的用户
     * 看到空白小组件，而他明明有数据——空白会被当成故障。
     */
    fun observeHighlighted(limit: Int = 2): Flow<List<Anniversary>> = observeAll().map { list ->
        if (list.isEmpty()) return@map emptyList()

        // observeAll 已按「还有几天」升序，且把没有下一次的沉到末尾
        val ordered = list.sortedWith(
            compareByDescending<Anniversary> { it.pinned }.then(DisplayOrder),
        )
        val hasUpcoming = ordered.any { it.daysUntil != null || it.pinned }
        if (hasUpcoming) {
            ordered.take(limit)
        } else {
            // 保底：最早创建的那条，至少让小组件有内容
            listOf(list.minByOrNull { it.createdAt } ?: list.first())
        }
    }

    suspend fun loadDraft(id: Long): AnniversaryDraft = withContext(io) {
        if (id == 0L) return@withContext AnniversaryDraft()
        val entity = dao.getById(id) ?: return@withContext AnniversaryDraft()
        AnniversaryDraft(
            id = entity.id,
            title = entity.title,
            date = LocalDate.ofEpochDay(entity.date),
            isLunar = entity.isLunar,
            repeatType = entity.repeatType,
            remindDaysBefore = entity.remindDaysBefore,
            note = entity.note.orEmpty(),
            pinned = entity.pinned,
        )
    }

    suspend fun save(draft: AnniversaryDraft): Long = withContext(io) {
        val now = AppTime.now()
        val id = if (draft.isNew) {
            dao.upsert(
                AnniversaryEntity(
                    title = draft.title.trim(),
                    date = draft.date.toEpochDay(),
                    isLunar = draft.isLunar,
                    repeatType = draft.repeatType,
                    remindDaysBefore = draft.remindDaysBefore,
                    note = draft.note.trim().ifBlank { null },
                    pinned = draft.pinned,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } else {
            // 读改写：uuid / createdAt 不属于表单，整体构造会把它们清掉
            val existing = dao.getById(draft.id) ?: return@withContext draft.id
            dao.update(
                existing.copy(
                    title = draft.title.trim(),
                    date = draft.date.toEpochDay(),
                    isLunar = draft.isLunar,
                    repeatType = draft.repeatType,
                    remindDaysBefore = draft.remindDaysBefore,
                    note = draft.note.trim().ifBlank { null },
                    pinned = draft.pinned,
                    updatedAt = now,
                ),
            )
            draft.id
        }
        // 置顶是单选，写完再统一收敛，避免出现两条置顶
        if (draft.pinned) {
            dao.clearPinned(now)
            dao.setPinned(id, now)
        }
        val saved = dao.getById(id)?.toDomain(AppTime.today())
        scheduleReminder(id, saved)
        id
    }

    suspend fun delete(id: Long): Unit = withContext(io) {
        dao.softDelete(id, AppTime.now())
        reminderScheduler.cancel(anniversaryReminderKey(id))
    }

    /** 撤销删除。软删除保留整行，恢复无损。 */
    suspend fun restore(id: Long): Unit = withContext(io) {
        dao.restore(id, AppTime.now())
    }

    suspend fun setPinned(id: Long): Unit = withContext(io) {
        val now = AppTime.now()
        dao.clearPinned(now)
        dao.setPinned(id, now)
    }

    /** 开机重建用：全部有效纪念日各自算一次下一次触发时间。 */
    override suspend fun pendingReminders(): List<ReminderRequest> = withContext(io) {
        val today = AppTime.today()
        dao.getAllActive().mapNotNull { entity -> reminderRequest(entity.toDomain(today)) }
    }

    /** 没有下一次（比如已过完的一次性纪念日）就取消旧闹钟而不是留着一个不会响的注册。 */
    private fun scheduleReminder(id: Long, anniversary: Anniversary?) {
        val request = anniversary?.let { reminderRequest(it) }
        if (request != null) {
            reminderScheduler.schedule(request.key, request.triggerAtMillis, request.title, request.body)
        } else {
            reminderScheduler.cancel(anniversaryReminderKey(id))
        }
    }

    private fun reminderRequest(anniversary: Anniversary): ReminderRequest? {
        val next = anniversary.nextDate ?: return null
        val triggerDate = next.minusDays(anniversary.remindDaysBefore.toLong())
        val triggerAt = with(AppTime) { triggerDate.toEpochMilliAtTime(REMINDER_HOUR) }
        return ReminderRequest(
            key = anniversaryReminderKey(anniversary.id),
            triggerAtMillis = triggerAt,
            title = anniversary.title,
            body = if (anniversary.remindDaysBefore > 0) "还有 ${anniversary.remindDaysBefore} 天" else "就是今天",
        )
    }
}

/**
 * 排序：今天的排最前，然后按剩余天数升序；
 * 已经过完的一次性纪念日沉到最后，按刚过去的在前。
 */
private object DisplayOrder : Comparator<Anniversary> {
    override fun compare(a: Anniversary, b: Anniversary): Int {
        val da = a.daysUntil
        val db = b.daysUntil
        return when {
            da != null && db != null -> da.compareTo(db)
            da != null -> -1
            db != null -> 1
            else -> a.elapsedDays.compareTo(b.elapsedDays)
        }
    }
}

private fun AnniversaryEntity.toDomain(today: LocalDate): Anniversary {
    val origin = LocalDate.ofEpochDay(date)
    val elapsed = ChronoUnit.DAYS.between(origin, today)
    val milestone = if (repeatType == AnniversaryRepeat.CUMULATIVE) {
        nextMilestone(origin, today, elapsed)
    } else {
        null
    }
    val next = when (repeatType) {
        AnniversaryRepeat.CUMULATIVE -> milestone?.date
        AnniversaryRepeat.ONCE -> origin.takeIf { !it.isBefore(today) }
        else -> nextYearlyDate(origin, today, isLunar)
    }

    return Anniversary(
        id = id,
        title = title,
        date = origin,
        isLunar = isLunar,
        repeatType = repeatType,
        remindDaysBefore = remindDaysBefore,
        note = note,
        pinned = pinned,
        createdAt = createdAt,
        nextDate = next,
        daysUntil = next?.let { ChronoUnit.DAYS.between(today, it) },
        elapsedDays = elapsed,
        milestone = milestone,
    )
}

/**
 * 每年重复的下一次公历日期。
 *
 * 农历要走换算：农历生日对应的公历日期每年都不同，直接把年份换掉是错的。
 * 换算失败（超出 1900~2100）时退回公历同月日，保证 UI 永远有个合理结果。
 */
private fun nextYearlyDate(origin: LocalDate, today: LocalDate, isLunar: Boolean): LocalDate {
    if (isLunar && LunarCalendar.isSupported(origin)) {
        val lunar = LunarCalendar.fromSolar(origin)
        LunarCalendar.nextSolarOccurrence(
            month = lunar.month,
            day = lunar.day,
            isLeapMonth = lunar.isLeapMonth,
            from = today,
        )?.let { return it }
    }
    // withYear 会把 2 月 29 日自动收敛到 2 月 28 日，正是想要的行为
    val thisYear = origin.withYear(today.year)
    return if (thisYear.isBefore(today)) origin.withYear(today.year + 1) else thisYear
}

/**
 * 累计型的下一个里程碑：**整百天与周年取更近的那个**。
 *
 * 只倒数整百天会漏掉「三周年」这种更有意义的节点，
 * 只倒数周年又会错过「在一起 1000 天」，两者都要。
 */
private fun nextMilestone(origin: LocalDate, today: LocalDate, elapsed: Long): Milestone {
    // 当天算第 1 天，所以第 N 天对应 origin.plusDays(N - 1)
    val currentDayNumber = elapsed + 1
    val nextHundred = (currentDayNumber / 100 + 1) * 100
    val hundredDate = origin.plusDays(nextHundred - 1)

    val passedYears = ChronoUnit.YEARS.between(origin, today)
    val nextYears = passedYears + 1
    val yearDate = origin.plusYears(nextYears)

    return if (!hundredDate.isAfter(yearDate)) {
        Milestone(hundredDate, "$nextHundred 天")
    } else {
        Milestone(yearDate, "$nextYears 周年")
    }
}
