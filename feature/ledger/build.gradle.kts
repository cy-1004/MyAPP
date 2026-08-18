// 一个 feature 模块的完整构建脚本就这么多--
// 所有配置都在 myapp.android.feature 约定插件里（PRD 4.7.8 的扩展性验收标准）。
plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.ledger"
}

dependencies {
    // 账目列表分页（PRD 4.5）。PagingSource/Pager 由 :core:database 的 api 依赖带进来，
    // 这里只需要 Compose 侧的 collectAsLazyPagingItems
    implementation(libs.androidx.paging.compose)
}
