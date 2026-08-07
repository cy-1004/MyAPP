// 一个 feature 模块的完整构建脚本就这么多--
// 所有配置都在 myapp.android.feature 约定插件里（PRD 4.7.8 的扩展性验收标准）。
plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.note"
}

dependencies {
    // 笔记列表的缩略图与编辑页的图片预览需要 Coil；
    // feature 约定插件未自动加，本模块单独引入。
    implementation(libs.coil.compose)
}
