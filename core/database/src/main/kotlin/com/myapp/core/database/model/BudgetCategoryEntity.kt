package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 分类预算上限（PRD 3.6.2）：为任意分类各自设一个上限，各分类上限之和不必等于总预算。
 *
 * 不做历史版本化——总预算的 `effectiveFrom`/`effectiveTo` 是为了"近 12 期回顾"这个历史查询
 * 需求存在的，PRD 没要求分类预算有历史回顾，这里就是简单的「一个分类一行、原地更新」，
 * 没有行 = 该分类没设预算。
 *
 * 无 uuid/deletedAt：与 [BudgetEntity] 同理，是配置不是业务数据。
 */
@Serializable
@Entity(
    tableName = "budget_category",
    indices = [Index("category_id", unique = true)],
)
data class BudgetCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    /** 上限，单位：分。 */
    @ColumnInfo(name = "cap_cents")
    val capCents: Long,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
