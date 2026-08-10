pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyAPP"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// ---- 组装层 ----
include(":app")

// ---- 基础设施层：不含业务逻辑，可被任意 feature 依赖 ----
include(":core:common")        // 时间/农历/协程扩展、Result 封装
include(":core:designsystem")  // 色彩、字体、形状、MotionTokens、通用组件
include(":core:database")      // Room 实例、实体、DAO、迁移
include(":core:datastore")     // 偏好设置、功能开关
include(":core:network")       // OkHttp/Retrofit 基建
include(":core:ui")            // 导航契约 Route、首页卡片插槽 HomeCard

// ---- 业务层：feature 之间互不依赖，只依赖 core ----
include(":feature:home")       // 首页：只做卡片编排，不认识具体业务
include(":feature:todo")       // 待办（P0，同时作为新增模块的参考实现）
include(":feature:anniversary")// 纪念日（P0，含农历）
include(":feature:period")     // 经期（P0）

// ---- 以下模块按 PRD 交付计划逐期加入，取消注释即可 ----
include(":feature:note")          // 笔记            P0
include(":feature:question")      // 疑问            P0
include(":feature:ledger")        // 记账 + 预算      P1
// include(":feature:widget")        // 桌面小组件       P1
// include(":feature:knowledge")     // 飞书公开页/知识点 P1/P2
// include(":feature:feed")          // RSS 资讯        P2
include(":feature:settings")      // 设置 + 保活自检   P1
