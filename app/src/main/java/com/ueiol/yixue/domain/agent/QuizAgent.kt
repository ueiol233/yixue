package com.ueiol.yixue.domain.agent

import com.ueiol.yixue.data.ai.OpenAiClient
import com.ueiol.yixue.domain.model.AppJson
import com.ueiol.yixue.domain.model.ChatMessage
import com.ueiol.yixue.domain.model.QuestionSpec
import com.ueiol.yixue.domain.model.QuizSpec
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException

/**
 * 出题 Agent：结构化输出 + 本地校验 + 带错误反馈重试一次（规划书 6 节）。
 */
class QuizAgent(private val ai: OpenAiClient) {

    suspend fun generate(request: String): QuizSpec {
        val messages = listOf(
            ChatMessage("system", Prompts.QUIZ_INSTRUCTION),
            ChatMessage("user", request)
        )
        return requestSpec(messages)
    }

    suspend fun adjust(current: QuizSpec, request: String): QuizSpec {
        val currentJson = AppJson.encodeToString(QuizSpec.serializer(), current)
        val messages = listOf(
            ChatMessage("system", Prompts.QUIZ_ADJUST_INSTRUCTION),
            ChatMessage("user", "现有试卷：\n$currentJson\n\n调整要求：$request")
        )
        return requestSpec(messages)
    }

    private suspend fun requestSpec(messages: List<ChatMessage>): QuizSpec {
        val first = ai.chatOnce(messages, responseFormat = jsonFormat())
        parseAndValidate(first)?.let { return it }
        // 带错误反馈重试一次
        val feedback = messages + ChatMessage(
            "assistant", first.takeLast(2000)
        ) + ChatMessage("user", lastError ?: "输出不是合法试卷")
        val second = ai.chatOnce(feedback, responseFormat = jsonFormat())
        return parseAndValidate(second)
            ?: throw IOException("AI 输出的试卷格式不合规，请重试。（$lastError）")
    }

    private fun jsonFormat() = com.ueiol.yixue.domain.model.ResponseFormat("json_object")

    private var lastError: String? = null

    private fun parseAndValidate(raw: String): QuizSpec? {
        lastError = null
        val json = extractJson(raw)
        val spec = try {
            AppJson.decodeFromString(QuizSpec.serializer(), json)
        } catch (t: Throwable) {
            lastError = "JSON 解析失败: ${t.message?.take(120)}"
            return null
        }
        val err = validate(spec)
        if (err != null) { lastError = err; return null }
        return spec
    }

    /** 从可能含说明文字的回复中提取首个 JSON 对象。 */
    private fun extractJson(raw: String): String {
        val start = raw.indexOf('{')
        if (start < 0) return raw
        var depth = 0
        var inStr = false
        var esc = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                esc -> esc = false
                c == '\\' && inStr -> esc = true
                c == '"' -> inStr = !inStr
                !inStr && c == '{' -> depth++
                !inStr && c == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return raw.substring(start)
    }

    private fun validate(spec: QuizSpec): String? {
        if (spec.questions.isEmpty()) return "questions 为空"
        spec.questions.forEachIndexed { idx, q ->
            if (q.stem.isBlank()) return "第${idx + 1}题题干为空"
            when {
                q.isSingle -> {
                    if (q.options.size < 2) return "第${idx + 1}题选项不足"
                    val a = q.answer.trim().uppercase()
                    if (a.length != 1 || a !in "ABCD") return "第${idx + 1}题单选答案非法: ${q.answer}"
                }
                q.isMultiple -> {
                    if (q.options.size < 2) return "第${idx + 1}题选项不足"
                    val a = q.answer.trim().uppercase()
                    if (a.isEmpty() || a.any { it !in 'A'..'D' }) return "第${idx + 1}题多选答案非法: ${q.answer}"
                }
                q.isFill -> {
                    if (q.answer.isBlank()) return "第${idx + 1}题填空答案为空"
                }
                else -> return "第${idx + 1}题题型非法: ${q.type}"
            }
        }
        return null
    }
}