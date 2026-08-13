package com.myapp.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 知识源正文缓存（PRD 3.7）：无头 WebView 提取出的标题+纯文本正文。
 *
 * 每个 source 目前只有一条 content 行（`sectionIndex` 固定 0），刷新时整行替换。
 * `sectionIndex`/`sectionTitle` 按章节切分的字段先留着——M7（每日知识推送）
 * 要按章节做间隔重复，V1 不做切分，只是不想 M7 落地时再迁移一次表结构。
 *
 * 提取失败不建这行（[KnowledgeSourceEntity.fetchStatus] 记录失败原因），不存空正文占位。
 */
@Serializable
@Entity(
    tableName = "knowledge_content",
    indices = [Index("source_id")],
)
data class KnowledgeContentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_id")
    val sourceId: Long,

    @ColumnInfo(name = "section_index")
    val sectionIndex: Int = 0,

    @ColumnInfo(name = "section_title")
    val sectionTitle: String? = null,

    @ColumnInfo(name = "content_text")
    val contentText: String,

    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)
