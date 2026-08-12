package com.myapp.feature.settings.about

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
)

/** 关于页 ViewModel（PRD 3.12）：应用版本信息，从系统 PackageManager 读取。 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val versionInfo: AppVersionInfo = runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        AppVersionInfo(
            versionName = packageInfo.versionName ?: "unknown",
            versionCode = versionCode,
            packageName = context.packageName,
        )
    }.getOrElse {
        AppVersionInfo(versionName = "unknown", versionCode = 0L, packageName = context.packageName)
    }
}
