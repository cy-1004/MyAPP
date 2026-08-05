plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.compose)
}

android {
    namespace = "com.myapp.core.designsystem"
}

dependencies {
    // 图标以 api 暴露给各 feature，避免每个 feature 重复声明
    api(libs.androidx.compose.material.icons.extended)
    implementation(projects.core.common)
    // 毛玻璃：底部导航、悬浮工具栏、FAB 展开时的背景（PRD 6.1）
    api(libs.haze)
    api(libs.haze.materials)
}
