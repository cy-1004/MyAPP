package com.myapp.feature.ledger.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分类管理的纯逻辑测试（PRD 3.6 M5 Phase 3）。
 *
 * [CategoryRepository] 本身要 Room DAO 跑不了纯 JVM，但两块最容易出错的逻辑是纯函数：
 * - [reorder]：上移/下移的边界（第一条不能再上移、最后一条不能再下移、id 不存在）
 * - [CategoryDraft.canSave]：名称必填 + 长度上限
 *
 * 排序尤其值得钉死：种子灌的 sortOrder 是 1..10，用户删掉几个再新增就会出现跳号，
 * 任何基于「sortOrder ± 1」的算术都会在跳号时错位。这里验证的是「整表按下标重排」的口径。
 */
class CategoryReorderTest {

    private val ids = listOf(10L, 20L, 30L, 40L)

    @Test
    fun moveUp_swapsWithPrevious() {
        assertEquals(listOf(10L, 30L, 20L, 40L), reorder(ids, id = 30L, delta = -1))
    }

    @Test
    fun moveDown_swapsWithNext() {
        assertEquals(listOf(10L, 30L, 20L, 40L), reorder(ids, id = 20L, delta = 1))
    }

    @Test
    fun moveUp_atFirst_returnsNull() {
        assertNull(reorder(ids, id = 10L, delta = -1))
    }

    @Test
    fun moveDown_atLast_returnsNull() {
        assertNull(reorder(ids, id = 40L, delta = 1))
    }

    @Test
    fun unknownId_returnsNull() {
        assertNull(reorder(ids, id = 99L, delta = -1))
    }

    @Test
    fun reorder_keepsEveryIdExactlyOnce() {
        val moved = reorder(ids, id = 40L, delta = -1)!!
        assertEquals(ids.size, moved.size)
        assertEquals(ids.toSet(), moved.toSet())
    }

    /** 排序值有跳号（种子 1..10 删几个再加）时，重排口径是按下标而不是按原值加减。 */
    @Test
    fun reorder_worksWithGappedSortOrders() {
        val gapped = listOf(3L, 7L, 12L)
        assertEquals(listOf(7L, 3L, 12L), reorder(gapped, id = 7L, delta = -1))
    }

    @Test
    fun canSave_requiresNonBlankName() {
        assertFalse(CategoryDraft(name = "").canSave)
        assertFalse(CategoryDraft(name = "   ").canSave)
        assertTrue(CategoryDraft(name = "宠物").canSave)
    }

    @Test
    fun canSave_rejectsOverlongName_countingTrimmed() {
        val tooLong = "一".repeat(CategoryDraft.MAX_NAME_LENGTH + 1)
        assertFalse(CategoryDraft(name = tooLong).canSave)

        val atLimit = "一".repeat(CategoryDraft.MAX_NAME_LENGTH)
        assertTrue(CategoryDraft(name = atLimit).canSave)
        // 前后空白不算长度：保存时会 trim
        assertTrue(CategoryDraft(name = "  $atLimit  ").canSave)
    }

    @Test
    fun newDraft_hasDefaultVisuals() {
        val draft = CategoryDraft()
        assertTrue(draft.isNew)
        assertEquals(DEFAULT_CATEGORY_ICON, draft.icon)
        assertEquals(DEFAULT_CATEGORY_COLOR, draft.color)
    }
}
