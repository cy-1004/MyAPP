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
    // 庆祝粒子（PRD 6.1）：待办清空/预算不超支/纪念日当天，包成 ConfettiOverlay 给各 feature 用，
    // 各 feature 不直接碰 konfetti 的 API，所以只需要 implementation
    implementation(libs.konfetti.compose)
    // 空态矢量动画（PRD 6.1）：接进 EmptyState 一处，全 App 的空态列表页跟着一起有，
    // 各 feature 不直接碰 lottie 的 API
    implementation(libs.lottie.compose)
}
