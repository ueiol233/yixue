package com.ueiol.yixue.domain.model

import kotlinx.serialization.Serializable

/** AI 出题结构化模型（严格 JSON 输出）。 */
@Serializable
data class QuizSpec(
    val title: String = "练习",
    val subject: String = "",
    val questions: List<QuestionSpec> = emptyList()
)

@Serializable
data class QuestionSpec(
    // single / multiple / fill
    val type: String = "",
    val stem: String = "",
    val options: List<String> = emptyList(),
    // 选择题：A 或 ABD；填空题：标准答案
    val answer: String = "",
    val acceptedAnswers: List<String> = emptyList(),
    val referenceAnswer: String = "",
    val explanation: String = "",
    val plotExpressions: List<String> = emptyList()
) {
    val isSingle: Boolean get() = type == "single"
    val isMultiple: Boolean get() = type == "multiple"
    val isFill: Boolean get() = type == "fill"
    val isChoice: Boolean get() = isSingle || isMultiple
}