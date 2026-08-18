package com.myapp.feature.settings.about

/**
 * 关于页的开源许可列表（PRD 3.12）。
 *
 * 手工维护而不是接 AboutLibraries：单人自用不上架应用商店，不需要那个库扫描 POM 生成的
 * 完整依赖树与许可证全文，只需要一份「用了什么、谁写的、什么协议」的说明。
 * 新增/升级依赖时如果引入了新的第三方库，记得在这里补一行——`gradle/libs.versions.toml`
 * 是版本的唯一来源，这里是「许可声明」的唯一来源，两者不是一回事。
 */
data class OpenSourceLicense(
    val name: String,
    val author: String,
    val license: String,
)

val openSourceLicenses = listOf(
    OpenSourceLicense(
        name = "Kotlin / kotlinx.coroutines / kotlinx.serialization",
        author = "JetBrains",
        license = "Apache License 2.0",
    ),
    OpenSourceLicense(
        name = "AndroidX / Jetpack（Core、Compose、Room、DataStore、Navigation、WorkManager、Glance 等）",
        author = "The Android Open Source Project",
        license = "Apache License 2.0",
    ),
    OpenSourceLicense(
        name = "Hilt / Dagger",
        author = "Google",
        license = "Apache License 2.0",
    ),
    OpenSourceLicense(
        name = "OkHttp / Retrofit",
        author = "Square, Inc.",
        license = "Apache License 2.0",
    ),
    OpenSourceLicense(
        name = "Coil",
        author = "Coil Contributors",
        license = "Apache License 2.0",
    ),
    OpenSourceLicense(
        name = "Haze",
        author = "Chris Banes",
        license = "Apache License 2.0",
    ),
    OpenSourceLicense(
        name = "Lottie for Android",
        author = "Airbnb",
        license = "Apache License 2.0",
    ),
    OpenSourceLicense(
        name = "Konfetti",
        author = "Daniel Velázquez",
        license = "Apache License 2.0",
    ),
)
