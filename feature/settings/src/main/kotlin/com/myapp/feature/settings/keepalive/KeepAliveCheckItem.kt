package com.myapp.feature.settings.keepalive

/**
 * 保活自检项（PRD 9.3）。
 *
 * 分四类，UI 渲染方式不同：
 * - [Category.AUTO]：有 API 检测 + 有系统设置跳转（电池白名单、通知权限）
 * - [Category.READONLY]：有 API 检测但恒通过，只读展示（精确闹钟，USE_EXACT_ALARM 安装即授予）
 * - [Category.MANUAL]：无 API 检测，靠用户勾选确认（自启动、后台活动、锁定后台任务）
 * - [Category.TEXTONLY]：不检测不跳转，纯文字说明（通知使用权，功能未上线）
 */
data class KeepAliveCheckItem(
    val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val status: Status,
    /** 手动项的 ColorOS 路径提示。 */
    val pathHint: String? = null,
    /** 按钮/勾选框文字。 */
    val actionLabel: String? = null,
    /** 手动项用户是否已勾选确认。 */
    val manualDone: Boolean = false,
    /** 是否可尝试直达 ColorOS 私有设置页（仅手动项可能有）。 */
    val canTryDirectOpen: Boolean = false,
) {
    enum class Category { AUTO, READONLY, MANUAL, TEXTONLY }

    enum class Status {
        PASSED,
        NOT_PASSED,
        PENDING_MANUAL,
        INFO,
    }

    /** 是否已通过（用于判断「完成」按钮是否可点）。 */
    val isSatisfied: Boolean
        get() = when (category) {
            Category.AUTO, Category.READONLY -> status == Status.PASSED
            Category.MANUAL -> manualDone
            Category.TEXTONLY -> true
        }
}

/** 检测项 id 常量，ViewModel 用它决定跳转哪个系统设置。 */
internal object KeepAliveCheckIds {
    const val BATTERY = "battery"
    const val NOTIFICATION = "notification"
    const val EXACT_ALARM = "exact_alarm"
    const val NOTIFICATION_LISTENER = "notification_listener"
    const val AUTOSTART = "autostart"
    const val BACKGROUND = "background"
    const val LOCK_TASK = "lock_task"
}
