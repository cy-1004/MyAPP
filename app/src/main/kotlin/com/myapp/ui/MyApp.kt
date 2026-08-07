package com.myapp.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.myapp.core.ui.navigation.Route
import com.myapp.navigation.AppNavHost

@Composable
fun MyApp(initialRoute: Route) {
    val navController = rememberNavController()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // TODO 底部导航（PRD 3.11）：Haze 毛玻璃 + 滚动时自动隐藏/显示
        // TODO 全局 FAB：长按沿弧线展开「记笔记 / 记疑问 / 记一笔 / 加待办」
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
