package com.myapp.feature.knowledge.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [reorderSelectors] 的边界测试，与 `KnowledgeRepositoryTest` 的 `reorderIds` 同一套口径。 */
class ExtractSelectorsTest {

    private val selectors = listOf(".a", ".b", ".c")

    @Test
    fun moveUp_swapsWithPrevious() {
        assertEquals(listOf(".b", ".a", ".c"), reorderSelectors(selectors, selector = ".b", delta = -1))
    }

    @Test
    fun moveDown_swapsWithNext() {
        assertEquals(listOf(".a", ".c", ".b"), reorderSelectors(selectors, selector = ".b", delta = 1))
    }

    @Test
    fun atFirst_moveUp_returnsNull() {
        assertNull(reorderSelectors(selectors, selector = ".a", delta = -1))
    }

    @Test
    fun atLast_moveDown_returnsNull() {
        assertNull(reorderSelectors(selectors, selector = ".c", delta = 1))
    }

    @Test
    fun unknownSelector_returnsNull() {
        assertNull(reorderSelectors(selectors, selector = ".zzz", delta = -1))
    }
}
