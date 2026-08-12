package com.myapp.core.ui.home

import androidx.compose.runtime.Composable
import com.myapp.core.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeHomeCard(
    override val id: String,
    override val defaultOrder: Int,
    override val displayName: String = id,
) : HomeCard {
    @Composable
    override fun Content(onNavigate: (Route) -> Unit) = Unit
}

/**
 * 首页卡片排序/显隐配置的编解码与应用（PRD 4.7.2）。
 * 排序设置页与 HomeViewModel 都靠 [HomeCardConfig.applyOrder] 得到一致的展示顺序，
 * 所以边界情况（空配置、脏 JSON、新卡片）单独钉死。
 */
class HomeCardConfigTest {

    private val a = FakeHomeCard(id = "a", defaultOrder = 100)
    private val b = FakeHomeCard(id = "b", defaultOrder = 200)
    private val c = FakeHomeCard(id = "c", defaultOrder = 300)

    @Test
    fun applyOrder_emptyConfig_fallsBackToDefaultOrder() {
        val result = HomeCardConfig.applyOrder(listOf(c, a, b), raw = "")
        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun applyOrder_malformedJson_fallsBackToDefaultOrder() {
        val result = HomeCardConfig.applyOrder(listOf(c, a, b), raw = "not json")
        assertEquals(listOf(a, b, c), result)
    }

    @Test
    fun applyOrder_usesStoredOrder() {
        val raw = HomeCardConfig.encode(
            listOf(
                HomeCardConfigEntry(id = "c"),
                HomeCardConfigEntry(id = "a"),
                HomeCardConfigEntry(id = "b"),
            ),
        )
        val result = HomeCardConfig.applyOrder(listOf(a, b, c), raw)
        assertEquals(listOf(c, a, b), result)
    }

    @Test
    fun applyOrder_newCardNotInConfig_appendsByDefaultOrder() {
        // 版本升级新增了卡片 c，用户的旧配置里没有它——应追加在已知卡片之后，而不是丢失
        val raw = HomeCardConfig.encode(listOf(HomeCardConfigEntry(id = "b"), HomeCardConfigEntry(id = "a")))
        val result = HomeCardConfig.applyOrder(listOf(a, b, c), raw)
        assertEquals(listOf(b, a, c), result)
    }

    @Test
    fun isEnabled_defaultsToTrueWhenNotInConfig() {
        assertEquals(true, HomeCardConfig.isEnabled("a", raw = ""))
    }

    @Test
    fun isEnabled_respectsStoredFalse() {
        val raw = HomeCardConfig.encode(listOf(HomeCardConfigEntry(id = "a", enabled = false)))
        assertEquals(false, HomeCardConfig.isEnabled("a", raw))
        assertEquals(true, HomeCardConfig.isEnabled("b", raw))
    }
}
