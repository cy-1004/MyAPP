package com.myapp.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

/**
 * 全局偏好设置。
 *
 * 命名空间约定（PRD 4.7.6）：key 一律用 `<feature>.<name>` 格式，
 * 各 feature 自管自己的命名空间，避免不同模块的 key 撞车。
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.dataStore

    // ---- 外观 ----
    val themeMode: Flow<String> = read(Keys.THEME_MODE, "system")   // system / light / dark
    val motionLevel: Flow<String> = read(Keys.MOTION_LEVEL, "full") // full / reduced / none

    /** Material You 动态取色，默认关闭（PRD 5 章：默认保持品牌感，是刻意的设计决定）。 */
    val dynamicColorEnabled: Flow<Boolean> = store.data.map {
        it[Keys.DYNAMIC_COLOR_ENABLED] ?: false
    }

    // ---- 首页卡片配置：id -> 顺序/显隐，JSON 存储 ----
    val homeCardConfig: Flow<String> = read(Keys.HOME_CARD_CONFIG, "")

    /** 经期预计开始日提前几天提醒（PRD 3.2 默认 2 天）。 */
    val periodReminderLeadDays: Flow<Int> =
        store.data.map { it[Keys.PERIOD_REMINDER_LEAD_DAYS] ?: 2 }

    suspend fun setThemeMode(value: String) = write(Keys.THEME_MODE, value)
    suspend fun setMotionLevel(value: String) = write(Keys.MOTION_LEVEL, value)
    suspend fun setHomeCardConfig(json: String) = write(Keys.HOME_CARD_CONFIG, json)

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        store.edit { it[Keys.DYNAMIC_COLOR_ENABLED] = enabled }
    }

    suspend fun setPeriodReminderLeadDays(days: Int) {
        store.edit { it[Keys.PERIOD_REMINDER_LEAD_DAYS] = days }
    }

    // ---- 云备份（PRD 3.13）----
    /** 是否开启每日自动云备份。默认关闭：没登录云账号时它本来也跑不起来。 */
    val cloudBackupEnabled: Flow<Boolean> = store.data.map {
        it[Keys.CLOUD_BACKUP_ENABLED] ?: false
    }

    /** 上次备份成功的时刻（epochMilli）；0 = 从未成功备份过。 */
    val cloudBackupLastSuccessAt: Flow<Long> = store.data.map {
        it[Keys.CLOUD_BACKUP_LAST_SUCCESS_AT] ?: 0L
    }

    /**
     * 上次备份失败的原因，空串 = 上次是成功的。
     *
     * 必须持久化：后台任务失败时用户不在场，只有把错误留下来，
     * 下次打开设置页才能看到「备份其实已经连续失败了」，而不是以为一直在正常备份。
     */
    val cloudBackupLastError: Flow<String> = read(Keys.CLOUD_BACKUP_LAST_ERROR, "")

    suspend fun setCloudBackupEnabled(enabled: Boolean) {
        store.edit { it[Keys.CLOUD_BACKUP_ENABLED] = enabled }
    }

    suspend fun setCloudBackupLastSuccessAt(epochMillis: Long) {
        store.edit { it[Keys.CLOUD_BACKUP_LAST_SUCCESS_AT] = epochMillis }
    }

    suspend fun setCloudBackupLastError(message: String) =
        write(Keys.CLOUD_BACKUP_LAST_ERROR, message)

    // ---- 知识点每日推送（PRD 3.8）----
    /** 默认关闭：PRD 原文「可选每日定时通知推送」，用户需主动开启。固定 08:00，不做可配置时间。 */
    val knowledgeDailyPushEnabled: Flow<Boolean> = store.data.map {
        it[Keys.KNOWLEDGE_DAILY_PUSH_ENABLED] ?: false
    }

    suspend fun setKnowledgeDailyPushEnabled(enabled: Boolean) {
        store.edit { it[Keys.KNOWLEDGE_DAILY_PUSH_ENABLED] = enabled }
    }

    // ---- 经期中每日关怀提醒（PRD 3.2）----
    /**
     * 默认**开启**：这个功能是用户明确点名要的，装上就该工作，
     * 不像云备份/AI 那种「碰了隐私边界、必须先知情同意」的开关。
     * 频率也低（一个周期只推经期前 3 天、每天一条），不至于变成骚扰。
     *
     * 改这个开关必须走 `PeriodReminderRefresher`--只写 DataStore 撤不掉已注册的闹钟。
     */
    val periodCareReminderEnabled: Flow<Boolean> = store.data.map {
        it[Keys.PERIOD_CARE_REMINDER_ENABLED] ?: true
    }

    suspend fun setPeriodCareReminderEnabled(enabled: Boolean) {
        store.edit { it[Keys.PERIOD_CARE_REMINDER_ENABLED] = enabled }
    }

    // ---- 笔记通知栏常驻快捷入口（PRD 3.4）----
    /**
     * 默认关闭：这是一条**常驻**通知，占着通知栏一行，得用户主动要才给。
     *
     * 这个开关只是「用户意愿」的存档，真正的挂/撤由 `:app` 层接线
     * （MainActivity 收集这个 Flow、BootCompletedReceiver 开机读一次）——
     * `:feature:settings` 不能直接调 `:feature:note` 的通知器（feature 之间不许互相依赖），
     * 走一个共享的 DataStore 键比为一个开关新建跨模块契约划算。
     */
    val noteQuickEntryEnabled: Flow<Boolean> = store.data.map {
        it[Keys.NOTE_QUICK_ENTRY_ENABLED] ?: false
    }

    suspend fun setNoteQuickEntryEnabled(enabled: Boolean) {
        store.edit { it[Keys.NOTE_QUICK_ENTRY_ENABLED] = enabled }
    }

    // ---- md 面试题库（PRD 3.7）----
    /**
     * 已导入的题库资源版本。0 = 从没导入过。
     * 与代码里的 `INTERVIEW_ASSETS_VERSION` 比对，低了才重新解析 assets 里的 md。
     */
    val interviewAssetsVersion: Flow<Int> = store.data.map {
        it[Keys.INTERVIEW_ASSETS_VERSION] ?: 0
    }

    suspend fun setInterviewAssetsVersion(version: Int) {
        store.edit { it[Keys.INTERVIEW_ASSETS_VERSION] = version }
    }

    // ---- AI 分析（PRD 3.14）----

    /**
     * AI 总开关。**默认关闭，且这个 false 同时代表「还没同意把记录发出去」**——
     * 发送的内容包含用户手写的私密文字，必须有一次明确的知情同意才能开始发，
     * 所以开启动作固定绑一个说明弹窗，不允许在别处静默置 true。
     */
    val aiEnabled: Flow<Boolean> = store.data.map { it[Keys.AI_ENABLED] ?: false }

    /**
     * 是否启用联网搜索。默认开——它是用户提这个需求时明确要的能力；
     * 但联网会额外产生 token 费用且明显更慢，所以留了关掉省钱的口子。
     */
    val aiWebSearchEnabled: Flow<Boolean> = store.data.map { it[Keys.AI_WEB_SEARCH] ?: true }

    suspend fun setAiEnabled(enabled: Boolean) {
        store.edit { it[Keys.AI_ENABLED] = enabled }
    }

    suspend fun setAiWebSearchEnabled(enabled: Boolean) {
        store.edit { it[Keys.AI_WEB_SEARCH] = enabled }
    }

    /**
     * 上一次经期 AI 分析的正文、输入指纹与时刻。
     *
     * 缓存在这里而不是 Room：它是一段随时可以重新生成的派生文本，
     * 没有查询/关联需求，为它开一张表和一次迁移不划算。
     * 指纹用于「同一份数据不重复调用」——数据没变就直接给旧结果，
     * 既省钱也避免把同样的隐私内容反复发出去（PRD 3.14）。
     */
    val periodAiResult: Flow<String> = read(Keys.PERIOD_AI_RESULT, "")
    val periodAiFingerprint: Flow<String> = read(Keys.PERIOD_AI_FINGERPRINT, "")
    val periodAiUpdatedAt: Flow<Long> = store.data.map { it[Keys.PERIOD_AI_UPDATED_AT] ?: 0L }

    suspend fun savePeriodAiResult(text: String, fingerprint: String, updatedAt: Long) {
        store.edit {
            it[Keys.PERIOD_AI_RESULT] = text
            it[Keys.PERIOD_AI_FINGERPRINT] = fingerprint
            it[Keys.PERIOD_AI_UPDATED_AT] = updatedAt
        }
    }

    /** 关闭 AI 或清空 key 时一并抹掉缓存，别让关掉之后还留着一份分析结果。 */
    suspend fun clearPeriodAiResult() {
        store.edit {
            it.remove(Keys.PERIOD_AI_RESULT)
            it.remove(Keys.PERIOD_AI_FINGERPRINT)
            it.remove(Keys.PERIOD_AI_UPDATED_AT)
        }
    }

    /**
     * 功能开关（PRD 4.7.6）。
     * 每个 feature 可被整体关闭：关闭后卡片、导航入口、后台任务同时失效。
     * 便于试验性功能安全上线。
     */
    fun featureEnabled(featureId: String, default: Boolean = true): Flow<Boolean> =
        store.data.map { it[booleanPreferencesKey("feature.$featureId.enabled")] ?: default }

    suspend fun setFeatureEnabled(featureId: String, enabled: Boolean) {
        store.edit { it[booleanPreferencesKey("feature.$featureId.enabled")] = enabled }
    }

    // ---- Onboarding：保活自检（PRD 9.3）----
    /** 是否已完成保活自检向导。false = 首次安装未走过，需强制引导。 */
    val keepAliveChecked: Flow<Boolean> = store.data.map {
        it[booleanPreferencesKey("onboarding.keepAliveChecked")] ?: false
    }

    suspend fun setKeepAliveChecked(value: Boolean) {
        store.edit { it[booleanPreferencesKey("onboarding.keepAliveChecked")] = value }
    }

    /**
     * 保活自检中手动项的勾选状态（PRD 9.3）。
     *
     * 手动项代表用户在系统设置里做的物理操作（允许自启动/后台活动/锁任务），
     * 这些操作不会因为离开 App 就消失，所以勾选状态也要持久化--
     * 否则每次复查都得重新勾，且完成条件永远满足不了。
     */
    val keepAliveManualDone: Flow<Set<String>> = store.data.map {
        it[stringSetPreferencesKey("onboarding.keepAliveManualDone")] ?: emptySet()
    }

    suspend fun setKeepAliveManualDone(id: String, done: Boolean) {
        store.edit { prefs ->
            val key = stringSetPreferencesKey("onboarding.keepAliveManualDone")
            val current = prefs[key] ?: emptySet()
            prefs[key] = if (done) current + id else current - id
        }
    }

    // ---- 撒花去重（PRD 6.1）----
    /**
     * 「纪念日当天」撒花的去重记号：存最后一次撒花的日期（ISO，如 "2026-08-19"）。
     * 同一天打开纪念日列表多次不会重复撒--只在这个值真的落后于今天时才触发一次并更新。
     */
    val anniversaryConfettiLastDate: Flow<String> = read(Keys.ANNIVERSARY_CONFETTI_LAST_DATE, "")

    suspend fun setAnniversaryConfettiLastDate(isoDate: String) =
        write(Keys.ANNIVERSARY_CONFETTI_LAST_DATE, isoDate)

    /**
     * 「预算周期内不超支」撒花的去重记号：存最后一次撒花对应的周期起始日（ISO）。
     * 同一个周期只在结束未超支时庆祝一次，不会因为反复打开账本页而重复撒。
     */
    val ledgerConfettiLastCycleStart: Flow<String> = read(Keys.LEDGER_CONFETTI_LAST_CYCLE_START, "")

    suspend fun setLedgerConfettiLastCycleStart(isoDate: String) =
        write(Keys.LEDGER_CONFETTI_LAST_CYCLE_START, isoDate)

    private fun read(key: Preferences.Key<String>, default: String): Flow<String> =
        store.data.map { it[key] ?: default }

    private suspend fun write(key: Preferences.Key<String>, value: String) {
        store.edit { it[key] = value }
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("app.themeMode")
        val MOTION_LEVEL = stringPreferencesKey("app.motionLevel")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("app.dynamicColorEnabled")
        val HOME_CARD_CONFIG = stringPreferencesKey("home.cardConfig")
        val PERIOD_REMINDER_LEAD_DAYS = intPreferencesKey("period.reminderLeadDays")
        val CLOUD_BACKUP_ENABLED = booleanPreferencesKey("backup.cloudEnabled")
        val CLOUD_BACKUP_LAST_SUCCESS_AT = longPreferencesKey("backup.lastSuccessAt")
        val CLOUD_BACKUP_LAST_ERROR = stringPreferencesKey("backup.lastError")
        val KNOWLEDGE_DAILY_PUSH_ENABLED = booleanPreferencesKey("knowledge.dailyPushEnabled")
        val NOTE_QUICK_ENTRY_ENABLED = booleanPreferencesKey("note.quickEntryEnabled")
        val PERIOD_CARE_REMINDER_ENABLED = booleanPreferencesKey("period.careReminderEnabled")
        val INTERVIEW_ASSETS_VERSION = intPreferencesKey("interview.assetsVersion")
        val AI_ENABLED = booleanPreferencesKey("ai.enabled")
        val AI_WEB_SEARCH = booleanPreferencesKey("ai.webSearch")
        val PERIOD_AI_RESULT = stringPreferencesKey("period.ai.result")
        val PERIOD_AI_FINGERPRINT = stringPreferencesKey("period.ai.fingerprint")
        val PERIOD_AI_UPDATED_AT = longPreferencesKey("period.ai.updatedAt")
        val ANNIVERSARY_CONFETTI_LAST_DATE = stringPreferencesKey("anniversary.confetti.lastDate")
        val LEDGER_CONFETTI_LAST_CYCLE_START = stringPreferencesKey("ledger.confetti.lastCycleStart")
    }
}
