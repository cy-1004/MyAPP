package com.myapp.feature.anniversary.ui

import com.myapp.core.common.time.AppFormatters
import com.myapp.core.common.time.AppTime
import com.myapp.feature.anniversary.data.Anniversary

/**
 * 倒数的主数字与单位分开返回：数字要用等宽字体大号显示，单位小号跟在后面，
 * 拼成一整个字符串就没法分别排版了。
 */
data class Countdown(val prefix: String?, val number: String, val unit: String)

fun Anniversary.countdown(): Countdown = when {
    // 累计型的主角是「第几天」，倒数里程碑退到副标题
    isCumulative -> Countdown(prefix = "第", number = dayNumber.toString(), unit = "天")
    daysUntil == null -> Countdown(prefix = "已过", number = elapsedDays.toString(), unit = "天")
    daysUntil == 0L -> Countdown(prefix = null, number = "今天", unit = "")
    daysUntil == 1L -> Countdown(prefix = null, number = "明天", unit = "")
    else -> Countdown(prefix = "还有", number = daysUntil.toString(), unit = "天")
}

/** 副标题：原始日期（农历标出农历月日）+ 下一次是哪天。 */
fun Anniversary.subtitle(): String {
    val origin = buildString {
        append(date.format(AppFormatters.dateWithYear))
        lunarLabel?.let { append("（农历$it）") }
    }
    val milestoneText = milestone?.let { m ->
        "距 ${m.label}还有 ${AppTime.daysFromToday(m.date)} 天"
    }
    val nextText = when {
        isCumulative -> milestoneText
        daysUntil == null -> null
        daysUntil == 0L -> null
        else -> nextDate?.format(AppFormatters.date)
    }
    return listOfNotNull(origin, nextText).joinToString(" · ")
}

/** 首页卡片用的一行式描述。 */
fun Anniversary.oneLine(): String {
    val c = countdown()
    return listOfNotNull(c.prefix, c.number + c.unit).joinToString(" ")
}

/** 今天是不是这条纪念日——用于给卡片加一点强调。 */
fun Anniversary.happeningToday(): Boolean = daysUntil == 0L || (isCumulative && date == AppTime.today())
