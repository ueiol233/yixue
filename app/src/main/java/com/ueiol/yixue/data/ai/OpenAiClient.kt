package com.ueiol.yixue.data.ai

import com.ueiol.yixue.data.settings.SettingsStore
import com.ueiol.yixue.domain.model.AccToolCall
import com.ueiol.yixue.domain.model.ApiMessage
import com.ueiol.yixue.domain.model.AppJson
import com.ueiol.yixue.domain.model.ChatChunk
import com.ueiol.yixue.domain.model.ChatMessage
import com.ueiol.yixue.domain.model.ChatRequest
import com.ueiol.yixue.domain.model.ResponseFormat
import com.ueiol.yixue.domain.model.StreamResult
import com.ueiol.yixue.domain.model.ToolDef
import com.ueiol.yixue.domain.model.toApiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容客户端：SSE 流式 + 工具调用（Agent function calling）+ 多模态。
 * 错误策略（对照规划书）：请求失败仅重试一次交给上层决定；网络层不无限重试。
 */
class OpenAiClient(private val settings: SettingsStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun buildRequest(
        messages: List<ApiMessage>,
        stream: Boolean,
        temperature: Double?,
        maxTokens: Int?,
        responseFormat: ResponseFormat?,
        tools: List<ToolDef>?
    ): Request {
        val s = settings.current
        require(s.apiKey.isNotBlank()) { "未配置 API Key，请到「我的 → AI 服务设置」填写" }
        val body = AppJson.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = s.model,
                messages = messages,
                stream = stream,
                temperature = temperature ?: s.temperature,
                maxTokens = maxTokens ?: s.maxTokens,
                responseFormat = responseFormat,
                tools = tools
            )
        ).toRequestBody(jsonType)
        return Request.Builder()
            .url(chatCompletionsUrl(s.baseUrl))
            .header("Authorization", "Bearer ${s.apiKey}")
            .post(body)
            .build()
    }

    /**
     * 端点规范化：用户可能填 base（/v1）、完整端点（/v1/chat/completions）或自定义兼容路径。
     * 统一得到 xxx/chat/completions，避免重复拼接导致 404。
     */
    fun chatCompletionsUrl(rawBase: String): String {
        var base = rawBase.trim().trimEnd('/')
        // 去掉用户误填的端点后缀
        if (base.endsWith("/chat/completions")) {
            base = base.removeSuffix("/chat/completions").trimEnd('/')
        }
        return "$base/chat/completions"
    }

    /** 列出可用模型（GET {base}/models），用于设置页自动拉取。 */
    suspend fun listModels(): List<String> = withContext(Dispatchers.IO) {
        val s = settings.current
        require(s.apiKey.isNotBlank()) { "未配置 API Key" }
        var base = s.baseUrl.trim().trimEnd('/')
        if (base.endsWith("/chat/completions")) base = base.removeSuffix("/chat/completions").trimEnd('/')
        val request = Request.Builder()
            .url("$base/models")
            .header("Authorization", "Bearer ${s.apiKey}")
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} ${text.take(300)}")
            val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: throw IOException("返回格式无法解析")
            val arr = root["data"] as? JsonArray ?: throw IOException("返回中没有 data 字段")
            arr.mapNotNull { el ->
                (el as? JsonObject)?.get("id")?.jsonPrimitive?.contentOrNull
            }.sorted()
        }
    }

    /**
     * 流式对话，逐增量回调；返回完整结果（文本 + 工具调用聚合）。
     * 协程取消（离开页面 / 上层取消）时通过 invokeOnCompletion 取消 HTTP 调用，
     * 阻塞的流读取会被中断，socket 不再继续收流。
     */
    private suspend fun executeStreaming(
        messages: List<ApiMessage>,
        temperature: Double? = null,
        maxTokens: Int? = null,
        responseFormat: ResponseFormat? = null,
        tools: List<ToolDef>? = null,
        onDelta: (String) -> Unit
    ): StreamResult {
        val request = buildRequest(messages, true, temperature, maxTokens, responseFormat, tools)
        val call = client.newCall(request)
        val job = coroutineContext[Job]
        job?.invokeOnCompletion { call.cancel() }
        return call.execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string()?.take(500) ?: ""
                throw IOException("HTTP ${resp.code} $err")
            }
            val source = resp.body?.source() ?: throw IOException("空响应")
            val sb = StringBuilder()
            // 工具调用按 index 聚合（流式分片传输）
            val toolIds = mutableMapOf<Int, String>()
            val toolNames = mutableMapOf<Int, String>()
            val toolArgs = mutableMapOf<Int, StringBuilder>()
            while (true) {
                job?.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val chunk = runCatching { AppJson.decodeFromString(ChatChunk.serializer(), data) }
                    .getOrNull() ?: continue
                val delta = chunk.choices.firstOrNull()?.delta ?: continue
                if (!delta.content.isNullOrEmpty()) {
                    sb.append(delta.content)
                    onDelta(delta.content)
                }
                delta.toolCalls?.forEach { tc ->
                    val idx = tc.index
                    tc.id?.let { toolIds[idx] = it }
                    tc.function?.name?.let { toolNames[idx] = it }
                    tc.function?.arguments?.let { arg ->
                        toolArgs.getOrPut(idx) { StringBuilder() }.append(arg)
                    }
                }
            }
            val calls = toolIds.keys.sorted().mapNotNull { idx ->
                val id = toolIds[idx] ?: return@mapNotNull null
                val name = toolNames[idx] ?: return@mapNotNull null
                AccToolCall(id, name, toolArgs[idx]?.toString() ?: "")
            }
            StreamResult(sb.toString(), calls)
        }
    }

    suspend fun streamApi(
        messages: List<ApiMessage>,
        temperature: Double? = null,
        maxTokens: Int? = null,
        responseFormat: ResponseFormat? = null,
        tools: List<ToolDef>? = null,
        onDelta: (String) -> Unit
    ): StreamResult = withContext(Dispatchers.IO) {
        executeStreaming(messages, temperature, maxTokens, responseFormat, tools, onDelta)
    }

    suspend fun streamChat(
        messages: List<ChatMessage>,
        temperature: Double? = null,
        maxTokens: Int? = null,
        responseFormat: ResponseFormat? = null,
        onDelta: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        executeStreaming(messages.map { it.toApiMessage() }, temperature, maxTokens, responseFormat, null, onDelta).content
    }

    /** 非流式单轮，用于连接测试与轻量任务（出题/复核/精讲/标题）。 */
    suspend fun chatOnce(
        messages: List<ChatMessage>,
        model: String? = null,
        temperature: Double? = null,
        maxTokens: Int? = null,
        responseFormat: ResponseFormat? = null
    ): String = withContext(Dispatchers.IO) {
        executeStreaming(messages.map { it.toApiMessage() }, temperature, maxTokens, responseFormat, null) {}.content
    }

    /** 连接测试：返回成功则 null，失败返回错误描述。 */
    suspend fun testConnection(): String? = try {
        val reply = chatOnce(
            messages = listOf(ChatMessage("user", "hi")),
            maxTokens = 8,
            temperature = 0.0
        )
        if (reply.isBlank()) "服务返回为空" else null
    } catch (t: Throwable) {
        t.message ?: t.javaClass.simpleName
    }
}