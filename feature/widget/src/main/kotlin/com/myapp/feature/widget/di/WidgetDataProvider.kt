package com.myapp.feature.widget.di

import com.myapp.core.common.contract.TodoToggleWriter
import com.myapp.core.database.dao.AnniversaryDao
import com.myapp.core.database.dao.BudgetDao
import com.myapp.core.database.dao.CategoryDao
import com.myapp.core.database.dao.TodoDao
import com.myapp.core.database.dao.TransactionDao
import com.myapp.feature.widget.WidgetUpdateManager
import com.myapp.feature.widget.data.WidgetNavTarget
import com.myapp.feature.widget.data.WidgetPrefsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 小组件数据入口。
 *
 * Glance 的 provideGlance 不是 Composable 也不是 Hilt 托管对象，
 * 通过 EntryPointAccessors 从应用图里取 DAO（与 MyApp 取 LedgerDeepLink 同一套模式）。
 * ActionCallback 同样靠它拿 [TodoToggleWriter] 与刷新协调器。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataProvider {
    fun transactionDao(): TransactionDao
    fun budgetDao(): BudgetDao
    fun todoDao(): TodoDao
    fun anniversaryDao(): AnniversaryDao
    fun categoryDao(): CategoryDao
    fun widgetPrefsStore(): WidgetPrefsStore
    fun todoToggleWriter(): TodoToggleWriter
    fun widgetUpdateManager(): WidgetUpdateManager
    fun widgetNavTarget(): WidgetNavTarget
}
