package com.myapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.myapp.core.designsystem.theme.MyAppTheme
import com.myapp.ui.MyApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不阻塞——提醒仍会注册，只是不弹通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 启动画面：Logo 缩放淡出直接过渡到首页，避免白屏跳变（PRD 6.2）
        installSplashScreen()

        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

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

    /**
     * `POST_NOTIFICATIONS` 是运行时权限（API 33+）。不请求的话待办/纪念日/经期提醒
     * 闹钟照样触发，但通知不会显示——用户会以为提醒功能坏了。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val TRANSPARENT = 0
    }
}
