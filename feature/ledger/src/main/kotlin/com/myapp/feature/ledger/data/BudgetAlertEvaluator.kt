package com.myapp.feature.ledger.data

/** 一期内的预警去重状态（PRD 3.6.2：80%/100% 各一次性通知）。 */
data class AlertState(val notified80: Boolean, val notified100: Boolean)

enum class AlertKind { REACHED_80, REACHED_100 }

/**
 * 预算预警判定（PRD 3.6.2），纯函数。
 *
 * 100% 触发时不再单独触发 80%——100% 的通知文案本身就涵盖了「已用完」这件事，
 * 用户不需要在几乎同一时刻收到两条内容重叠的通知。这也覆盖了「一笔大额支出直接从
 * 50% 跳到 105%」的情况：只弹一条 100% 通知，不会先弹 80% 再弹 100%。
 */
object BudgetAlertEvaluator {

    fun evaluate(spentCents: Long, budgetCents: Long, state: AlertState): List<AlertKind> {
        if (budgetCents <= 0L) return emptyList()
        if (spentCents >= budgetCents) {
            return if (!state.notified100) listOf(AlertKind.REACHED_100) else emptyList()
        }
        val threshold80 = budgetCents * 80 / 100
        return if (spentCents >= threshold80 && !state.notified80) {
            listOf(AlertKind.REACHED_80)
        } else {
            emptyList()
        }
    }
}
