plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.period"
}

dependencies {
    // AI 分析（PRD 3.14）：DeepSeek 客户端在 :core:network，
    // 这里只做业务编排（组 prompt、判峰谷、缓存结果）。
    implementation(projects.core.network)
}
