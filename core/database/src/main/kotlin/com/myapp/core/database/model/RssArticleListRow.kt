package com.myapp.core.database.model

import androidx.room.ColumnInfo

/**
 * 资讯列表用的投影行——**刻意不含 `content`**（PRD 3.9）。
 *
 * 为什么单独开一个类而不是直接查 [RssArticleEntity]：`content` 是抓回来的整篇正文，
 * 实测 5469 篇文章的 `content` + `summary` 合计接近 8MB（见 PRD 4.13 云备份那段的实测数据），
 * 而列表一个字都用不到它。查整行等于每次进资讯页都把 8MB 读出来、映射成对象、
 * 再交给 Compose——这就是「每次进入都要加载好久」的原因。
 * 详情页仍然查整行（那里确实要正文），走 `observeById`。
 */
data class RssArticleListRow(
    val id: Long,

    @ColumnInfo(name = "source_id")
    val sourceId: Long,

    val link: String,

    val title: String,

    val summary: String,

    @ColumnInfo(name = "cover_image_url")
    val coverImageUrl: String?,

    @ColumnInfo(name = "published_at")
    val publishedAt: Long,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,
)

/**
 * 分页版的投影行：比 [RssArticleListRow] 多一个 [sourceTitle]，**直接从 JOIN 出来的
 * `rss_source.title` 取**，不再回头单独查一次订阅源表。
 *
 * 非分页路径是「一批行拿到手后查一次 `sourceDao.getAll()` 建映射」，一批只查一次，划算；
 * 但 `PagingData.map` 是**逐条**执行的，那套做法会退化成每篇文章查一次全表。
 * query 本来就 JOIN 了 `rss_source`，把标题顺手带出来最省事。
 */
data class RssPagedArticleRow(
    val id: Long,

    @ColumnInfo(name = "source_id")
    val sourceId: Long,

    @ColumnInfo(name = "source_title")
    val sourceTitle: String,

    val link: String,

    val title: String,

    val summary: String,

    @ColumnInfo(name = "cover_image_url")
    val coverImageUrl: String?,

    @ColumnInfo(name = "published_at")
    val publishedAt: Long,

    @ColumnInfo(name = "is_read")
    val isRead: Boolean,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,
)
