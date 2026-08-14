package com.myapp.feature.knowledge.interview

import android.content.Context
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.common.time.AppTime
import com.myapp.core.database.dao.InterviewDao
import com.myapp.core.database.model.InterviewChapterEntity
import com.myapp.core.database.model.InterviewQuestionEntity
import com.myapp.core.datastore.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 把 assets 里的面试题 md 导入数据库（PRD 3.7）。
 *
 * 触发时机：App 启动时调一次 [importIfNeeded]。只有当
 * [INTERVIEW_ASSETS_VERSION] 比上次导入记录的版本高才真的干活——
 * 解析两篇 44 万字的文档要几百毫秒，没必要每次冷启动都做。
 *
 * 幂等性由三件事保证，所以重复导入是安全的：
 * - 章节/题目按稳定 key 生成，整篇替换（`InterviewDao.replaceDoc`）；
 * - 章节的「是否在池」在替换时按 key 沿用旧值，不会把用户的勾选重置；
 * - 复习进度存在独立的 `interview_review` 表、挂在 `question_key` 上，本次导入完全不碰它。
 */
@Singleton
class InterviewImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: InterviewDao,
    private val preferences: AppPreferences,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun importIfNeeded(): Unit = withContext(io) {
        val imported = preferences.interviewAssetsVersion.first()
        if (imported >= INTERVIEW_ASSETS_VERSION) return@withContext
        INTERVIEW_DOCS.forEach { doc -> runCatching { importDoc(doc) } }
        preferences.setInterviewAssetsVersion(INTERVIEW_ASSETS_VERSION)
    }

    /** 强制重新导入（设置页「重新导入题库」用），忽略版本号。 */
    suspend fun reimport(): Unit = withContext(io) {
        INTERVIEW_DOCS.forEach { doc -> runCatching { importDoc(doc) } }
        preferences.setInterviewAssetsVersion(INTERVIEW_ASSETS_VERSION)
    }

    private suspend fun importDoc(doc: InterviewDoc) {
        val markdown = context.assets
            .open("$ASSETS_ROOT/${doc.key}/doc.md")
            .bufferedReader()
            .use { it.readText() }

        val parsed = InterviewMarkdownParser.parse(markdown)
        if (parsed.isEmpty()) return

        val now = AppTime.now()
        val chapters = parsed.mapIndexed { index, chapter ->
            InterviewChapterEntity(
                key = chapterKey(doc.key, chapter.title),
                docKey = doc.key,
                docName = doc.displayName,
                title = chapter.title,
                sortOrder = index,
                updatedAt = now,
            )
        }
        val questionsByChapterKey = parsed.associate { chapter ->
            val cKey = chapterKey(doc.key, chapter.title)
            cKey to chapter.questions.mapIndexed { index, question ->
                InterviewQuestionEntity(
                    key = questionKey(doc.key, chapter.title, question.title, index),
                    // chapterId 在 replaceDoc 里按插入结果回填，这里给 0 占位
                    chapterId = 0L,
                    title = question.title,
                    body = question.body,
                    sortOrder = index,
                    updatedAt = now,
                )
            }
        }

        dao.replaceDoc(doc.key, chapters, questionsByChapterKey)
    }

    private fun chapterKey(docKey: String, chapterTitle: String): String =
        sha1("$docKey|$chapterTitle")

    /**
     * 题目的稳定 key。
     *
     * 带上 [index] 是因为**同一章里题干可能重名**——两篇文档里都有若干道
     * 「场景题」「补充」这类标题。只用标题做 key 会让它们撞成一条，
     * 唯一索引一冲突，后面那道就把前面那道顶掉，题库凭空少题。
     * 代价是「在某章中间插入一道新题」会让它后面所有题的 key 变、复习进度重置；
     * 相比「静默丢题」这是可接受的一侧。
     */
    private fun questionKey(
        docKey: String,
        chapterTitle: String,
        questionTitle: String,
        index: Int,
    ): String = sha1("$docKey|$chapterTitle|$index|$questionTitle")

    private fun sha1(input: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val ASSETS_ROOT = "interview"
    }
}
