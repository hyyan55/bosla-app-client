package com.bosla.client

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
