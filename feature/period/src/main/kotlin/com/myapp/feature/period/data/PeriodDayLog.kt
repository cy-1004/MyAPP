package com.myapp.feature.period.data

import java.time.LocalDate

/**
 * 预置的身体情况标签（PRD 3.2「每日异常记录」）。
 *
 * [id] 落库、[label] 展示——**id 一旦发布就不能改**，改了等于把用户已经记下的历史清空。
 * 想改文案只动 [label]。
 *
 * V1 不做用户自定义标签：标签是「可统计」的那一半，开放自定义之后同一件事会被写成
 * 五种说法，统计立刻失去意义；写不进标签的情况有自由文本兜底。
 */
enum class DayLogTag(val id: String, val label: String, val group: DayLogTagGroup) {
    DISCHARGE_COLOR("discharge_color", "分泌物颜色异常", DayLogTagGroup.DISCHARGE),
    DISCHARGE_BLOOD("discharge_blood", "有血丝", DayLogTagGroup.DISCHARGE),
    DISCHARGE_AMOUNT("discharge_amount", "分泌物量多", DayLogTagGroup.DISCHARGE),
    DISCHARGE_ODOR("discharge_odor", "异味", DayLogTagGroup.DISCHARGE),

    CRAMPS("cramps", "痛经", DayLogTagGroup.BODY),
    BACK_PAIN("back_pain", "腰酸", DayLogTagGroup.BODY),
    BREAST_TENDERNESS("breast_tenderness", "乳房胀痛", DayLogTagGroup.BODY),
    HEADACHE("headache", "头痛", DayLogTagGroup.BODY),
    BLOATING("bloating", "腹胀", DayLogTagGroup.BODY),

    MOOD_LOW("mood_low", "情绪低落", DayLogTagGroup.MOOD),
    MOOD_IRRITABLE("mood_irritable", "易怒", DayLogTagGroup.MOOD),
    MOOD_ANXIOUS("mood_anxious", "焦虑", DayLogTagGroup.MOOD),

    INTERCOURSE("intercourse", "同房", DayLogTagGroup.OTHER),
    MEDICATION("medication", "用药", DayLogTagGroup.OTHER),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        /** 落库的字符串反解成标签。认不出的 id 直接丢弃（降级安装/将来删标签时不至于崩）。 */
        fun parse(raw: String): List<DayLogTag> =
            raw.split(",").mapNotNull { byId[it.trim()] }

        fun join(tags: Collection<DayLogTag>): String =
            entries.filter { it in tags }.joinToString(",") { it.id } // 按枚举顺序存，读回来顺序稳定
    }
}

enum class DayLogTagGroup(val label: String) {
    DISCHARGE("分泌物"),
    BODY("体感"),
    MOOD("情绪"),
    OTHER("其他"),
}

/**
 * 某一天的记录。
 *
 * [isEmpty] 为真的记录不该存在——保存时若标签与文本都空，等价于删除这一天的记录。
 */
data class PeriodDayLog(
    val date: LocalDate,
    val tags: List<DayLogTag> = emptyList(),
    val note: String = "",
) {
    val isEmpty: Boolean get() = tags.isEmpty() && note.isBlank()
}
