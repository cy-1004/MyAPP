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

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
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
            // 用固定 keystore 签名后即可长期覆盖安装。
            signingConfig = signingConfigs.getByName("debug")
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
