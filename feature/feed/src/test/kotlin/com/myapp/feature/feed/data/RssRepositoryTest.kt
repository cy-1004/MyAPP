package com.myapp.feature.feed.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RssRepository] 的纯逻辑测试（PRD 3.9）——[reorderIds] 与 KnowledgeRepositoryTest 同一套
 * 「整表按下标重排」口径，钉死上移/下移边界。
 */
class RssRepositoryTest {

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
    fun draft_canSave_requiresUrl() {
        assertFalse(RssSourceDraft(url = "").canSave)
        assertFalse(RssSourceDraft(url = "   ").canSave)
        assertTrue(RssSourceDraft(url = "https://example.com/feed.xml").canSave)
    }

    @Test
    fun draft_isNew_matchesZeroId() {
        assertTrue(RssSourceDraft(id = 0L).isNew)
        assertFalse(RssSourceDraft(id = 5L).isNew)
    }
}
