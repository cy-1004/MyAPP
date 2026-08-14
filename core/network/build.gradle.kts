plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.myapp.core.network"
}

dependencies {
    // api 而不是 implementation：DeepSeekClient 的构造参数里有 :core:common 的
    // SecretStore，Hilt 的组件代码是在 :app 里生成的，那边必须能看见这个类型。
    api(projects.core.common)
    api(libs.okhttp)
    api(libs.retrofit)
    api(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit.kotlinx.serialization)
}
