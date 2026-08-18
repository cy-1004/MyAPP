package com.myapp.feature.ledger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.myapp.core.designsystem.theme.appColors
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 分类图标 key -> ImageVector 映射。
 *
 * key 与 `core/database/src/main/assets/categories.json` 的 `icon` 字段一一对应。
 * 加新分类时在 categories.json 加条目，**这里也要加映射**，否则 fallback 到 HelpOutline。
 */
fun categoryIcon(iconKey: String): ImageVector = when (iconKey) {
    "food" -> Icons.Outlined.Restaurant
    "transport" -> Icons.Outlined.DirectionsCar
    "shopping" -> Icons.Outlined.ShoppingBag
    "entertainment" -> Icons.Outlined.SportsEsports
    "housing" -> Icons.Outlined.Home
    "medical" -> Icons.Outlined.LocalHospital
    "gift" -> Icons.Outlined.CardGiftcard
    "study" -> Icons.Outlined.School
    "other" -> Icons.Outlined.Category
    "uncategorized" -> Icons.AutoMirrored.Outlined.HelpOutline
    // 以下是分类管理页（Phase 3）给自建分类用的，种子里没有
    "coffee" -> Icons.Outlined.LocalCafe
    "travel" -> Icons.Outlined.Flight
    "fitness" -> Icons.Outlined.FitnessCenter
    "pet" -> Icons.Outlined.Pets
    "clothes" -> Icons.Outlined.Checkroom
    "phone" -> Icons.Outlined.PhoneIphone
    "salary" -> Icons.Outlined.Payments
    "invest" -> Icons.AutoMirrored.Outlined.TrendingUp
    "child" -> Icons.Outlined.ChildCare
    "beauty" -> Icons.Outlined.ContentCut
    else -> Icons.AutoMirrored.Outlined.HelpOutline
}

/**
 * 分类编辑页图标选择器的候选 key，顺序即展示顺序。
 *
 * 不含 `uncategorized`：那是「未分类」保留项专用的语义图标，
 * 让用户给自建分类选它只会造成混淆。
 */
val selectableCategoryIcons: List<String> = listOf(
    "food", "coffee", "transport", "travel", "shopping", "clothes",
    "entertainment", "fitness", "housing", "phone", "medical", "beauty",
    "gift", "child", "pet", "study", "salary", "invest", "other",
)

/** 分类编辑页颜色选择器的候选 key，与 [categoryColor] 的映射表一一对应。 */
val selectableCategoryColors: List<String> = listOf(
    "clay", "mistBlue", "mustard", "lotus", "pineGreen",
    "ochre", "olive", "taupe", "neutralGray", "lightGray",
)

/**
 * 分类颜色 key -> Color 映射（PRD 5.1 莫兰迪色板）。
 *
 * 与 Accent 同色温，不用彩虹色。按稳定 key 取色保证同一分类每次颜色一致。
 *
 * **深浅色两套色值**（2026-08-18 补，此前是「暂用同一色」）：
 * 浅色那套是为暖米白背景（`Base` #FAF9F5）挑的中低明度莫兰迪色，
 * 直接搬到深色底（`SurfaceDark` #262624）上明度不够，真机上「餐饮」的褐色图标
 * 配同色系的圆底几乎糊成一团。深色变体保持同一色相、只把明度提上去，
 * 不提饱和度--提饱和就变成彩虹色，那是 PRD 5.1 明确要避免的廉价感来源。
 *
 * 判断依据是 [com.myapp.core.designsystem.theme.AppColors.isDark] 而**不是
 * `isSystemInDarkTheme()`**：用户可以在设置里强制浅色/深色，跟系统设置未必一致。
 */
@Composable
fun categoryColor(colorKey: String): Color =
    if (MaterialTheme.appColors.isDark) darkCategoryColor(colorKey) else lightCategoryColor(colorKey)

private fun lightCategoryColor(colorKey: String): Color = when (colorKey) {
    "clay" -> Color(0xFFB08968)          // 陶土
    "mistBlue" -> Color(0xFF7B9EA8)      // 雾蓝
    "mustard" -> Color(0xFFB5A03A)       // 芥末
    "lotus" -> Color(0xFFA98CA6)         // 藕荷
    "pineGreen" -> Color(0xFF6B8E7F)     // 松绿
    "ochre" -> Color(0xFFC97B5C)         // 赭石
    "olive" -> Color(0xFF8B9D6B)         // 橄榄
    "taupe" -> Color(0xFF9B8B7E)         // 灰褐
    "neutralGray" -> Color(0xFFA8A29E)   // 中性灰
    "lightGray" -> Color(0xFFC9C4BC)     // 浅灰（未分类）
    else -> Color(0xFFA8A29E)
}

/** 深色变体：同色相、明度提高约 20%，饱和度不动。 */
private fun darkCategoryColor(colorKey: String): Color = when (colorKey) {
    "clay" -> Color(0xFFCBA285)
    "mistBlue" -> Color(0xFF9CBDC7)
    "mustard" -> Color(0xFFD2BC5C)
    "lotus" -> Color(0xFFC6A9C3)
    "pineGreen" -> Color(0xFF8FB2A2)
    "ochre" -> Color(0xFFE0977A)
    "olive" -> Color(0xFFACBE8C)
    "taupe" -> Color(0xFFB9A99C)
    "neutralGray" -> Color(0xFFC2BCB8)
    "lightGray" -> Color(0xFFDCD7D0)
    else -> Color(0xFFC2BCB8)
}

/**
 * 分类图标圆底的填充色。
 *
 * **透明度必须分明暗**：浅色底上 15% 的着色叠在近白背景上是柔和的粉彩圈；
 * 同样 15% 叠在 #262624 上几乎等于没有，圆圈直接消失。深色下提到 24%。
 *
 * 这个值原本散落在两个调用点各写一遍（0.15f / 0.16f），顺手收敛到这里，
 * 免得以后只改一处。
 */
@Composable
fun categoryContainerColor(colorKey: String): Color {
    val base = categoryColor(colorKey)
    return base.copy(alpha = if (MaterialTheme.appColors.isDark) 0.24f else 0.15f)
}
