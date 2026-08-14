package com.myapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.myapp.core.common.di.ApplicationScope
import com.myapp.feature.knowledge.interview.InterviewImporter
import com.myapp.feature.ledger.notification.PaymentNotificationListener
import com.myapp.feature.widget.WidgetAlarmReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var interviewImporter: InterviewImporter

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    /**
     * WorkManager 走 Hilt 注入，这样 Worker 里可以直接拿 Repository。
     *
     * 提醒：**只有可延迟的任务（RSS 拉取、知识池刷新、数据清理）才用 WorkManager**。
     * 定时提醒必须用 AlarmManager 的 setExactAndAllowWhileIdle——
     * ColorOS 会冻结后台，WorkManager 的周期任务在这台机器上不可靠（PRD 9.3）。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // 进程启动时排跨天闹钟。之前只在 BootCompletedReceiver 和 onReceive 自续期排，
        // 首次安装且未重启手机时闹钟永远不会被排（PRD 3.10 兜底链断裂）。
        // 幂等：覆盖同一个 PendingIntent，重复调用无副作用。
        WidgetAlarmReceiver.scheduleMidnightAlarm(this)
        // 覆盖安装会断开通知监听绑定且 ColorOS 不自动重连，自动记账因此静默失效。
        // 每次进程启动请求一次重绑，幂等（PRD 9.3）。
        PaymentNotificationListener.ensureBound(this)
        // 首次安装 / 题库资源升版时把 assets 里的 md 解析入库（PRD 3.7）。
        // 版本号没变时 importIfNeeded 立即返回，不会每次冷启动都解析 44 万字。
        appScope.launch { interviewImporter.importIfNeeded() }
    }
}
