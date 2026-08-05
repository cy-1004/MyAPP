plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.hilt)
    alias(libs.plugins.myapp.android.room)
}

android {
    namespace = "com.myapp.core.database"
}

dependencies {
    implementation(projects.core.common)
}
