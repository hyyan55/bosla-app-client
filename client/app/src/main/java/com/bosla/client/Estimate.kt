package com.bosla.client

/**
 * تقدير تعقيد الوصف محلياً قبل البناء — النقاط النهائية تُؤخذ من إعدادات Supabase.
 * بسيط: وصف قصير بلا ميزات معقدة. معقد: كلمتان معقدتان أو أكثر، أو وصف طويل جداً.
 */
object Estimate {

    enum class Complexity { SIMPLE, MEDIUM, COMPLEX }

    private val complexKeywords = listOf(
        "تسجيل دخول", "حساب", "قاعدة بيانات", "خريطة", "دفع", "متجر",
        "محادثة", "إشعار", "كاميرا", "صور", "مزامنة", "ذكاء اصطناعي", "شاشات"
    )

    fun estimate(description: String): Complexity {
        val text = description.trim()
        var hits = 0
        for (keyword in complexKeywords) {
            if (text.contains(keyword)) hits++
        }
        return when {
            hits >= 2 || text.length > 300 -> Complexity.COMPLEX
            hits == 0 && text.length < 80 -> Complexity.SIMPLE
            else -> Complexity.MEDIUM
        }
    }

    fun pointsFor(complexity: Complexity, settings: SupabaseClient.Settings): Int {
        return when (complexity) {
            Complexity.SIMPLE -> settings.pointsSimple
            Complexity.MEDIUM -> settings.pointsMedium
            Complexity.COMPLEX -> settings.pointsComplex
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
