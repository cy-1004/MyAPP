plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.widget"
}

dependencies {
    // 小组件：Glance（Compose 风格的 AppWidget 框架，PRD 3.10）
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
