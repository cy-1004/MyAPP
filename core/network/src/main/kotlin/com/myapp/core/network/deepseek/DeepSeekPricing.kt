package com.myapp.core.network.deepseek

import java.time.Instant
import java.time.ZoneOffset

/**
 * DeepSeek 的峰谷计价时段（PRD 3.14）。
 *
 * 时段边界抄自官方定价页（2026-08-14 抄录，2026-08-16 16:00 UTC 起生效的那版）：
 * **峰价 01:00–04:00 与 06:00–10:00 UTC**，其余时间为谷价，峰价是谷价的两倍。
 * 换算到北京时间（UTC+8）是 09:00–12:00 与 14:00–18:00——正好覆盖大半个工作日，
 * 这也是 PRD 里「峰价只置灰、不硬禁用」的由来：硬禁用等于白天完全不能用。
 *
 * 边界口径是**左闭右开**：01:00:00 已经是峰价，04:00:00 已经回到谷价。
 * 官方页面写的是「01:00 - 04:00」这种人类区间，落到代码必须挑一种解释并钉死，
 * 否则整点前后一秒的判定会随手写实现漂移。选左闭右开是因为它跟「小时」这个单位对齐：
 * 属于第 1、2、3 个小时算峰价，第 4 个小时不算。
 *
 * 这里只做时段判断，不做金额估算——App 不显示预估费用（拿不到实时价目表，
 * 显示一个可能过期的数字比不显示更糟）。
 */
object DeepSeekPricing {

    /** 峰价时段，UTC 小时的左闭右开区间。官方调整时段时只改这一处。 */
    private val PEAK_WINDOWS_UTC = listOf(1 to 4, 6 to 10)

    /** [instant] 是否落在峰价时段。 */
    fun isPeak(instant: Instant): Boolean {
        val hour = instant.atZone(ZoneOffset.UTC).hour
        return PEAK_WINDOWS_UTC.any { (start, end) -> hour >= start && hour < end }
    }

    /**
     * 从 [instant] 起下一次进入谷价的时刻；本来就在谷价时段则原样返回 [instant]。
     *
     * 给 UI 拼「X 点之后转谷价」用——只说「现在是峰价」不给出口，
     * 用户唯一的选择就是强发，那这个提示就只是在制造焦虑。
     */
    fun nextOffPeakStart(instant: Instant): Instant {
        if (!isPeak(instant)) return instant
        val utc = instant.atZone(ZoneOffset.UTC)
        val end = PEAK_WINDOWS_UTC.first { (start, e) -> utc.hour >= start && utc.hour < e }.second
        // 峰价窗口不跨天（最晚到 10:00），truncate 到整点后直接加小时差即可
        return utc.withMinute(0).withSecond(0).withNano(0)
            .plusHours((end - utc.hour).toLong())
            .toInstant()
    }
}
