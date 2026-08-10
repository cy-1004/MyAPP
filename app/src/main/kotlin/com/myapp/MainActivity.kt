package com.myapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.keepalive.KeepAliveStatusChecker
import com.myapp.core.datastore.AppPreferences
import com.myapp.core.designsystem.theme.MyAppTheme
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.ledger.data.LedgerDeepLink
import com.myapp.feature.ledger.notification.LedgerNotifierExtras
import com.myapp.ui.MyApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var statusChecker: KeepAliveStatusChecker

    @Inject
    @ApplicationScope
    lateinit var appScope: kotlinx.coroutines.CoroutineScope

    @Inject
    lateinit var ledgerDeepLink: LedgerDeepLink

    /**
     * null = 未读取完，splash 挂住；
     * true = 已完成保活自检（或老用户升级），进 Home；
     * false = 首次安装未完成，进 KeepAliveCheck 向导。
     *
     * 用 mutableStateOf 让 setContent 里的 Composable 能响应状态变化--
     * 虽然实际渲染时 splash 已退出、值已确定，但用 State 更稳妥。
     */
    private var keepAliveChecked by mutableStateOf<Boolean?>(null)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也不阻塞--提醒仍会注册，只是不弹通知 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. installSplashScreen 必须在 super.onCreate 之前
        val splash = installSplashScreen()

        // 2. super.onCreate 后 Hilt 才填充 @Inject lateinit
        super.onCreate(savedInstanceState)

        // 通知点击拉起：把账目 id 写入深链，MyApp 收集后导航到确认页
        handleLedgerDeepLink(intent)

        // 3. 异步读 onboarding 标志；老用户升级直接写 true 跳过向导
        appScope.launch {
            try {
                val checked = appPreferences.keepAliveChecked.first()
                val effective = when {
                    !checked && !statusChecker.isFirstInstall() -> {
                        // 老用户升级到含向导的版本：免打扰，直接标记已完成
                        appPreferences.setKeepAliveChecked(true)
                        true
                    }
                    else -> checked
                }
                keepAliveChecked = effective
                // 非首启时在此请求通知权限；首启由 KeepAliveCheck 向导内请求，
                // 避免首启同时弹向导和权限框（PRD 9.3 + Plan 审查）
                if (effective) {
                    withContext(Dispatchers.Main.immediate) {
                        requestNotificationPermissionIfNeeded()
                    }
                }
            } catch (_: Throwable) {
                // 读取失败时兜底为已完成，避免 splash 永不退出
                keepAliveChecked = false
            }
        }

        // 4. splash 挂住直到 onboarding 状态读取完
        splash.setKeepOnScreenCondition { keepAliveChecked == null }

        // targetSdk 36 起 edge-to-edge 强制生效、无法 opt-out（PRD 9.2），
        // 所以每个页面都必须自行处理 WindowInsets。
        // 系统栏用透明 + 自动图标反色，跟随应用主题。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
        )

        setContent {
            MyAppTheme {
                val initialRoute = when (keepAliveChecked) {
                    true -> Route.Home
                    false, null -> Route.KeepAliveCheck
                }
                MyApp(initialRoute = initialRoute)
            }
        }
    }

    /**
     * 通知 PendingIntent 用 CLEAR_TOP|SINGLE_TOP 拉起 MainActivity：
     * 冷启动走 onCreate，已在前台走 onNewIntent，两个入口都要处理。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLedgerDeepLink(intent)
    }

    private fun handleLedgerDeepLink(intent: Intent?) {
        // getLongExtra 对 Integer extra 会抛 ClassCastException 静默返回默认值，
        // 深链是外部输入边界，手动按类型读（通知 PendingIntent 存 Long，adb --ei 是 Integer）
        val id = intent?.extras?.get(LedgerNotifierExtras.TRANSACTION_ID).let { raw ->
            when (raw) {
                is Long -> raw
                is Int -> raw.toLong()
                else -> return
            }
        }
        if (id > 0L) ledgerDeepLink.openTransaction(id)
    }

    /**
     * `POST_NOTIFICATIONS` 是运行时权限（API 33+）。不请求的话待办/纪念日/经期提醒
     * 闹钟照样触发，但通知不会显示--用户会以为提醒功能坏了。
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
