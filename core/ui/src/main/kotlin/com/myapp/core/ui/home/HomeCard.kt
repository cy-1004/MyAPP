package com.myapp.core.ui.home

import androidx.compose.runtime.Composable
import com.myapp.core.ui.navigation.Route
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 首页卡片插槽协议——**整个项目可扩展性的核心**（PRD 4.7.2）。
 *
 * :feature:home 不认识任何具体业务，它只消费一个 `Set<HomeCard>`。
 * 每个 feature 通过 Hilt 的 `@IntoSet` 把自己的卡片注入进来。
 *
 * 因此「新增一个首页卡片」的完整步骤是：
 *   1. 在自己的 feature 模块里实现 HomeCard
 *   2. 用 @Binds @IntoSet 绑定
 * 首页代码一行都不用改，也不需要 import 你的模块。
 *
 * 契约要求：
 *   - [Content] 必须自己处理 Loading / Error / Empty 三种状态，
 *     不能把异常抛给首页——单卡片故障不该拖垮整页。
 *   - [Content] 内部自行 hiltViewModel() 获取自己的 ViewModel，
 *     各卡片的数据流互相独立，慢的不阻塞快的。
 */
interface HomeCard {

    /** 唯一标识，用于持久化用户的排序与显隐配置。**一旦发布不要再改**。 */
    val id: String

    /** 默认顺序，数字越小越靠前。用户可在设置里覆盖。 */
    val defaultOrder: Int

    /** 卡片是否显示。可接功能开关或「无数据时自动隐藏」逻辑。 */
    fun isEnabled(): Flow<Boolean> = flowOf(true)

    @Composable
    fun Content(onNavigate: (Route) -> Unit)
}

/** 便于 feature 实现时少写样板。 */
abstract class BaseHomeCard(
    override val id: String,
    override val defaultOrder: Int,
) : HomeCard

/**
 * 首页卡片的默认顺序基准（PRD 3.11）。
 * 集中放在这里，避免各 feature 各自拍脑袋定数字导致顺序混乱。
 */
object HomeCardOrder {
    const val GREETING = 100      // 日期问候 + 最近纪念日
    const val TODO = 200          // 今日待办
    const val PERIOD = 300        // 经期状态
    const val KNOWLEDGE = 400     // 今日知识点
    const val LEDGER = 500        // 今日支出 + 预算进度
    const val FEED = 600          // 行业动态
    const val QUESTION = 700      // 一条待解决疑问
}
