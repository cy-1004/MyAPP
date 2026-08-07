plugins {
    alias(libs.plugins.myapp.android.application)
    alias(libs.plugins.myapp.android.compose)
    alias(libs.plugins.myapp.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.myapp"

    defaultConfig {
        applicationId = "com.myapp"
        versionCode = 1
        versionName = "0.1.0"
    }

    // 项目内独立开发签名（交接文档：换电脑签名不一致导致无法覆盖安装）。
    // debug.keystore 提交到 git 仓库，所有机器用同一份，避免 ~/.android/debug.keystore 漂移。
    // 日常自用不上架，用 debug keystore 签 release 也无妨。
    signingConfigs {
        create("dev") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 日常自用建议直接跑 release 包：R8 优化后的性能才是真实体验，
            // 且 realme UI 对 debug 包有额外的安装/权限拦截（PRD 9.6）。
            // 用项目内 dev keystore 签名后即可长期覆盖安装。
            signingConfig = signingConfigs.getByName("dev")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}

dependencies {
    // ---- core ----
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)

    // ---- feature：新增 feature 时在这里加一行，同时在 AppNavHost 注册导航图 ----
    implementation(projects.feature.home)
    implementation(projects.feature.todo)
    implementation(projects.feature.anniversary)
    implementation(projects.feature.period)
    implementation(projects.feature.settings)

    // ---- AndroidX ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // ---- 后台任务 ----
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
