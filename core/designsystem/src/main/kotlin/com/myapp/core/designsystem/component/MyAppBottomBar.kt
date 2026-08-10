package com.myapp.core.designsystem.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.myapp.core.designsystem.theme.LocalMotionLevel
import com.myapp.core.designsystem.theme.appColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

data class BottomBarItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

/**
 * 底部导航栏（PRD 3.11）。
 *
 * Haze 毛玻璃背景：底栏叠在内容之上，通过 [hazeState] 采样底层内容做实时模糊。
 * 动效降级（[LocalMotionLevel] 非 Full）时退回纯 Surface 色，避免低端机掉帧。
 *
 * 选中态：图标微弹（spring）+ primaryContainer 椭圆背景 + Accent 文字。
 * 未选中：textTertiary 灰。
 */
@Composable
fun MyAppBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val enableBlur = LocalMotionLevel.current.enableBlur

    val surfaceColor = MaterialTheme.colorScheme.surface
    val barModifier = if (enableBlur) {
        modifier.hazeEffect(
            hazeState,
            style = HazeStyle(
                backgroundColor = surfaceColor,
                blurRadius = 20.dp,
                tint = HazeTint(surfaceColor.copy(alpha = 0.6f)),
            ),
        )
    } else {
        modifier
    }

    NavigationBar(
        modifier = barModifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = if (enableBlur) Color.Transparent else MaterialTheme.colorScheme.surface,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "navIconScale",
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onItemClick(index) },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        modifier = Modifier.scale(scale),
                    )
                },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    unselectedIconColor = MaterialTheme.appColors.textTertiary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.appColors.textTertiary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}
