package com.myapp.feature.knowledge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 知识源仓库的纯逻辑测试（PRD 3.7）。
 *
 * [KnowledgeRepository] 本身要 Room DAO 跑不了纯 JVM，抽出来的纯函数/纯逻辑单独钉死：
 * - [reorderIds]：上移/下移边界，与 `feature.ledger.data.reorder`/`HomeCardOrderViewModel`
 *   同一套「整表按下标重排」口径
 * - [KnowledgeFetchStatus.from]：脏字符串回退默认值
 * - [KnowledgeSourceDraft.canSave]：URL 必填
 */
class KnowledgeRepositoryTest {

    private val ids = listOf(10L, 20L, 30L, 40L)

    @Test
    fun reorderIds_moveUp_swapsWithPrevious() {
        assertEquals(listOf(10L, 30L, 20L, 40L), reorderIds(ids, id = 30L, delta = -1))
    }

    @Test
    fun reorderIds_moveDown_swapsWithNext() {
        assertEquals(listOf(10L, 30L, 20L, 40L), reorderIds(ids, id = 20L, delta = 1))
    }

    @Test
    fun reorderIds_atFirst_moveUp_returnsNull() {
        assertNull(reorderIds(ids, id = 10L, delta = -1))
    }

    @Test
    fun reorderIds_atLast_moveDown_returnsNull() {
        assertNull(reorderIds(ids, id = 40L, delta = 1))
    }

    @Test
    fun reorderIds_unknownId_returnsNull() {
        assertNull(reorderIds(ids, id = 99L, delta = -1))
    }

    @Test
    fun reorderIds_keepsEveryIdExactlyOnce() {
        val moved = reorderIds(ids, id = 40L, delta = -1)!!
        assertEquals(ids.size, moved.size)
        assertEquals(ids.toSet(), moved.toSet())
    }

    @Test
    fun fetchStatus_parsesKnownValues() {
        assertEquals(KnowledgeFetchStatus.SUCCESS, KnowledgeFetchStatus.from("SUCCESS"))
        assertEquals(KnowledgeFetchStatus.LOGIN_REQUIRED, KnowledgeFetchStatus.from("LOGIN_REQUIRED"))
    }

    @Test
    fun fetchStatus_unknownOrNull_fallsBackToPending() {
        assertEquals(KnowledgeFetchStatus.PENDING, KnowledgeFetchStatus.from("GARBAGE"))
        assertEquals(KnowledgeFetchStatus.PENDING, KnowledgeFetchStatus.from(null))
    }

    @Test
    fun draft_canSave_requiresNonBlankUrl() {
        assertTrue(KnowledgeSourceDraft(url = "https://a.feishu.cn/docx/x").canSave)
        assertEquals(false, KnowledgeSourceDraft(url = "").canSave)
        assertEquals(false, KnowledgeSourceDraft(url = "   ").canSave)
    }

    @Test
    fun newDraft_isNew() {
        assertTrue(KnowledgeSourceDraft().isNew)
        assertEquals(false, KnowledgeSourceDraft(id = 5L).isNew)
    }
}
