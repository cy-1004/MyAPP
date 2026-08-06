import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.myapp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Compose 依赖与编译器配置。
 * 所有要写 UI 的模块 apply 这个，依赖清单只维护这一份。
 *
 * 必须在 myapp.android.application / myapp.android.library 之后 apply，
 * 否则拿不到 Android 扩展。
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // 同时兼容 application / library 两种模块
        val androidExtension: CommonExtension<*, *, *, *, *, *> =
            extensions.findByType(ApplicationExtension::class.java)
                ?: extensions.findByType(LibraryExtension::class.java)
                ?: error(
                    "myapp.android.compose 需要先 apply myapp.android.application " +
                        "或 myapp.android.library",
                )

        androidExtension.buildFeatures.compose = true

        dependencies {
            val bom = libs.findLibrary("androidx.compose.bom").get()
            // BOM 同时加到 api，保证下游模块引用 Compose 构件时也能省略版本号
            add("api", platform(bom))
            add("androidTestImplementation", platform(bom))

            add("implementation", libs.findLibrary("androidx.compose.ui").get())
            add("implementation", libs.findLibrary("androidx.compose.ui.graphics").get())
            add("implementation", libs.findLibrary("androidx.compose.ui.tooling.preview").get())
            add("implementation", libs.findLibrary("androidx.compose.foundation").get())
            add("implementation", libs.findLibrary("androidx.compose.animation").get())
            add("implementation", libs.findLibrary("androidx.compose.material3").get())
            // 图标全集：R8 会把没用到的资源剃掉，release 包不受影响；
            // 统一在这里加，省得每个模块用到一个新图标就改一次构建脚本
            add("implementation", libs.findLibrary("androidx.compose.material.icons.extended").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.runtime.compose").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.viewmodel.compose").get())

            add("debugImplementation", libs.findLibrary("androidx.compose.ui.tooling").get())
            add("debugImplementation", libs.findLibrary("androidx.compose.ui.test.manifest").get())
            add("androidTestImplementation", libs.findLibrary("androidx.compose.ui.test.junit4").get())
        }
    }
}
