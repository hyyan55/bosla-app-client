package com.bosla.client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    var loginNotice: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("bosla", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        if (token.isNullOrBlank()) {
            showLogin()
        } else {
            SupabaseClient.accessToken = token
            SupabaseClient.refreshToken = prefs.getString("refresh_token", null)
            showMain()
        }
        // بعد كل تحديث ناجح للجلسة من أي طلب (محفظة/بناءات/بناء)،
        // تُحفظ التوكنات الجديدة في SharedPreferences.
        SupabaseClient.onTokensChanged = { saveTokens() }
        handleDeepLink(intent)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_prompt -> {
                    showFragment(PromptFragment())
                    true
                }
                R.id.nav_wallet -> {
                    showFragment(WalletFragment())
                    true
                }
                R.id.nav_builds -> {
                    showFragment(BuildsFragment())
                    true
                }
                else -> false
            }
        }
    }

    fun onLoggedIn(token: String) {
        SupabaseClient.accessToken = token
        saveTokens()
        showMain()
    }

    // حفظ التوكنين الحاليين (دخول + تحديث) في SharedPreferences —
    // يُستدعى بعد الدخول وبعد كل تحديث ناجح للجلسة. القيمة الفارغة
    // تحذف مفتاحها بدل حفظ فراغ.
    fun saveTokens() {
        val edit = getSharedPreferences("bosla", Context.MODE_PRIVATE).edit()
        val at = SupabaseClient.accessToken
        val rt = SupabaseClient.refreshToken
        if (at.isNullOrBlank()) edit.remove("access_token") else edit.putString("access_token", at)
        if (rt.isNullOrBlank()) edit.remove("refresh_token") else edit.putString("refresh_token", rt)
        edit.apply()
    }

    fun logout() {
        SupabaseClient.accessToken = null
        SupabaseClient.refreshToken = null
        saveTokens()
        showLogin()
    }

    fun goToLogin() {
        showLogin()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    // روابط البريد: boslaapp://reset-password#access_token=... (إعادة تعيين)
    // و boslaapp://verified (تفعيل الإيميل بعد إنشاء الحساب)
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "boslaapp") return
        when (data.host) {
            "verified" -> {
                loginNotice = getString(R.string.email_verified_notice)
                goToLogin()
            }
            "reset-password" -> {
                val fragment = data.fragment ?: return
                val token = fragment.split("&")
                    .firstOrNull { it.startsWith("access_token=") }
                    ?.substringAfter("=")
                if (token.isNullOrBlank()) return
                // رابط الاسترداد يحمل توكن تحديث أيضاً — نحفظه ليجدّد
                // الجلسة لاحقاً (توكن الدخول هنا للاسترداد فقط ولا يُحفظ)
                val refresh = fragment.split("&")
                    .firstOrNull { it.startsWith("refresh_token=") }
                    ?.substringAfter("=")
                if (!refresh.isNullOrBlank()) {
                    SupabaseClient.refreshToken = refresh
                    saveTokens()
                }
                val resetFragment = ResetPasswordFragment()
                resetFragment.arguments = Bundle().apply { putString("recovery_token", token) }
                binding.bottomNav.visibility = View.GONE
                showFragment(resetFragment)
            }
        }
    }

    private fun showLogin() {
        binding.bottomNav.visibility = View.GONE
        showFragment(LoginFragment())
    }

    private fun showMain() {
        binding.bottomNav.visibility = View.VISIBLE
        binding.bottomNav.selectedItemId = R.id.nav_prompt
        showFragment(PromptFragment())
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
    }
}
