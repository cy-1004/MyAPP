package com.myapp.core.network.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DeepSeek Responses API 的报文（PRD 3.14）。
 *
 * **为什么用 `/responses` 而不是 PRD 里写的「对话补全接口」**：内置的联网搜索
 * 是服务端执行的 built-in tool，只有 Responses API 支持；`/chat/completions` 的
 * `tools` 参数官方明确写着「目前只支持 function」，把 `{"type":"web_search"}` 塞进去
 * 会被当成畸形的 function 定义拒掉。用户要的联网搜索决定了接口选型。
 */
@Serializable
internal data class ResponsesRequest(
    val model: String,
    /** 系统指令。Responses API 用 `instructions` 承载，不是 messages 里的 system 角色。 */
    val instructions: String,
    val input: String,
    val tools: List<ToolSpec> = emptyList(),
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
)

@Serializable
internal data class ToolSpec(val type: String)

/**
 * 响应。字段全给默认值 + `ignoreUnknownKeys`：这是第三方接口，
 * 加字段是常态，少一个字段就整体解析失败会让功能莫名其妙地挂掉。
 */
@Serializable
internal data class ResponsesReply(
    val output: List<OutputItem> = emptyList(),
    val error: ApiError? = null,
)

@Serializable
internal data class OutputItem(
    val type: String = "",
    val content: List<ContentPart> = emptyList(),
)

@Serializable
internal data class ContentPart(
    val type: String = "",
    val text: String = "",
)

@Serializable
internal data class ApiError(
    val message: String = "",
    val type: String = "",
)
