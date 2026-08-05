plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.compose)
    alias(libs.plugins.myapp.android.hilt)
}

android {
    namespace = "com.myapp.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
