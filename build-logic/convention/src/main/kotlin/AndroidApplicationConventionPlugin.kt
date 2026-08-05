import com.android.build.api.dsl.ApplicationExtension
import com.myapp.configureKotlinAndroid
import com.myapp.libs
import com.myapp.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** :app 模块的基础配置。 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
}
