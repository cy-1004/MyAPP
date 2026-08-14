package com.myapp.feature.knowledge.interview

/**
 * 打包进 assets 的题库文档清单（PRD 3.7）。
 *
 * 加一篇新题库的完整步骤：
 * 1. 把 md 放到 `assets/interview/<key>/doc.md`，图片放到 `assets/interview/<key>/img/`；
 * 2. 在这里加一行；
 * 3. 把 [INTERVIEW_ASSETS_VERSION] 加一，触发下次启动重新导入。
 *
 * 目录名用 ASCII（`backend` / `llm`）而不是中文原名：
 * 源文件叫「火箭🚀.md」，文件名里的 emoji 在打包与跨平台构建里是不必要的风险，
 * 显示名单独放在 [displayName] 里，不跟目录名绑定。
 */
data class InterviewDoc(
    /** assets 子目录名，同时也是 `interview_chapter.doc_key`。 */
    val key: String,
    /** 界面上显示的文档名。 */
    val displayName: String,
)

val INTERVIEW_DOCS = listOf(
    InterviewDoc(key = "backend", displayName = "后端"),
    InterviewDoc(key = "llm", displayName = "大模型"),
)

/**
 * 题库资源版本。**改了 assets 里的 md 就要把这个数加一**，否则不会重新导入。
 *
 * 导入是幂等的（整篇替换 + 按 key 保留在池状态与复习进度），
 * 但每次启动都重新解析 44 万字没有意义，所以用版本号卡一道。
 */
const val INTERVIEW_ASSETS_VERSION = 2
