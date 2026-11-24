package com.bosla.client

import android.os.Handler
import android.os.Looper
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * عميل Supabase عبر REST مباشرة (بدون SDK) — يعمل مع مشروع Supabase المجاني.
 * مشروع bosla-app — القيم الحقيقية مضمّنة (تم التحقق من عمل المشروع 2026-11-24).
 */
object SupabaseClient {

    var supabaseUrl: String = "https://latsgfvpgrcpfnhtdfij.supabase.co"
    var anonKey: String = "sb_publishable_KqGzygcUNUY-Qtcch_iduQ_Fz-jjSvY"

    var accessToken: String? = null

    data class Wallet(val walletNumber: String, val balance: Int, val displayName: String?)
    data class BuildItem(
        val id: String,
        val prompt: String,
        val status: String,
        val complexity: String?,
        val pointsCharged: Int,
        val apkUrl: String?,
        val createdAt: String
    )
    data class LedgerEntry(val amount: Int, val type: String, val reason: String?, val createdAt: String)
    data class Settings(
        val pricePerPoint: Int,
        val pointsSimple: Int,
        val pointsMedium: Int,
        val pointsComplex: Int
    )

    // مهل أطول: شبكات الجوال البطيئة كانت تقطع الطلب بعد 10 ثوانٍ (خطأ «تعذّر الاتصال»)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonType = "application/json".toMediaType()

    private fun runOnMain(action: () -> Unit) {
        mainHandler.post { action() }
    }

    private fun call(
        url: String,
        method: String,
        bodyJson: String?,
        auth: Boolean,
        bearer: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val body = bodyJson?.toRequestBody(jsonType)
                val reqBuilder = Request.Builder().url(url)
                reqBuilder.addHeader("apikey", anonKey)
                val token = bearer ?: if (auth) accessToken else null
                token?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }
                when (method) {
                    "POST" -> reqBuilder.post(body ?: "".toRequestBody(jsonType))
                    "GET" -> reqBuilder.get()
                    else -> reqBuilder.method(method, body)
                }
                val response = http.newCall(reqBuilder.build()).execute()
                val text = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    runOnMain { onSuccess(text) }
                } else {
                    runOnMain { onError(text.ifBlank { "HTTP ${response.code}" }) }
                }
            } catch (e: Exception) {
                runOnMain { onError("NETWORK") }
            }
        }.start()
    }

    // الاسم واسم المستخدم يُحفظان في بيانات الحساب (user_metadata)
    // — تقرأهما دالة ensure_wallet عند إنشاء المحفظة (supabase/003_username.sql)
    fun signUp(email: String, password: String, displayName: String, username: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        // رابط التفعيل يعيد المستخدم إلى التطبيق (boslaapp://verified)
        // — التحقق من الإيميل مفروض عند إنشاء الحساب
        val data = JSONObject()
            .put("display_name", displayName)
            .put("username", username)
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", data)
            .put("redirect_to", "boslaapp://verified")
            .put("redirectTo", "boslaapp://verified")
            .toString()
        call(
            "$supabaseUrl/auth/v1/signup", "POST", body, false,
            onSuccess = { text ->
                val token = JSONObject(text).optString("access_token", "")
                if (token.isBlank()) onError("CONFIRM_EMAIL") else onSuccess(token)
            },
            onError = onError
        )
    }

    fun signIn(email: String, password: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        call(
            "$supabaseUrl/auth/v1/token?grant_type=password", "POST", body, false,
            onSuccess = { text ->
                val token = JSONObject(text).optString("access_token", "")
                if (token.isBlank()) onError("LOGIN_FAILED") else onSuccess(token)
            },
            onError = { err ->
                // الدخول مرفوض قبل تفعيل الإيميل — رسالة واضحة بدل خطأ عام
                if (err.contains("not confirmed", ignoreCase = true)) onError("EMAIL_NOT_CONFIRMED")
                else onError(err)
            }
        )
    }

    fun getSettings(onSuccess: (Settings) -> Unit, onError: (String) -> Unit) {
        call(
            "$supabaseUrl/rest/v1/settings?select=key,value", "GET", null, false,
            onSuccess = { text ->
                val arr = JSONArray(text)
                var price = 100
                var simple = 10
                var medium = 25
                var complex = 50
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    when (row.optString("key")) {
                        "points_price_sdg" -> price = row.optString("value", "100").toIntOrNull() ?: 100
                        "points_simple" -> simple = row.optString("value", "10").toIntOrNull() ?: 10
                        "points_medium" -> medium = row.optString("value", "25").toIntOrNull() ?: 25
                        "points_complex" -> complex = row.optString("value", "50").toIntOrNull() ?: 50
                    }
                }
                onSuccess(Settings(price, simple, medium, complex))
            },
            onError = onError
        )
    }

    fun ensureWallet(onSuccess: (Wallet) -> Unit, onError: (String) -> Unit) {
        call(
            "$supabaseUrl/rest/v1/rpc/ensure_wallet", "POST", "{}", true,
            onSuccess = { text ->
                val obj = if (text.trim().startsWith("[")) JSONArray(text).getJSONObject(0) else JSONObject(text)
                onSuccess(
                    Wallet(
                        walletNumber = obj.optString("wallet_number"),
                        balance = obj.optInt("points_balance", 0),
                        displayName = obj.optString("display_name").ifBlank { null }
                    )
                )
            },
            onError = onError
        )
    }

    fun getTransactions(wallet: String, onSuccess: (List<LedgerEntry>) -> Unit, onError: (String) -> Unit) {
        val w = URLEncoder.encode(wallet, "UTF-8")
        call(
            "$supabaseUrl/rest/v1/points_transactions?wallet_number=eq.$w&order=created_at.desc&select=amount,type,reason,created_at",
            "GET", null, true,
            onSuccess = { text ->
                val arr = JSONArray(text)
                val list = ArrayList<LedgerEntry>()
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    list.add(
                        LedgerEntry(
                            amount = row.optInt("amount", 0),
                            type = row.optString("type"),
                            reason = row.optString("reason").ifBlank { null },
                            createdAt = row.optString("created_at")
                        )
                    )
                }
                onSuccess(list)
            },
            onError = onError
        )
    }

    fun getBuilds(wallet: String, onSuccess: (List<BuildItem>) -> Unit, onError: (String) -> Unit) {
        val w = URLEncoder.encode(wallet, "UTF-8")
        call(
            "$supabaseUrl/rest/v1/builds?wallet_number=eq.$w&order=created_at.desc&select=id,prompt,status,complexity,points_charged,apk_artifact_url,created_at",
            "GET", null, true,
            onSuccess = { text ->
                val arr = JSONArray(text)
                val list = ArrayList<BuildItem>()
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    list.add(
                        BuildItem(
                            id = row.optString("id"),
                            prompt = row.optString("prompt"),
                            status = row.optString("status"),
                            complexity = row.optString("complexity").ifBlank { null },
                            pointsCharged = row.optInt("points_charged", 0),
                            apkUrl = row.optString("apk_artifact_url").ifBlank { null },
                            createdAt = row.optString("created_at")
                        )
                    )
                }
                onSuccess(list)
            },
            onError = onError
        )
    }

    fun startBuild(prompt: String, complexity: String, points: Int, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val body = JSONObject()
            .put("prompt", prompt)
            .put("complexity", complexity)
            .put("points", points)
            .toString()
        call(
            "$supabaseUrl/functions/v1/start-build", "POST", body, true,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    // إعادة تعيين كلمة السر: يرسل Supabase رابطاً إلى البريد.
    // الرابط يعيد المستخدم إلى التطبيق عبر boslaapp://reset-password
    // (يجب إضافة هذا الرابط في Supabase: Authentication ← URL Configuration ← Redirect URLs)
    fun recoverPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val body = JSONObject()
            .put("email", email)
            .put("redirect_to", "boslaapp://reset-password")
            .put("redirectTo", "boslaapp://reset-password")
            .toString()
        call(
            "$supabaseUrl/auth/v1/recover", "POST", body, false,
            onSuccess = { onSuccess() },
            onError = onError
        )
    }

    // تعيين كلمة السر الجديدة باستعمال توكن الاسترداد من رابط البريد
    fun updatePassword(recoveryToken: String, newPassword: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val body = JSONObject().put("password", newPassword).toString()
        call(
            "$supabaseUrl/auth/v1/user", "PUT", body, false,
            bearer = recoveryToken,
            onSuccess = { onSuccess() },
            onError = onError
        )
    }
}
