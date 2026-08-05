import com.google.devtools.ksp.gradle.KspExtension
import com.myapp.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room 配置。
 * 关键：导出 schema JSON 到 schemas/ 目录并纳入版本控制——
 * 这是编写和验证数据库迁移的前提（禁止 fallbackToDestructiveMigration，
 * 因为本项目数据无云端备份，丢一次就没了）。
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<KspExtension> {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }

        dependencies {
            add("implementation", libs.findLibrary("androidx.room.runtime").get())
            add("implementation", libs.findLibrary("androidx.room.ktx").get())
            add("ksp", libs.findLibrary("androidx.room.compiler").get())
        }
    }
}
