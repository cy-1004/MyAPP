import com.android.build.gradle.LibraryExtension
import com.myapp.configureKotlinAndroid
import com.myapp.libs
import com.myapp.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * 所有 :core / :feature 库模块的基础配置。
 * 用法：plugins { alias(libs.plugins.myapp.android.library) }
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            // 库模块不需要 BuildConfig，减少无谓的编译产物。
            // 也不设 targetSdk —— AGP 8 起库模块的 defaultConfig.targetSdk 已废弃，
            // 由最终打包的 :app 统一决定。
            buildFeatures.buildConfig = false
        }

        dependencies {
            add("implementation", libs.findLibrary("androidx.core.ktx").get())
            add("implementation", libs.findLibrary("kotlinx.coroutines.android").get())
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
        }
    }
}
