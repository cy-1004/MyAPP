package com.myapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.myapp.core.designsystem.theme.MyAppTheme
import com.myapp.ui.MyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动画面：Logo 缩放淡出直接过渡到首页，避免白屏跳变（PRD 6.2）
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // targetSdk 36 起 edge-to-edge 强制生效、无法 opt-out（PRD 9.2），
        // 所以每个页面都必须自行处理 WindowInsets。
        // 系统栏用透明 + 自动图标反色，跟随应用主题。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
        )

        setContent {
            MyAppTheme {
                MyApp()
            }
        }
    }

    private companion object {
        const val TRANSPARENT = 0
    }
}
