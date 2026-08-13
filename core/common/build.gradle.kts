plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.hilt)
}

android {
    namespace = "com.myapp.core.common"
}

dependencies {
    // WorkManager 的 Hilt 绑定放在这里统一提供：它没有 @Inject 构造函数，
    // 而多个 feature（:knowledge 正文提取、:settings 每日云备份）都要注入 WorkManager。
    // 若各自 @Provides 会造成 SingletonComponent 里的重复绑定，编译期直接失败。
    api(libs.androidx.work.runtime.ktx)
}
