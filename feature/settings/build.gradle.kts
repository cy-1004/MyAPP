plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.settings"
}

dependencies {
    // 云备份（PRD 3.13）：直连腾讯云开发的 HTTP 接口，复用 :core:network 的
    // OkHttpClient 与 Json 单例（两者在该模块里是 api 依赖，可传递拿到）。
    implementation(projects.core.network)

    // 每日一次的备份任务走 WorkManager 周期任务（WorkManager 本身由 :core:common 提供绑定）。
    // @HiltWorker 需要 androidx.hilt:hilt-compiler，跟 Hilt 约定插件里的 dagger hilt-compiler
    // 是两个不同的 KSP 处理器，都要有。
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
