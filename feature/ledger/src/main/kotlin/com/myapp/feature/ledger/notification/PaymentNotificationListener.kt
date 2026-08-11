package com.myapp.feature.ledger.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.myapp.core.common.contract.LedgerWriter
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.time.AppTime
import com.myapp.core.common.time.BudgetCycle
import com.myapp.core.database.dao.TransactionDao
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
    @Inject lateinit var transactionDao: TransactionDao
    @Inject lateinit var categorizer: AutoCategorizer
    @Inject lateinit var prefs: LedgerPrefsStore
    @Inject lateinit var ruleRepository: RuleRepository
    @Inject lateinit var notifier: AutoLedgerNotifier
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

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
                notifier.post(id, result.amountCents, result.direction, result.merchant, category, remaining)
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

    private fun extractText(notification: Notification): String {
        val extras = notification.extras
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n")
        return listOfNotNull(text, big, lines).joinToString("\n")
    }

    private companion object {
        /** 去重窗口：60 秒内相同原文视为重复投递。 */
        const val DEDUPE_WINDOW_MILLIS = 60_000L
    }
}
