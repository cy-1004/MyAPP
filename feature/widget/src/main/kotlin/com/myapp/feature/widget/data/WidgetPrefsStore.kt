package com.myapp.feature.widget.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore by preferencesDataStore(name = "widget_preferences")

/**
 * 小组件配置持久化（PRD 3.10）。
 *
 * 每个 widget 实例（appWidgetId）可以有独立配置，key 用 `widget.<appWidgetId>.<key>`。
 * 与 AppPreferences 分开：小组件配置是 widget 模块内部细节，不进入全局命名空间。
 */
@Singleton
class WidgetPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.widgetDataStore

    // ---- W1 今日概览 ----

    /** 是否显示支出区。默认 true；配置页可关（不想让旁人看到钱的场景，PRD 3.10）。 */
    fun w1ShowExpense(appWidgetId: Int): Flow<Boolean> =
        store.data.map { it[Keys.w1ShowExpense(appWidgetId)] ?: true }

    suspend fun setW1ShowExpense(appWidgetId: Int, show: Boolean) {
        store.edit { it[Keys.w1ShowExpense(appWidgetId)] = show }
    }

    // ---- W4 纪念日倒数 ----

    /** null = 默认链（置顶 → 最近下一个 → 最早创建）；非 null = 用户选定某一条。 */
    fun w4SelectedAnniversaryId(appWidgetId: Int): Flow<Long?> =
        store.data.map { it[Keys.w4AnniversaryId(appWidgetId)] }

    suspend fun setW4SelectedAnniversaryId(appWidgetId: Int, id: Long?) {
        store.edit { prefs ->
            val key = Keys.w4AnniversaryId(appWidgetId)
            if (id != null) prefs[key] = id else prefs.remove(key)
        }
    }

    private object Keys {
        fun w1ShowExpense(appWidgetId: Int) = booleanPreferencesKey("widget.$appWidgetId.w1.showExpense")
        fun w4AnniversaryId(appWidgetId: Int) = longPreferencesKey("widget.$appWidgetId.w4.anniversaryId")
    }
}
