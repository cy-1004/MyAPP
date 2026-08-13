package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 预算预警去重状态（PRD 3.6.2：80%/100% 各一次性通知）。
 *
 * key 是 [cycleStartEpoch]（当前预算周期起点的 epochMilli）而不是 `budget.id`：
 * `BudgetRepository.setBudget` 在值没变时不产生新版本，同一个 `budget.id` 会跨很多个真实周期，
 * 拿它当"这一期"的唯一标识会导致同一期反复触发或跨期漏触发。用周期起点当 key，
 * 新周期到来自然表现为"没有这一行"，不需要显式重置逻辑。
 *
 * 不进云备份（见 `BackupDao` 的说明）：换机后重新走一遍 80%/100% 判定没有副作用。
 */
@Serializable
@Entity(
    tableName = "budget_alert_state",
    indices = [Index("cycle_start_epoch", unique = true)],
)
data class BudgetAlertStateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "cycle_start_epoch")
    val cycleStartEpoch: Long,

    @ColumnInfo(name = "notified_80")
    val notified80: Boolean = false,

    @ColumnInfo(name = "notified_100")
    val notified100: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
