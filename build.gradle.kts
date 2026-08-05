// 根构建脚本：只声明插件，不做任何配置。
// 所有共享配置都在 build-logic 的约定插件里（见 build-logic/convention/），
// 这样新增一个 module 只需 apply 一个约定插件，不用复制粘贴配置。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
