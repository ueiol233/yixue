package com.ueiol.yixue.domain.agent

import com.ueiol.yixue.data.ai.OpenAiClient
import com.ueiol.yixue.domain.model.AppJson
import com.ueiol.yixue.domain.model.ChatMessage
import com.ueiol.yixue.domain.model.ResponseFormat
import kotlinx.serialization.Serializable
import java.io.IOException

/**
 * 判分复核 + 归档 Agent（规划书 3.3/3.5）与精讲 Agent（3.6）。
 */
class ReviewAgent(private val ai: OpenAiClient) {

    @Serializable
    data class ReviewResult(
        val verdict: String = "WRONG",
        val reason: String = "",
        val mistakeTag: String = "其他",
        val subject: String = ""
    )

    @Serializable
    private data class ReviewReq(
        val stem: String,
        val options: List<String> = emptyList(),
        val studentAnswer: String,
        val correctAnswer: String,
        val referenceAnswer: String = ""
    )

    suspend fun review(
        stem: String,
        options: List<String>,
        studentAnswer: String,
        correctAnswer: String,
        referenceAnswer: String
    ): ReviewResult {
        val req = AppJson.encodeToString(
            ReviewReq.serializer(),
            ReviewReq(stem, options, studentAnswer, correctAnswer, referenceAnswer)
        )
        val messages = listOf(
            ChatMessage("system", Prompts.REVIEW_INSTRUCTION),
            ChatMessage("user", req)
        )
        val raw = ai.chatOnce(messages, responseFormat = ResponseFormat("json_object"), temperature = 0.0)
        val json = extractJson(raw)
        return try {
            AppJson.decodeFromString(ReviewResult.serializer(), json)
        } catch (t: Throwable) {
            throw IOException("复核结果解析失败: ${t.message?.take(100)}")
        }
    }

    suspend fun explain(question: String, studentAnswer: String, correctAnswer: String, mistakeTag: String): String =
        explainStream(question, studentAnswer, correctAnswer, mistakeTag) {}

    /** 流式精讲：逐段回调，便于界面实时展示 AI 输出过程。 */
    suspend fun explainStream(
        question: String,
        studentAnswer: String,
        correctAnswer: String,
        mistakeTag: String,
        onDelta: (String) -> Unit
    ): String {
        val user = buildString {
            appendLine("错题：$question")
            appendLine("我的答案：${studentAnswer.ifBlank { "未作答" }}")
            appendLine("正确答案：$correctAnswer")
            if (mistakeTag.isNotBlank()) appendLine("错因：$mistakeTag")
        }
        val messages = listOf(
            ChatMessage("system", Prompts.EXPLAIN_INSTRUCTION),
            ChatMessage("user", user)
        )
        return ai.streamChat(messages, temperature = 0.4, onDelta = onDelta)
    }

    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        if (start < 0) return raw
        var depth = 0; var inStr = false; var esc = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                esc -> esc = false
                c == '\\' && inStr -> esc = true
                c == '"' -> inStr = !inStr
                !inStr && c == '{' -> depth++
                !inStr && c == '}' -> { depth--; if (depth == 0) return raw.substring(start, i + 1) }
            }
        }
        return raw.substring(start)
    }
}