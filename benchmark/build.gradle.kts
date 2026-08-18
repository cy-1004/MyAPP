// Macrobenchmark + Baseline Profile 生成（PRD 4.5 / 6.4）。
//
// 这个模块**不进包**：`com.android.test` 产出的是一个独立的测试 APK，
// 由它去驱动 :app 这个「被测应用」。:app 不依赖它，发布产物里也不会有它。
//
// 不套项目自己的 myapp.android.* 约定插件：那几个插件是给「进包的模块」准备的
// （Compose、Hilt、Room 一整套），测试模块一样都用不上，套上只会拖慢构建。
// 所以这里的 compileSdk/minSdk 是手写的，改全局 SDK 版本时记得也看一眼这里。
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.myapp.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    defaultConfig {
        // Macrobenchmark 要求 minSdk >= 23；项目本身是 35，直接跟随
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 被测应用
    targetProjectPath = ":app"

    // self-instrumenting：测试 APK 与被测 App 跑在**两个进程**里。
    // 必须开——否则测试进程和被测进程混在一起，测出来的启动耗时是脏的。
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

// 不要在这里写 `beforeVariants { it.enable = it.buildType == "release" }`。
// androidx.baselineprofile 插件会**自动派生**两个变体：
//   nonMinifiedRelease —— 不混淆的 release，用来采 profile（混淆过的名字采了也没用）
//   benchmarkRelease   —— 跑基准测试用的 release
// 按 buildType == "release" 过滤会把这两个一起禁掉，
// 表现是构建成功但提示「No baseline profile rules were generated」，一开始就踩了这个坑。
