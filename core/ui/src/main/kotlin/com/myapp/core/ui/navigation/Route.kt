package com.myapp.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
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

    /** id 为 0 表示新建。新建与编辑共用一个页面，不值得为此多一条路由。 */
    @Serializable
    data class TodoDetail(val id: Long = 0L) : Route

    // ---- 纪念日 ----
    @Serializable
    data object AnniversaryList : Route

    /** id 为 0 表示新建，与待办同一套约定。 */
    @Serializable
    data class AnniversaryDetail(val id: Long = 0L) : Route

    // ---- 经期 ----
    @Serializable
    data object PeriodCalendar : Route

    // ---- 以下随对应 feature 落地时启用 ----
    @Serializable
    data object NoteList : Route

    /** id 为 0 表示新建，与待办同一套约定。 */
    @Serializable
    data class NoteDetail(val id: Long = 0L) : Route

    @Serializable
    data object QuestionList : Route

    /** id 为 0 表示新建，与待办/笔记同一套约定。 */
    @Serializable
    data class QuestionDetail(val id: Long = 0L) : Route

    @Serializable
    data object Ledger : Route

    /** id 为 0 表示新建，与待办/笔记同一套约定。 */
    @Serializable
    data class LedgerDetail(val id: Long = 0L) : Route

    /** 自动记账未识别队列（PRD 3.6.1 兜底）。 */
    @Serializable
    data object LedgerUnrecognized : Route

    /** 自动记账规则管理（PRD 3.6.1 Phase 3）。 */
    @Serializable
    data object RuleList : Route

    /**
     * 规则编辑页。
     * - [id] 为 0 表示新建，与待办/纪念日同一套约定。
     * - [presetUnrecognizedId] 非 0 表示从未识别队列跳来，保存成功后要把该未识别项也落账并出队。
     */
    @Serializable
    data class RuleDetail(val id: Long = 0L, val presetUnrecognizedId: Long = 0L) : Route

    /** 记账分类管理（PRD 3.6 M5 Phase 3）。 */
    @Serializable
    data object CategoryList : Route

    /** 分类编辑页。id 为 0 表示新建，与待办/规则同一套约定。 */
    @Serializable
    data class CategoryDetail(val id: Long = 0L) : Route

    @Serializable
    data object Budget : Route

    /** 记账统计：月度支出趋势 + 分类占比（PRD 3.6.3）。 */
    @Serializable
    data object Statistics : Route

    /**
     * 底部导航「知识」tab 的落地页：内部用顶部子 tab 分「知识库」（M6）/「资讯」（M8 RSS）。
     * 具体子 tab 切换是 :app 层的进程内状态，不走 NavController（避免子 tab 切换污染返回栈）。
     */
    @Serializable
    data object Feed : Route

    /**
     * RSS 文章列表（PRD 3.9）：全部/按分组/未读/已收藏筛选。「资讯」子 tab 的落地内容，
     * 也是首页卡片「查看更多」的跳转目标。
     */
    @Serializable
    data object RssArticles : Route

    /** RSS 订阅源管理列表（PRD 3.9）：增删改分组排序启停，从 [RssArticles] 顶栏入口进。 */
    @Serializable
    data object RssSources : Route

    /** RSS 订阅源编辑页。id 为 0 表示新建，与知识源编辑同一套约定。 */
    @Serializable
    data class RssSourceDetail(val id: Long = 0L) : Route

    /** RSS 文章详情：优先展示正文，无正文用 Custom Tabs 打开原链接（PRD 3.9）。 */
    @Serializable
    data class RssArticleDetail(val articleId: Long) : Route

    /**
     * 飞书网页收藏列表（PRD 3.7）。
     *
     * 曾经是「知识库」子 tab 的主内容；改版后知识库以 md 面试题库为主，
     * 网页收藏降级为只读书签，从题库页顶栏入口进。
     */
    @Serializable
    data object KnowledgeSources : Route

    /**
     * 一道面试题的阅读页（PRD 3.7 改版）：markdown 渲染，图片从 assets 读。
     * 首页「今日知识点」卡片和章节题目列表都跳这里。
     */
    @Serializable
    data class InterviewQuestion(val questionId: Long) : Route

    /** 某一章下的题目列表（PRD 3.7 改版）。 */
    @Serializable
    data class InterviewChapter(val chapterId: Long) : Route

    /**
     * 知识源编辑页。id 为 0 表示新建，与待办/纪念日/分类同一套约定。
     * sharedUrl：系统分享菜单「分享到 MyAPP」带过来的链接，仅新建时用于预填 URL 输入框。
     */
    @Serializable
    data class KnowledgeSourceDetail(val id: Long = 0L, val sharedUrl: String? = null) : Route

    /** 知识源阅读页：内嵌 WebView + 自定义工具栏（PRD 3.7）。 */
    @Serializable
    data class KnowledgeReader(val sourceId: Long) : Route

    /** 正文提取选择器设置（PRD 3.7）：飞书改版时不用等发版就能调整。 */
    @Serializable
    data object KnowledgeExtractSettings : Route

    @Serializable
    data object Settings : Route

    /** 保活自检（PRD 9.3）。首启强制走一遍，后续从设置页进入。 */
    @Serializable
    data object KeepAliveCheck : Route

    /** 首页卡片排序（PRD 3.11 / 4.7.2）：拖不动就用上下箭头，卡片量级小。 */
    @Serializable
    data object HomeCardOrder : Route

    /** 经期提醒提前天数设置（PRD 3.2）。 */
    @Serializable
    data object PeriodReminderSettings : Route

    /** 外观设置（PRD 3.12）：主题模式 / 动态取色 / 动效强度。 */
    @Serializable
    data object Appearance : Route

    /** 关于（PRD 3.12）：版本信息 + 开源许可。 */
    @Serializable
    data object About : Route

    /** 云备份（PRD 3.13）：登录云账号、每日自动备份、从历史备份恢复。 */
    @Serializable
    data object CloudBackup : Route
}

/** 底部导航的一级入口。 */
enum class TopLevelDestination(
    val route: Route,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(Route.Home, "首页", Icons.Filled.Home, Icons.Outlined.Home),
    RECORDS(Route.NoteList, "记录", Icons.AutoMirrored.Filled.Article, Icons.AutoMirrored.Outlined.Article),
    LEDGER(Route.Ledger, "记账", Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    // 「资讯」tab（PRD 3.11 底部导航 5 栏结构不变）：M8 落地后内部用顶部子 tab
    // 分「知识库」（M6）/「资讯」（M8 RSS），落地页见 Route.Feed。
    KNOWLEDGE(
        Route.Feed,
        "知识",
        Icons.AutoMirrored.Filled.MenuBook,
        Icons.AutoMirrored.Outlined.MenuBook,
    ),
    SETTINGS(Route.Settings, "我的", Icons.Filled.Settings, Icons.Outlined.Settings),
}
