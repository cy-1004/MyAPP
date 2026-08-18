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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.myapp.core.common.contract.KnowledgeItemKind
import com.myapp.core.common.di.ApplicationScope
import com.myapp.core.common.keepalive.KeepAliveStatusChecker
import com.myapp.core.datastore.AppPreferences
import com.myapp.core.designsystem.theme.MotionLevel
import com.myapp.core.designsystem.theme.MyAppTheme
import com.myapp.core.designsystem.theme.rememberSystemMotionLevel
import com.myapp.core.ui.navigation.Route
import com.myapp.feature.knowledge.data.KnowledgeDailyDestination
import com.myapp.feature.knowledge.data.KnowledgeDailyTarget
import com.myapp.feature.knowledge.data.KnowledgeShareTarget
import com.myapp.feature.knowledge.notify.KnowledgeDailyReceiver
import com.myapp.feature.knowledge.notify.KnowledgeNotifierExtras
import com.myapp.feature.settings.backup.CloudBackupScheduler
import com.myapp.feature.ledger.data.BudgetAlertTarget
import com.myapp.feature.ledger.data.LedgerDeepLink
import com.myapp.feature.ledger.notification.BudgetAlertNotifierExtras
import com.myapp.feature.ledger.notification.LedgerNotifierExtras
import com.myapp.feature.note.notify.NoteQuickEntryExtras
import com.myapp.feature.note.notify.NoteQuickEntryNotifier
import com.myapp.feature.note.notify.NoteQuickEntryTarget
import com.myapp.feature.widget.WidgetIntents
import com.myapp.feature.widget.data.WidgetNavTarget
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
    lateinit var cloudBackupScheduler: CloudBackupScheduler

    @Inject
    lateinit var ledgerDeepLink: LedgerDeepLink

    @Inject
    lateinit var budgetAlertTarget: BudgetAlertTarget

    @Inject
    lateinit var widgetNavTarget: WidgetNavTarget

    @Inject
    lateinit var knowledgeShareTarget: KnowledgeShareTarget

    @Inject
    lateinit var knowledgeDailyTarget: KnowledgeDailyTarget

    @Inject
    lateinit var noteQuickEntryTarget: NoteQuickEntryTarget

    @Inject
    lateinit var noteQuickEntryNotifier: NoteQuickEntryNotifier

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
        // 小组件点击拉起：目标页面写入 WidgetNavTarget，MyApp 收集后导航
        handleWidgetIntent(intent)
        // 系统分享菜单「分享到 MyAPP」：链接写入 KnowledgeShareTarget，MyApp 收集后导航到知识源新建页
        handleKnowledgeShareIntent(intent)
        // 每日知识点通知点击：目标写入 KnowledgeDailyTarget，MyApp 收集后导航到阅读页/笔记详情
        handleKnowledgeDailyIntent(intent)
        // 预算预警通知点击：写入 BudgetAlertTarget，MyApp 收集后导航到预算页
        handleBudgetAlertIntent(intent)
        // 通知栏常驻快捷入口点击：写入 NoteQuickEntryTarget，MyApp 收集后导航到新建笔记页
        handleNoteQuickEntryIntent(intent)

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

        // 云备份补偿（PRD 3.13）：ColorOS 会冻结后台，WorkManager 周期任务不保证会跑，
        // 所以每次启动检查一次「距上次成功备份是否超过一天」，超了就立刻补一次。
        // 只要用户偶尔打开 App，每日备份就不会因为系统吃掉周期任务而长期中断。
        appScope.launch {
            runCatching {
                if (appPreferences.cloudBackupEnabled.first()) {
                    cloudBackupScheduler.ensureDailyBackup(
                        lastSuccessAt = appPreferences.cloudBackupLastSuccessAt.first(),
                        now = System.currentTimeMillis(),
                    )
                }
            }
        }

        // 每日知识点推送自愈（PRD 3.8）：ColorOS 一键清理/强停会静默清掉 AlarmManager 注册，
        // 而链条只在「开机、触发、开关切换」三处重排，被清一次就永久失效
        // （2026-08-17 真机实测：开关开着但闹钟已不在 AlarmManager 里）。
        // 每次启动重排一次；scheduleNext 覆盖同一个 PendingIntent，幂等。
        appScope.launch {
            runCatching {
                if (appPreferences.knowledgeDailyPushEnabled.first()) {
                    KnowledgeDailyReceiver.scheduleNext(this@MainActivity)
                }
            }
        }

        // 笔记通知栏常驻快捷入口（PRD 3.4）：开关存在 DataStore，挂/撤在这里接线——
        // :feature:settings 不能直接调 :feature:note 的通知器（feature 之间不许互相依赖）。
        // 用 lifecycleScope 而不是 appScope：跟着 Activity 走，重建时自动重订，不会越攒越多。
        // 每次启动都会重放一次当前值，所以用户手动划掉通知后，下次打开 App 它会自己回来。
        lifecycleScope.launch {
            runCatching {
                appPreferences.noteQuickEntryEnabled.collect { enabled ->
                    if (enabled) noteQuickEntryNotifier.show() else noteQuickEntryNotifier.cancel()
                }
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
            val themeMode by appPreferences.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val motionLevelPref by appPreferences.motionLevel.collectAsStateWithLifecycle(initialValue = "full")
            val dynamicColorEnabled by appPreferences.dynamicColorEnabled.collectAsStateWithLifecycle(initialValue = false)

            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val userMotionLevel = when (motionLevelPref) {
                "reduced" -> MotionLevel.Reduced
                "none" -> MotionLevel.None
                else -> MotionLevel.Full
            }

            MyAppTheme(
                darkTheme = darkTheme,
                dynamicColor = dynamicColorEnabled,
                motionLevel = rememberSystemMotionLevel(userMotionLevel),
            ) {
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
        handleWidgetIntent(intent)
        handleKnowledgeShareIntent(intent)
        handleKnowledgeDailyIntent(intent)
        handleBudgetAlertIntent(intent)
        handleNoteQuickEntryIntent(intent)
    }

    /** 通知栏常驻快捷入口（PRD 3.4）点击拉起：没有参数，认出 extra 就够了。 */
    private fun handleNoteQuickEntryIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(NoteQuickEntryExtras.OPEN_NEW_NOTE, false) != true) return
        noteQuickEntryTarget.open()
    }

    private fun handleBudgetAlertIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(BudgetAlertNotifierExtras.OPEN_BUDGET, false) != true) return
        budgetAlertTarget.open()
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

    /** 小组件点击的目标页面写入 WidgetNavTarget（冷启动 onCreate / 已在前台 onNewIntent 都要处理）。 */
    private fun handleWidgetIntent(intent: Intent?) {
        val screen = intent?.getStringExtra(WidgetIntents.EXTRA_SCREEN) ?: return
        widgetNavTarget.open(screen)
    }

    /**
     * 系统分享菜单「分享到 MyAPP」拉起（浏览器/飞书分享文本链接）：
     * `ACTION_SEND` + `text/plain` 只带 `EXTRA_TEXT`，直接当 URL 预填知识源新建页，
     * 不做域名校验——编辑页本身对 URL 就是宽松处理（同知识源编辑约定）。
     */
    private fun handleKnowledgeShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim() ?: return
        if (sharedText.isBlank()) return
        knowledgeShareTarget.share(sharedText)
    }

    /** 每日知识点通知点击拉起：id 是知识源还是笔记，看 [KnowledgeNotifierExtras.IS_NOTE]（PRD 3.8）。 */
    private fun handleKnowledgeDailyIntent(intent: Intent?) {
        if (intent?.extras?.containsKey(KnowledgeNotifierExtras.SOURCE_ID) != true) return
        val id = intent?.extras?.get(KnowledgeNotifierExtras.SOURCE_ID).let { raw ->
            when (raw) {
                is Long -> raw
                is Int -> raw.toLong()
                else -> return
            }
        }
        if (id <= 0L) return
        // KIND 是改版后加的；老通知没有这个 extra，退回按 IS_NOTE 判断（那时只有源/笔记两种）
        val kind = intent?.getStringExtra(KnowledgeNotifierExtras.KIND)
        val isNote = intent?.getBooleanExtra(KnowledgeNotifierExtras.IS_NOTE, false) ?: false
        val destination = when {
            kind == KnowledgeItemKind.INTERVIEW_QUESTION.name -> KnowledgeDailyDestination.Question(id)
            kind == KnowledgeItemKind.NOTE_FALLBACK.name || isNote -> KnowledgeDailyDestination.Note(id)
            else -> KnowledgeDailyDestination.Reader(id)
        }
        knowledgeDailyTarget.open(destination)
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
