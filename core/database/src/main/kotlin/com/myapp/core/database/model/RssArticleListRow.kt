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
