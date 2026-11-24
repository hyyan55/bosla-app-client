package com.bosla.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLogin.setOnClickListener { doAuth(signUp = false) }
        binding.buttonSignup.setOnClickListener { doAuth(signUp = true) }
    }

    private fun doAuth(signUp: Boolean) {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString()
        if (email.isBlank() || password.length < 6) {
            binding.textError.text = getString(R.string.login_error)
            binding.textError.visibility = View.VISIBLE
            return
        }
        binding.textError.visibility = View.GONE
        binding.buttonLogin.isEnabled = false
        binding.buttonSignup.isEnabled = false
        val onSuccess: (String) -> Unit = { token ->
            (activity as? MainActivity)?.onLoggedIn(token)
            // المحفظة تُنشأ تلقائياً عند تسجيل الدخول — بلا خطوة منفصلة
            SupabaseClient.ensureWallet(onSuccess = {}, onError = {})
        }
        val onError: (String) -> Unit = { err ->
            binding.buttonLogin.isEnabled = true
            binding.buttonSignup.isEnabled = true
            binding.textError.text = when (err) {
                "CONFIRM_EMAIL" -> getString(R.string.confirm_email_notice)
                "NETWORK" -> getString(R.string.error_network)
                else -> getString(if (signUp) R.string.signup_error else R.string.login_error)
            }
            binding.textError.visibility = View.VISIBLE
        }
        if (signUp) SupabaseClient.signUp(email, password, onSuccess, onError)
        else SupabaseClient.signIn(email, password, onSuccess, onError)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
