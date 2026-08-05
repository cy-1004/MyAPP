package com.myapp.core.ui.navigation

import kotlinx.serialization.Serializable

/**
 * 导航契约（PRD 4.7.3）。
 *
 * 所有路由集中定义在这里，feature 模块只实现自己那部分导航图。
 * 这样 feature A 想跳转到 feature B 的页面时，只依赖这个契约，
 * **不需要依赖 feature B 本身**——这是 feature 之间零依赖的关键。
 *
 * 用 kotlinx.serialization 的类型安全导航，避免字符串拼路由。
 */
sealed interface Route {

    @Serializable
    data object Home : Route

    // ---- 待办 ----
    @Serializable
    data object TodoList : Route

    @Serializable
    data class TodoDetail(val id: Long) : Route

    // ---- 以下随对应 feature 落地时启用 ----
    @Serializable
    data object NoteList : Route

    @Serializable
    data class NoteDetail(val id: Long) : Route

    @Serializable
    data object QuestionList : Route

    @Serializable
    data object PeriodCalendar : Route

    @Serializable
    data object AnniversaryList : Route

    @Serializable
    data object Ledger : Route

    @Serializable
    data object Budget : Route

    @Serializable
    data object Feed : Route

    @Serializable
    data object KnowledgeSources : Route

    @Serializable
    data object Settings : Route
}

/** 底部导航的一级入口。 */
enum class TopLevelDestination(val route: Route, val label: String) {
    HOME(Route.Home, "首页"),
    RECORDS(Route.NoteList, "记录"),
    LEDGER(Route.Ledger, "记账"),
    FEED(Route.Feed, "资讯"),
    SETTINGS(Route.Settings, "我的"),
}
