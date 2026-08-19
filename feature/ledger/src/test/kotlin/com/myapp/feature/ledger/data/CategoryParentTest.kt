package com.myapp.feature.ledger.data

import com.myapp.core.database.model.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [resolveParentId] 的校验逻辑（PRD 3.6.1「支持自建子分类……最多两级」）。
 *
 * 四条规则各测一次「合法」和「不合法」的边界，外加一条正常挂靠的正例。
 */
class CategoryParentTest {

    private fun topLevel(id: Long) = CategoryEntity(
        id = id,
        name = "top-$id",
        icon = "other",
        color = "neutralGray",
        sortOrder = 1,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun childOf(id: Long, parentId: Long) = topLevel(id).copy(parentId = parentId)

    @Test
    fun `正常挂靠到一个顶级分类下`() {
        val result = resolveParentId(
            requestedParentId = 1L,
            selfId = 2L,
            isProtected = false,
            selfHasChildren = false,
            parentCandidate = topLevel(1L),
        )
        assertEquals(1L, result)
    }

    @Test
    fun `没选父分类就是顶级`() {
        val result = resolveParentId(
            requestedParentId = null,
            selfId = 2L,
            isProtected = false,
            selfHasChildren = false,
            parentCandidate = null,
        )
        assertNull(result)
    }

    @Test
    fun `保留项永远是顶级 忽略传入值`() {
        val result = resolveParentId(
            requestedParentId = 1L,
            selfId = 99L,
            isProtected = true,
            selfHasChildren = false,
            parentCandidate = topLevel(1L),
        )
        assertNull(result)
    }

    @Test
    fun `不能把自己设成自己的父分类`() {
        val result = resolveParentId(
            requestedParentId = 5L,
            selfId = 5L,
            isProtected = false,
            selfHasChildren = false,
            // 现实中查不到自己是「顶级」还是别的，这里给个顶级占位，
            // 校验应该在比对 id 那一步就拦下，不看这个参数
            parentCandidate = topLevel(5L),
        )
        assertNull(result)
    }

    @Test
    fun `自己已经有子分类的不能再选父分类`() {
        val result = resolveParentId(
            requestedParentId = 1L,
            selfId = 2L,
            isProtected = false,
            selfHasChildren = true,
            parentCandidate = topLevel(1L),
        )
        assertNull(result)
    }

    @Test
    fun `父分类候选本身是子分类时不合法 不能出现三级`() {
        val result = resolveParentId(
            requestedParentId = 1L,
            selfId = 2L,
            isProtected = false,
            selfHasChildren = false,
            // id=1 自己也挂在 id=99 下面，不是顶级，不能再被别人当父分类
            parentCandidate = childOf(id = 1L, parentId = 99L),
        )
        assertNull(result)
    }

    @Test
    fun `父分类候选查不到了 比如已被删除 不合法`() {
        val result = resolveParentId(
            requestedParentId = 1L,
            selfId = 2L,
            isProtected = false,
            selfHasChildren = false,
            parentCandidate = null,
        )
        assertNull(result)
    }
}
