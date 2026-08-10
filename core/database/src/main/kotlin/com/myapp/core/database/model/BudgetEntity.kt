package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 预算（PRD 3.6.2）。
 *
 * **预算周期**：`cycleStartDay` 1~28（默认 10 = 发薪日），「本期」= 从上一个
 * cycleStartDay 到下一个 cycleStartDay 前一天的区间，不是自然月。
 *
 * **历史预算**：每期一行，`effectiveTo = null` 表示当前生效。Phase 1 只用单行
 * （永远 effectiveTo = null）；Phase 2 加历史回顾时，旧预算 effectiveTo 落时间戳，
 * 新预算 effectiveFrom = now、effectiveTo = null。
 *
 * **总预算**：`totalAmount` 单位分；分类预算在 `budget_category` 表（Phase 2 落地）。
 *
 * 无 uuid / deletedAt：预算是配置不是业务数据，不走同步、不软删，旧版本被新版本覆盖即可。
 */
@Entity(
    tableName = "budget",
    indices = [Index("effective_to")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "cycle_start_day")
    val cycleStartDay: Int,

    /** 总预算，单位：分。 */
    @ColumnInfo(name = "total_amount")
    val totalAmount: Long,

    @ColumnInfo(name = "effective_from")
    val effectiveFrom: Long,

    /** null = 当前生效；非 null = 历史预算的失效时间。 */
    @ColumnInfo(name = "effective_to")
    val effectiveTo: Long? = null,

    /** true = 每期自动沿用上期；false = 手动确认。Phase 1 永远 true。 */
    @ColumnInfo(name = "auto_rollover")
    val autoRollover: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
