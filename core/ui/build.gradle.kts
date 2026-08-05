plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.compose)
    alias(libs.plugins.myapp.android.hilt)
    // Route 用 @Serializable 做类型安全导航，
    // 序列化器由这个编译器插件生成——只加注解不加插件会在运行时报「找不到序列化器」
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.myapp.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(libs.androidx.navigation.compose)
    api(libs.kotlinx.serialization.json)
}
