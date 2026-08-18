plugins {
    alias(libs.plugins.myapp.android.feature)
}

android {
    namespace = "com.myapp.feature.feed"
}

dependencies {
    // 拉取 RSS/Atom XML 用 core:network 的 OkHttpClient（早就配好了，一直没人用）。
    implementation(projects.core.network)
    // 文章封面图
    implementation(libs.coil.compose)
    // 无正文时 Custom Tabs 打开原链接（PRD 3.9）
    implementation(libs.androidx.browser)
    // 文章列表分页（PRD 4.5）。PagingSource/Pager 由 :core:database 的 api 依赖带进来，
    // 这里只需要 Compose 侧的 collectAsLazyPagingItems
    implementation(libs.androidx.paging.compose)
}
