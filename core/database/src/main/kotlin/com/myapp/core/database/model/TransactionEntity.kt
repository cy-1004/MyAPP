package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 一笔账目（PRD 3.6.1）。
 *
 * **金额一律存「分」Long**（PRD 4.2：严禁 Float/Double，浮点累加误差不可接受）。
 *
 * `direction` / `status` / `source` 存字符串不存枚举（与 [AnniversaryEntity.repeatType] /
 * [QuestionEntity.status] 同一套约定）：加新值不做迁移。
 *
 * `categoryId` 引用 [CategoryEntity.id]，不用 @ForeignKey：分类只软删除不硬删，
 * 引用完整性在 Repository 层保证，与项目其他实体保持一致。
 *
 * Phase 1 只产生 `source = MANUAL` + `status = CONFIRMED` 的条目；
 * `PENDING` 与 `AUTO` 留给 Phase 3 自动记账。
 */
@Entity(
    tableName = "transaction_record",
    indices = [
        Index("uuid", unique = true),
        Index("occurred_at"),
        Index("status"),
        Index("category_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    /** 金额，单位：分。 */
    val amount: Long,

    /** 'EXPENSE' / 'INCOME'。 */
    val direction: String,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    val merchant: String? = null,

    /** 'WECHAT' / 'ALIPAY' / 'BANK' / 'CASH' / null。手工记账可空。 */
    val channel: String? = null,

    /** 发生时间，epochMilli。 */
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /** 'PENDING' / 'CONFIRMED'。手工记账默认 CONFIRMED。 */
    val status: String,

    /** 自动记账原文，手工记账为 null。 */
    @ColumnInfo(name = "raw_text")
    val rawText: String? = null,

    /** 'AUTO' / 'MANUAL'。 */
    val source: String,

    val note: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
