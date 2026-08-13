package com.myapp.feature.ledger.navigation

import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.budget.BudgetScreen
import com.myapp.feature.ledger.category.CategoryDetailScreen
import com.myapp.feature.ledger.category.CategoryListScreen
import com.myapp.feature.ledger.data.BudgetAlertTarget
import com.myapp.feature.ledger.data.LedgerDeepLink
import com.myapp.feature.ledger.data.LedgerSaveEvents
import com.myapp.feature.ledger.edit.LedgerEditScreen
import com.myapp.feature.ledger.list.LedgerListScreen
import com.myapp.feature.ledger.rule.RuleDetailScreen
import com.myapp.feature.ledger.rule.RuleListScreen
import com.myapp.feature.ledger.statistics.StatisticsScreen
import com.myapp.feature.ledger.unrecognized.UnrecognizedScreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 本 feature 自己的导航图片段（PRD 4.7.3）。
 *
 * :app 只需在导航图里调用一次 `ledgerGraph(...)`，
 * 新增页面时只改这个文件，不触碰其他模块。
 *
 * 编辑页保存后经 [LedgerSaveEvents]（进程内单例 Channel）把金额发给列表页 VM，
 * 列表页 VM 观察到后弹 Snackbar。不用 savedStateHandle 传值：进程恢复 + 底部 tab
 * 的 saveState/restoreState 会产生多个同名 entry，handle 对不上（真机踩过坑）。
 * 从全局 FAB 进编辑页时 previousBackStackEntry 是 Home，不发布事件 --
 * 这是预期行为（首页不需要 Snackbar）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface LedgerGraphEntryPoint {
    fun ledgerSaveEvents(): LedgerSaveEvents

    /** 系统通知 → 确认页深链。MainActivity 写入，MyApp 收集后导航。 */
    fun ledgerDeepLink(): LedgerDeepLink

    /** 预算预警通知 → 预算页深链。MainActivity 写入，MyApp 收集后导航。 */
    fun budgetAlertTarget(): BudgetAlertTarget
}

fun NavGraphBuilder.ledgerGraph(
    onNavigate: (Route) -> Unit,
    onBack: () -> Unit,
    navController: NavController,
) {
    composable<Route.Ledger> {
        LedgerListScreen(onNavigate = onNavigate)
    }

    // 新建与编辑共用：Route.LedgerDetail(id) 的 id 默认为 0 表示新建
    composable<Route.LedgerDetail> {
        val context = LocalContext.current
        val saveEvents = remember(context) {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                LedgerGraphEntryPoint::class.java,
            ).ledgerSaveEvents()
        }
        LedgerEditScreen(
            onBack = onBack,
            onSaved = { amountCents, categoryId ->
                val prev = navController.previousBackStackEntry
                if (prev?.destination?.route == Route.Ledger::class.qualifiedName) {
                    saveEvents.publish(amountCents, categoryId)
                }
                onBack()
            },
        )
    }

    composable<Route.LedgerUnrecognized> {
        UnrecognizedScreen(onNavigate = onNavigate, onBack = onBack)
    }

    composable<Route.RuleList> {
        RuleListScreen(onNavigate = onNavigate, onBack = onBack)
    }

    composable<Route.RuleDetail> {
        RuleDetailScreen(onBack = onBack)
    }

    composable<Route.Budget> {
        BudgetScreen(onBack = onBack)
    }

    composable<Route.CategoryList> {
        CategoryListScreen(onNavigate = onNavigate, onBack = onBack)
    }

    composable<Route.CategoryDetail> {
        CategoryDetailScreen(onBack = onBack)
    }

    composable<Route.Statistics> {
        StatisticsScreen(onBack = onBack)
    }
}
