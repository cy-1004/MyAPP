package com.myapp.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    }
}
