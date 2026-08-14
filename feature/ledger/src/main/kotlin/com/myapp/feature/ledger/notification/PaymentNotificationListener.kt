package com.myapp.feature.ledger.notification

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.myapp.core.common.contract.LedgerWriter
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.keepalive.NotificationListenerConnection
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.BudgetCycle
import com.myapp.core.database.dao.TransactionDao
import com.myapp.feature.ledger.data.BudgetCategoryRepository
import com.myapp.feature.ledger.data.BudgetRepository
import com.myapp.feature.ledger.data.LedgerPrefsStore
import com.myapp.feature.ledger.data.LedgerRepository
import com.myapp.feature.ledger.data.RuleRepository
import com.myapp.feature.ledger.data.UnrecognizedItem
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 自动记账的监听落地点（PRD 3.6.1）。
 *
 * 只处理白名单包名（[PaymentWhitelist]）；命中后取标题+正文交给 [PaymentParser]，
 * 解析成功 → [LedgerWriter.recordExpense] 落库（PENDING + AUTO + rawText）→ 发系统通知；
 * 解析失败 → 原文进未识别队列（[LedgerPrefsStore]），不静默丢弃。
 *
 * 收到通知和真正落库之间隔了 IO（规则解析 + 数据库写入），用 goAsync() +
 * ApplicationScope 协程完成，不阻塞系统的通知派发线程。
 *
 * 注意：这是自用 App 的兜底方案，PRD 3.6.1「已知限制」明确不指望 100% 识别。
 */
@AndroidEntryPoint
class PaymentNotificationListener : NotificationListenerService() {

    @Inject lateinit var ledgerWriter: LedgerWriter
    @Inject lateinit var ledgerRepository: LedgerRepository
    @Inject lateinit var budgetRepository: BudgetRepository
    @Inject lateinit var budgetCategoryRepository: BudgetCategoryRepository
    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var categorizer: AutoCategorizer
    @Inject lateinit var prefs: LedgerPrefsStore
    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var notifier: AutoLedgerNotifier
    @Inject lateinit var connection: NotificationListenerConnection
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    /**
     * 系统真正把服务连上时回调。只有走到这里，通知才会送达——
     * 「用户授权了」推不出「服务连上了」，详见 [NotificationListenerConnection]。
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        connection.onConnected()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connection.onDisconnected()
    }

    override fun onDestroy() {
        connection.onDisconnected()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val channel = PaymentWhitelist.channelOf(sbn.packageName) ?: return
        val title = sbn.notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extractText(sbn.notification).trim()
        if (title.isEmpty() && text.isEmpty()) return
        // 白名单只按包名过滤：微信等 App 还有大量普通聊天通知，
        // 不含金额/支付特征的一律跳过，不占未识别队列
        if (!PaymentParser.isLikelyPayment(title, text)) return

        // 协程在 ApplicationScope 跑，回调立即返回；异常捕获兜底进未识别队列
        appScope.launch {
            try {
                handle(channel, title, text)
            } catch (_: Throwable) {
                runCatching {
                    prefs.addUnrecognized(
                        UnrecognizedItem(
                            id = AppTime.now(),
                            channel = channel,
                            title = title,
                            text = text,
                            occurredAt = AppTime.now(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun handle(channel: String, title: String, text: String) {
        val now = AppTime.now()
        val raw = "$title\n$text"

        // 去重：同一通知重复投递（60s 窗口内已有相同原文的 PENDING 条目）
        val duplicate = transactionDao.getPendingByRawText(raw, now - DEDUPE_WINDOW_MILLIS)
        if (duplicate != null) return

        when (val result = PaymentParser.parse(channel, title, text, ruleRepository.activeRules.first())) {
            is PaymentParseResult.Success -> {
                val category = categorizer.categorize(result.merchant)
                val id = ledgerWriter.recordExpense(
                    amountCents = result.amountCents,
                    merchant = result.merchant,
                    category = category,
                    occurredAt = now,
                    raw = raw,
                    direction = result.direction,
                )
                val remaining = budgetRemaining()
                val categoryRemaining = categoryBudgetRemaining(id)
                notifier.post(
                    id,
                    result.amountCents,
                    result.direction,
                    result.merchant,
                    category,
                    remaining,
                    categoryRemaining,
                )
            }
            PaymentParseResult.Failed -> {
                prefs.addUnrecognized(
                    UnrecognizedItem(
                        id = now,
                        channel = channel,
                        title = title,
                        text = text,
                        occurredAt = now,
                    ),
                )
            }
        }
    }

    /** 当前预算周期剩余（分）。没设预算返回 null。 */
    private suspend fun budgetRemaining(): Long? {
        val budget = budgetRepository.getCurrent() ?: return null
        val range = BudgetCycle.currentCycleRange(budget.cycleStartDay)
        val spent = ledgerRepository.sumExpenseInRange(range.first, range.last + 1)
        return budget.totalAmountCents - spent
    }

    /** 该笔落库后实际的 categoryId（[recordExpense] 内部按名解析），命中分类预算时算剩余。 */
    private suspend fun categoryBudgetRemaining(transactionId: Long): Long? {
        val budget = budgetRepository.getCurrent() ?: return null
        val entity = transactionDao.getById(transactionId) ?: return null
        val cap = budgetCategoryRepository.observeCaps().first()[entity.categoryId] ?: return null
        val range = BudgetCycle.currentCycleRange(budget.cycleStartDay)
        val categorySpent = ledgerRepository.observeCategoryExpenses(range.first, range.last + 1)
            .first()
            .firstOrNull { it.categoryId == entity.categoryId }
            ?.totalCents ?: 0L
        return cap - categorySpent
    }

    private fun extractText(notification: Notification): String {
        val extras = notification.extras
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n")
        return listOfNotNull(text, big, lines).joinToString("\n")
    }

    companion object {
        /** 去重窗口：60 秒内相同原文视为重复投递。 */
        private const val DEDUPE_WINDOW_MILLIS = 60_000L

        /**
         * 覆盖安装后请求系统重新绑定服务（PRD 9.3）。
         *
         * 背景：`adb install -r` / 应用商店更新之后，系统会断开旧的 listener 绑定。
         * 标准 Android 会自动重连，ColorOS 不会——权限开关看着还是「已开启」，
         * 实际一条通知都收不到，自动记账静默失效（2026-08-14 实测确认，
         * 服务在 dumpsys 的 enabled 列表里但不在 Live 列表里）。
         *
         * [NotificationListenerService.requestRebind] 就是为这个场景设计的 API。
         * 未授权时它是 no-op，所以先判一次授权只是省一次无用的跨进程调用。
         * 幂等，进程每次启动调一次即可；已连接时再调也无副作用。
         *
         * 兜底：万一 requestRebind 也被 ROM 吞了，保活自检页会显示
         * 「已授权但未连接」并引导用户手动关一次再开（见 KeepAliveCheckViewModel）。
         */
        fun ensureBound(context: Context) {
            val granted = NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
            if (!granted) return
            runCatching {
                requestRebind(ComponentName(context, PaymentNotificationListener::class.java))
            }
        }
    }
}
