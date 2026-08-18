package com.myapp.feature.ledger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.theme.LocalMotionLevel
import com.myapp.core.designsystem.theme.MotionTokens
import com.myapp.core.designsystem.theme.Spacing
import com.myapp.core.designsystem.theme.appColors
import com.myapp.feature.ledger.list.SavedEvent
import kotlinx.coroutines.delay

/** 浮层自动消失前停留多久。够读完两行字，又不至于挡着列表碍事。 */
private const val VISIBLE_MILLIS = 2600L

/**
 * 记一笔之后的即时提示浮层（PRD 3.6.2）。
 *
 * 替掉原来的纯文字 Snackbar--PRD 要的是「金额从 0 滚动 + 进度条推进」，
 * 交接文档把当时的 Snackbar 实现标记为**体验降级**，这里把它补回来。
 *
 * 两个动画都用 [MotionTokens.DurationCounter]（该令牌的注释写的就是「数值滚动：金额、进度条」）。
 * PRD 原文写的是 800ms，令牌是 700ms--以令牌为准，项目规矩是所有动画一律引用令牌，
 * 免得各处随手写时长导致全局手感不一致。
 *
 * **进度条是「推进」不是「填充」**：从记这笔之前的已用比例animate 到记完之后的比例，
 * 而不是每次都从 0 长出来。用户要看的是「我刚花的这笔把预算推了多远」，
 * 从 0 开始的话这个信息就丢了。
 *
 * @param budgetTotalCents 本周期预算总额；没设预算时传 null，此时不显示进度条
 */
@Composable
fun SaveFeedbackOverlay(
    event: SavedEvent?,
    budgetTotalCents: Long?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 停留计时。event 变化就重新计时——连着记两笔时，第二笔应该重新给够阅读时间
    LaunchedEffect(event) {
        if (event == null) return@LaunchedEffect
        delay(VISIBLE_MILLIS)
        onDismiss()
    }

    AnimatedVisibility(
        visible = event != null,
        enter = slideInVertically(MotionTokens.enterSpringOffset()) { it } + fadeIn(),
        exit = slideOutVertically(MotionTokens.exitTween()) { it } + fadeOut(),
        modifier = modifier,
    ) {
        // 退场动画期间内容仍在组合树里，而此时 event 已经变回 null，
        // 直接用会让浮层在滑出去的过程中先闪成空白。用一个普通持有器兜住最后一次非空值：
        // 它只在组合期间被写、被读，不是 State、不参与重组失效，正是这里要的语义
        val holder = remember { LastEventHolder() }
        if (event != null) holder.value = event
        holder.value?.let { SaveFeedbackCard(event = it, budgetTotalCents = budgetTotalCents) }
    }
}

/** 见上：只为让退场动画有内容可画，不需要是 State。 */
private class LastEventHolder {
    var value: SavedEvent? = null
}

@Composable
private fun SaveFeedbackCard(event: SavedEvent, budgetTotalCents: Long?) {
    val motionLevel = LocalMotionLevel.current
    val duration = motionLevel.scale(MotionTokens.DurationCounter)

    // 金额从 0 滚上去（PRD 3.6.2）。用 Animatable 而不是 animateFloatAsState：
    // 需要「每次事件都从 0 重新滚」，而不是从上一笔的数值补间过去
    val amountProgress = remember(event) { Animatable(0f) }
    LaunchedEffect(event) {
        amountProgress.animateTo(1f, tween(duration, easing = MotionTokens.EmphasizedEasing))
    }
    val rollingCents = (event.savedAmountCents * amountProgress.value).toLong()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "已记录",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.appColors.textSecondary,
                )
                Text(
                    text = rollingCents.yuanWithSymbol(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (budgetTotalCents != null && budgetTotalCents > 0L && event.remainingCents != null) {
                BudgetAdvanceBar(
                    totalCents = budgetTotalCents,
                    remainingCents = event.remainingCents,
                    justSpentCents = event.savedAmountCents,
                    isOverBudget = event.isOverBudget,
                    durationMillis = duration,
                )
            }

            SummaryLine(event)
        }
    }
}

/**
 * 预算进度条：从「记这笔之前」推进到「记完之后」。
 *
 * 超支时比例会 > 1，条子钉在满格并转成危险色--继续按真实比例画会溢出控件，
 * 而「满了还在涨」这个信息用颜色表达比用长度更清楚。
 */
@Composable
private fun BudgetAdvanceBar(
    totalCents: Long,
    remainingCents: Long,
    justSpentCents: Long,
    isOverBudget: Boolean,
    durationMillis: Int,
) {
    val spentAfter = totalCents - remainingCents
    val spentBefore = spentAfter - justSpentCents
    val ratioBefore = (spentBefore.toFloat() / totalCents).coerceIn(0f, 1f)
    val ratioAfter = (spentAfter.toFloat() / totalCents).coerceIn(0f, 1f)

    val progress = remember(ratioBefore, ratioAfter) { Animatable(ratioBefore) }
    LaunchedEffect(ratioAfter) {
        progress.animateTo(ratioAfter, tween(durationMillis, easing = MotionTokens.EmphasizedEasing))
    }

    LinearProgressIndicator(
        progress = { progress.value },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = if (isOverBudget) MaterialTheme.appColors.danger else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.appColors.border,
    )
}

/** 「本期剩余 / 已超支」+ 命中分类的预算情况，与原 Snackbar 的文案口径一致。 */
@Composable
private fun SummaryLine(event: SavedEvent) {
    val parts = buildList {
        if (event.remainingCents != null) {
            add(
                if (event.isOverBudget) {
                    "本期已超支 ${(-event.remainingCents).yuanWithSymbol()}"
                } else {
                    "本期剩余 ${event.remainingCents.yuanWithSymbol()}"
                },
            )
        }
        if (event.categoryRemainingCents != null) {
            add(
                if (event.categoryOverBudget) {
                    "该分类已超支 ${(-event.categoryRemainingCents).yuanWithSymbol()}"
                } else {
                    "该分类剩余 ${event.categoryRemainingCents.yuanWithSymbol()}"
                },
            )
        }
    }
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = if (event.isOverBudget || event.categoryOverBudget) {
            MaterialTheme.appColors.danger
        } else {
            MaterialTheme.appColors.textSecondary
        },
    )
}
