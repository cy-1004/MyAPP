package com.myapp.feature.ledger.notification

import com.myapp.feature.ledger.data.LedgerPrefsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull

/**
 * 自动分类（PRD 3.6.1）：商户名 → 分类名。
 *
 * 优先级：用户学习过的映射（改过一次分类就记住）> 内置关键词表 > null（落「未分类」）。
 * 返回的是分类**名**而非 id——recordExpense 契约按名查 id，外部不需要知道内部 id。
 */
@Singleton
class AutoCategorizer @Inject constructor(
    private val prefs: LedgerPrefsStore,
) {

    suspend fun categorize(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null
        // 学习映射优先：用户手动改过的分类是最高置信度
        prefs.merchantCategoryMap.firstOrNull()?.get(merchant)?.let { return it }
        return BUILTIN_KEYWORDS.entries.firstOrNull { (keyword, _) -> merchant.contains(keyword) }?.value
    }

    /** 用户改过分类后记住：同商户下次自动记账直接命中。 */
    suspend fun learn(merchant: String, categoryName: String) {
        prefs.learnMerchantCategory(merchant, categoryName)
    }

    /**
     * 内置商户关键词 → 分类。分类名必须与 assets/categories.json 的种子一致，
     * 否则 recordExpense 会查不到而落「未分类」。关键词按包含匹配，短的放后面
     * 防止「商店」之类泛词抢先命中。
     */
    private val BUILTIN_KEYWORDS: Map<String, String> = linkedMapOf(
        "星巴克" to "餐饮", "瑞幸" to "餐饮", "喜茶" to "餐饮", "奈雪" to "餐饮",
        "蜜雪" to "餐饮", "霸王茶姬" to "餐饮", "肯德基" to "餐饮", "麦当劳" to "餐饮",
        "汉堡王" to "餐饮", "必胜客" to "餐饮", "美团" to "餐饮", "饿了么" to "餐饮",
        "外卖" to "餐饮", "餐厅" to "餐饮", "咖啡" to "餐饮", "奶茶" to "餐饮",
        "滴滴" to "交通", "高德" to "交通", "曹操" to "交通", "首汽" to "交通",
        "地铁" to "交通", "公交" to "交通", "铁路" to "交通", "加油" to "交通",
        "停车" to "交通", "打车" to "交通", "共享单车" to "交通",
        "淘宝" to "购物", "天猫" to "购物", "京东" to "购物", "拼多多" to "购物",
        "唯品会" to "购物", "抖音" to "购物", "商城" to "购物", "超市" to "购物",
        "便利店" to "购物",
        "猫眼" to "娱乐", "淘票票" to "娱乐", "影城" to "娱乐", "万达" to "娱乐",
        "腾讯视频" to "娱乐", "爱奇艺" to "娱乐", "优酷" to "娱乐", "音乐" to "娱乐",
        "游戏" to "娱乐", "KTV" to "娱乐",
        "医院" to "医疗", "诊所" to "医疗", "药" to "医疗", "体检" to "医疗",
        "口腔" to "医疗", "眼科" to "医疗", "牙科" to "医疗",
        "房租" to "居住", "水电" to "居住", "燃气" to "居住", "物业" to "居住",
        "话费" to "居住", "宽带" to "居住",
        "红包" to "人情", "转账" to "人情", "礼金" to "人情", "礼物" to "人情",
        "书店" to "学习", "当当" to "学习", "课程" to "学习", "培训" to "学习",
        "知识" to "学习",
    )
}
