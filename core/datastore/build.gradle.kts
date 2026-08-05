plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.hilt)
}

android {
    namespace = "com.myapp.core.datastore"
}

dependencies {
    implementation(projects.core.common)
    api(libs.androidx.datastore.preferences)
}
