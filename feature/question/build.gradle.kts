// 一个 feature 模块的完整构建脚本就这么多--
// 所有配置都在 myapp.android.feature 约定插件里（PRD 4.7.8 的扩展性验收标准）。
plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.question"
}
