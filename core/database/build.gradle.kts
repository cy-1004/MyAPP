plugins {
    alias(libs.plugins.myapp.android.library)
    alias(libs.plugins.myapp.android.hilt)
    alias(libs.plugins.myapp.android.room)
}

android {
    namespace = "com.myapp.core.database"

    // Robolectric 需要读 manifest 与资源，否则 MigrationTestHelper 拿不到 Context
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    // 把 schemas/ 目录追加到 test assets 根，MigrationTestHelper 才能在
    // assets 里读到 schemas/com.myapp.core.database.MyAppDatabase/<v>.json
    // （KSP 把 schema JSON 输出到 $projectDir/schemas，见 AndroidRoomConventionPlugin）
    sourceSets {
        getByName("test") {
            assets.srcDir(file("schemas"))
        }
    }
}

dependencies {
    implementation(projects.core.common)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
