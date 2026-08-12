package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一篇抓取到的 RSS/Atom 文章（PRD 3.9）。
 *
 * 不用 `deleted_at` 软删除——这是从外部源缓存的资讯，不是用户手填数据，
 * 清理策略（非收藏保留 30 天，超期物理删除）直接 DELETE 即可，不需要撤销。
 * `sourceId` 不用外键，同 [KnowledgeContentEntity.sourceId] 约定。
 *
 * 去重键是 `(source_id, guid)`：同一订阅源里 guid（RSS `<guid>` / Atom `<id>`，
 * 缺失时退化用 link）重复即视为同一篇，插入用 `OnConflictStrategy.IGNORE`。
 */
@Entity(
    tableName = "rss_article",
    indices = [
        Index(value = ["source_id", "guid"], unique = true),
        Index("published_at"),
        Index("is_read"),
        Index("is_favorite"),
    ],
)
data class RssArticleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_id")
    val sourceId: Long,

    val guid: String,

    val link: String,

    val title: String,

    val summary: String,

    /**
     * 全文（RSS `<content:encoded>` / Atom `<content>`），与 [summary]（`<description>`/`<summary>`）
     * 分开存——很多源两者都提供且内容不同。为 null 时详情页按 PRD 3.9 用 Custom Tabs 打开原链接。
     */
    val content: String? = null,

    @ColumnInfo(name = "cover_image_url")
    val coverImageUrl: String? = null,

    @ColumnInfo(name = "published_at")
    val publishedAt: Long,

    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
)
