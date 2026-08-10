package com.myapp.feature.ledger.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.ui.graphics.Color
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
    else -> Icons.AutoMirrored.Outlined.HelpOutline
}

/**
 * 分类颜色 key -> Color 映射（PRD 5.1 莫兰迪色板）。
 *
 * 与 Accent 同色温，不用彩虹色。按稳定 key 取色保证同一分类每次颜色一致。
 * 深浅色模式暂用同一色，Phase 2 可改为 darkColorLighter 变体。
 */
fun categoryColor(colorKey: String): Color = when (colorKey) {
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
