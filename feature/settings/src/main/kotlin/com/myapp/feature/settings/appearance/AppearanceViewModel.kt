package com.myapp.feature.settings.appearance

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapp.core.datastore.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 外观设置页 ViewModel（PRD 3.12）：主题模式 / 动态取色 / 动效强度。 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val themeMode: StateFlow<String> = appPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "system")

    val motionLevel: StateFlow<String> = appPreferences.motionLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "full")

    val dynamicColorEnabled: StateFlow<Boolean> = appPreferences.dynamicColorEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * 系统「移除动画」无障碍设置开启时（动画缩放为 0），动效强度锁定为「关闭」，
     * 用户在设置页也不该能手动改回来——跟 [com.myapp.core.designsystem.theme.rememberSystemMotionLevel] 同一条规则（PRD 6.4）。
     */
    val motionLevelLockedByAccessibility: Boolean
        get() = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

    fun setThemeMode(value: String) {
        viewModelScope.launch { appPreferences.setThemeMode(value) }
    }

    fun setMotionLevel(value: String) {
        if (motionLevelLockedByAccessibility) return
        viewModelScope.launch { appPreferences.setMotionLevel(value) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setDynamicColorEnabled(enabled) }
    }
}
