plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.knowledge"
}

dependencies {
    // 正文提取用 WorkManager 一次性任务（充电 + WiFi 约束），@HiltWorker 需要单独的
    // androidx.hilt:hilt-compiler（跟 AndroidHiltConventionPlugin 里的 dagger hilt-compiler
    // 是两个不同的 KSP 处理器，都要有）。
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // 面试题正文里的配图从 assets 读（file:///android_asset/...），Coil 原生支持这个 scheme
    implementation(libs.coil.compose)
}
