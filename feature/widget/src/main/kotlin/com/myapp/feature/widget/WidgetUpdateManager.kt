package com.myapp.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.myapp.core.common.contract.WidgetRefreshNotifier
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.di.IoDispatcher
import com.myapp.feature.widget.anniversary.AnniversaryCountdownWidget
import com.myapp.feature.widget.overview.TodayOverviewWidget
import com.myapp.feature.widget.todayexpense.TodayExpenseWidget
import com.myapp.feature.widget.todaytodo.TodayTodoWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 小组件刷新协调器（PRD 3.10）。
 *
 * 数据变更主动刷新：各 feature 的 Repository 在写操作后调用 [notifyDataChanged]，
 * 这里收集后对全部四个小组件执行 updateAll()。Glance 的 updateAll 内部走
 * WorkManager 入队（非阻塞），withContext(io) 只是避免在主线程碰 DAO 之外的杂活。
 *
 * 注意：`SharedFlow(extraBufferCapacity)` 而不是 StateFlow——刷新是一次性事件，
 * 不需要「重放最新值」语义；无订阅者时 emit 直接丢弃，进程刚启动时不会积累。
 */
@Singleton
class WidgetUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : WidgetRefreshNotifier {

    private val _refreshEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    val refreshEvents = _refreshEvents.asSharedFlow()

    /** 进程启动后开始收集刷新事件。@Inject 方法在构造后立即调用，无需手动接线。 */
    @Inject
    fun startCollecting(@ApplicationScope scope: CoroutineScope) {
        scope.launch {
            refreshEvents.collect { updateAllWidgets() }
        }
    }

    override suspend fun notifyDataChanged() {
        _refreshEvents.emit(Unit)
    }

    /** 刷新全部已添加的小组件。 */
    suspend fun updateAllWidgets() = withContext(io) {
        TodayOverviewWidget().updateAll(context)
        TodayExpenseWidget().updateAll(context)
        TodayTodoWidget().updateAll(context)
        AnniversaryCountdownWidget().updateAll(context)
    }
}
