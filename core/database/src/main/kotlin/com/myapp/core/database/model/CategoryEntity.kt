package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 记账分类（PRD 3.6.1）。
 *
 * **分类存在 DB 表里不是硬编码枚举**：用户可改名称/图标/颜色/排序/停用，
 * 加新分类不用做迁移。停用而非删除：已有账目仍指向它，硬删会让历史统计出现
 * 「无主账目」。停用后只是不再出现在选择器里。
 *
 * 内置 10 个默认分类（餐饮/交通/.../其他/未分类）由 [CategorySeeder] 在
 * 首次 DB 创建时灌入。`isProtected = true` 标记「未分类」保留项，不可删不可停用--
 * 自动记账没命中规则时必须有地方落。
 *
 * 全局字段约定（PRD 4.7.7）：uuid / createdAt / updatedAt / deletedAt。
 */
@Entity(
    tableName = "category",
    indices = [
        Index("uuid", unique = true),
        Index("is_active"),
        Index("sort_order"),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val uuid: String = UUID.randomUUID().toString(),

    val name: String,

    /** 图标 key（'food'/'transport'/...），UI 层用它选 ImageVector。 */
    @ColumnInfo(name = "icon")
    val icon: String,

    /** 莫兰迪色板 key（'clay'/'olive'/'mistBlue'/...），UI 层用它选 Color。 */
    @ColumnInfo(name = "color")
    val color: String,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    /** true = 保留项（未分类），不可删不可停用。 */
    @ColumnInfo(name = "is_protected")
    val isProtected: Boolean = false,

    /** 二级分类的父 id；Phase 1 不用，留字段避免将来迁移。 */
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)
