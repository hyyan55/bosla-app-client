package com.bosla.client

/**
 * تعقيد التطبيق كما يحدده الذكاء الاصطناعي (Edge Function `chat`) —
 * لا يوجد تقدير محلي مبني على كلمات مفتاحية؛ التحليل الحقيقي في السيرفر.
 */
object Estimate {

    enum class Complexity { SIMPLE, MEDIUM, COMPLEX }

    fun fromString(value: String): Complexity? {
        return when (value.lowercase()) {
            "simple" -> Complexity.SIMPLE
            "medium" -> Complexity.MEDIUM
            "complex" -> Complexity.COMPLEX
            else -> null
        }
    }

    fun labelFor(complexity: Complexity): Int {
        return when (complexity) {
            Complexity.SIMPLE -> R.string.estimate_simple
            Complexity.MEDIUM -> R.string.estimate_medium
            Complexity.COMPLEX -> R.string.estimate_complex
        }
    }
}
