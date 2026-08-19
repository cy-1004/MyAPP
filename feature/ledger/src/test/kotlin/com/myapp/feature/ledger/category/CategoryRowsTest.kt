package com.myapp.feature.ledger.category

import com.myapp.feature.ledger.data.ManagedCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [buildCategoryRows] 的分组/排序/兄弟范围逻辑（PRD 3.6.1「支持自建子分类……最多两级」）。
 *
 * 最容易出错的地方是 isFirst/isLast 的范围：必须是「同一父分类下的兄弟」，
 * 不能是全表位置——不然移动按钮的可点状态会跟 `CategoryRepository.move`
 * 真正的移动范围对不上。
 */
class CategoryRowsTest {

    private fun category(
        id: Long,
        sortOrder: Int,
        parentId: Long? = null,
        isActive: Boolean = true,
    ) = ManagedCategory(
        id = id,
        name = "c$id",
        icon = "other",
        color = "neutralGray",
        sortOrder = sortOrder,
        isActive = isActive,
        isProtected = false,
        parentId = parentId,
    )

    @Test
    fun `没有子分类时表现与原来的扁平列表一致`() {
        val categories = listOf(category(1, 1), category(2, 2), category(3, 3))
        val rows = buildCategoryRows(categories, emptyMap())

        assertEquals(listOf(1L, 2L, 3L), rows.map { it.category.id })
        assertTrue(rows.none { it.isChild })
        assertTrue(rows.none { it.hasChildren })
        assertTrue(rows[0].isFirst)
        assertTrue(rows[2].isLast)
    }

    @Test
    fun `子分类紧跟在父分类后面 按自己的sortOrder排序`() {
        val categories = listOf(
            category(1, 1), // 顶级：餐饮
            category(11, 2, parentId = 1L), // 餐饮 > 外卖
            category(10, 1, parentId = 1L), // 餐饮 > 堂食（sortOrder 更小，排在外卖前面）
            category(2, 2), // 顶级：交通
        )
        val rows = buildCategoryRows(categories, emptyMap())

        assertEquals(listOf(1L, 10L, 11L, 2L), rows.map { it.category.id })
    }

    @Test
    fun `顶级分类标记hasChildren 子分类标记isChild`() {
        val categories = listOf(category(1, 1), category(11, 1, parentId = 1L), category(2, 2))
        val rows = buildCategoryRows(categories, emptyMap())

        val parent = rows.first { it.category.id == 1L }
        val child = rows.first { it.category.id == 11L }
        val noChild = rows.first { it.category.id == 2L }

        assertTrue(parent.hasChildren)
        assertFalse(parent.isChild)
        assertTrue(child.isChild)
        assertFalse(child.hasChildren)
        assertFalse(noChild.hasChildren)
    }

    @Test
    fun `isFirst和isLast只在同一父分类的兄弟范围内计算`() {
        val categories = listOf(
            category(1, 1), // 顶级第 1 个
            category(11, 1, parentId = 1L), // 子分类第 1 个
            category(12, 2, parentId = 1L), // 子分类第 2 个（子分类范围内的最后一个）
            category(2, 2), // 顶级第 2 个（顶级范围内的最后一个）
        )
        val rows = buildCategoryRows(categories, emptyMap())

        val parent1 = rows.first { it.category.id == 1L }
        val parent2 = rows.first { it.category.id == 2L }
        val child11 = rows.first { it.category.id == 11L }
        val child12 = rows.first { it.category.id == 12L }

        // 顶级范围：1 是第一个，2 是最后一个——不受子分类插进中间影响
        assertTrue(parent1.isFirst)
        assertFalse(parent1.isLast)
        assertTrue(parent2.isLast)

        // 子分类范围：只在「1 的孩子」这个小组里比，跟顶级分类的位置无关
        assertTrue(child11.isFirst)
        assertFalse(child11.isLast)
        assertTrue(child12.isLast)
    }

    @Test
    fun `账目笔数按分类id取 没有账目的分类是0`() {
        val categories = listOf(category(1, 1))
        val rows = buildCategoryRows(categories, mapOf(1L to 7))

        assertEquals(7, rows.first().transactionCount)
        assertEquals(0, buildCategoryRows(categories, emptyMap()).first().transactionCount)
    }

    @Test
    fun `子分类的父分类停用了 子分类仍然渲染在原位置`() {
        // 父子的 isActive 是各自独立的字段，buildCategoryRows 本身不做任何过滤--
        // 按 isActive 分「启用中/已停用」两个 section 是调用方（CategoryListState）的职责，
        // 这里只负责给出正确的渲染顺序和范围信息
        val categories = listOf(
            category(1, 1, isActive = false),
            category(11, 1, parentId = 1L, isActive = true),
        )
        val rows = buildCategoryRows(categories, emptyMap())
        assertEquals(listOf(1L, 11L), rows.map { it.category.id })
    }
}
