package com.ueiol.yixue.domain.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
    // 附加图片（data URL），仅 user 消息使用；编码到 API 消息时转为多模态数组
    val images: List<String> = emptyList()
)

// ---------- API 请求消息（多模态 + 工具调用） ----------

@Serializable
data class ApiContentPart(
    val type: String,
    val text: String? = null,
    @SerialName("image_url") val imageUrl: ApiImageUrl? = null
)

@Serializable
data class ApiImageUrl(val url: String)

@Serializable
data class ApiToolCall(
    val id: String,
    // 必须始终序列化：部分平台严格要求该字段
    @EncodeDefault
    val type: String = "function",
    val function: ApiFuncCall
)

@Serializable
data class ApiFuncCall(val name: String, val arguments: String)

@Serializable
data class ApiMessage(
    val role: String,
    val content: JsonElement,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ApiToolCall>? = null
)

/** 工具定义（Agent function calling）。 */
@Serializable
data class ToolDef(
    @EncodeDefault
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement
)

/** 对话消息转 API 消息：有图片时 content 为多模态数组。 */
fun ChatMessage.toApiMessage(): ApiMessage {
    val contentEl: JsonElement = if (images.isEmpty()) {
        JsonPrimitive(content)
    } else {
        buildJsonArray {
            add(buildJsonObject {
                put("type", "text")
                put("text", content)
            })
            images.forEach { url ->
                add(buildJsonObject {
                    put("type", "image_url")
                    put("image_url", buildJsonObject { put("url", url) })
                })
            }
        }
    }
    return ApiMessage(role = role, content = contentEl)
}

fun assistantToolCallMessage(toolCalls: List<ApiToolCall>): ApiMessage =
    ApiMessage(role = "assistant", content = JsonNull, toolCalls = toolCalls)

fun toolResultMessage(callId: String, result: String): ApiMessage =
    ApiMessage(role = "tool", content = JsonPrimitive(result), toolCallId = callId)
@Serializable
data class ResponseFormat(@EncodeDefault val type: String = "json_object")

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    @SerialName("response_format") val responseFormat: ResponseFormat? = null,
    val tools: List<ToolDef>? = null
)

@Serializable
data class ChatChunk(
    val choices: List<StreamChoice> = emptyList()
)

@Serializable
data class StreamChoice(
    val delta: Delta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class Delta(
    val content: String? = null,
    @SerialName("tool_calls") val toolCalls: List<DeltaToolCall>? = null
)

@Serializable
data class DeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val function: DeltaFunc? = null
)

@Serializable
data class DeltaFunc(val name: String? = null, val arguments: String? = null)

/** 流式结果：文本 + 聚合后的工具调用。 */
data class StreamResult(
    val content: String,
    val toolCalls: List<AccToolCall>
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}

data class AccToolCall(val id: String, val name: String, val arguments: String)

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    isLenient = true
}

/** 共享的字符串列表序列化器。 */
val StringListSerializer: kotlinx.serialization.KSerializer<List<String>> =
    ListSerializer(String.serializer())