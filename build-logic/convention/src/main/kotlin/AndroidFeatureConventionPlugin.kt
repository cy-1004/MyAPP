import com.myapp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * feature 模块的一站式配置：library + compose + hilt + 统一的 core 依赖。
 *
 * 这是「拓展性」的核心落点——新增一个 feature 只需：
 *   1. settings.gradle.kts 里 include 一行
 *   2. build.gradle.kts 里写 `plugins { alias(libs.plugins.myapp.android.feature) }` + namespace
 * 就能拿到全套能力，不用复制粘贴任何配置。
 *
 * 注意：这里只允许依赖 :core:*，**不允许依赖任何 :feature:***。
 * feature 之间的协作一律通过 :core:ui / :core:common 中定义的接口 + Hilt 注入完成。
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("myapp.android.library")
        pluginManager.apply("myapp.android.compose")
        pluginManager.apply("myapp.android.hilt")

        dependencies {
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))
            add("implementation", project(":core:database"))
            add("implementation", project(":core:datastore"))

            add("implementation", libs.findLibrary("androidx.hilt.navigation.compose").get())
            add("implementation", libs.findLibrary("androidx.navigation.compose").get())
            add("implementation", libs.findLibrary("androidx.lifecycle.runtime.ktx").get())
        }
    }
}
