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
            showMain()
        }
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
        getSharedPreferences("bosla", Context.MODE_PRIVATE).edit()
            .putString("access_token", token).apply()
        SupabaseClient.accessToken = token
        showMain()
    }

    fun logout() {
        getSharedPreferences("bosla", Context.MODE_PRIVATE).edit()
            .remove("access_token").apply()
        SupabaseClient.accessToken = null
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
