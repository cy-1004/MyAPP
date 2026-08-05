plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.myapp.core.network"
}

dependencies {
    implementation(projects.core.common)
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)
}
