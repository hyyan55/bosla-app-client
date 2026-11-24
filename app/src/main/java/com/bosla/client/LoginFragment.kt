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
        binding.buttonForgot.setOnClickListener { forgotPassword() }
        // رسالة وصول من رابط التفعيل (boslaapp://verified): تم تفعيل البريد
        (activity as? MainActivity)?.loginNotice?.let { notice ->
            showForgotStatus(notice)
            (activity as? MainActivity)?.loginNotice = null
        }
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
                "EMAIL_NOT_CONFIRMED" -> getString(R.string.email_not_confirmed_notice)
                "NETWORK" -> getString(R.string.error_network)
                else -> getString(if (signUp) R.string.signup_error else R.string.login_error)
            }
            binding.textError.visibility = View.VISIBLE
        }
        if (signUp) SupabaseClient.signUp(email, password, onSuccess, onError)
        else SupabaseClient.signIn(email, password, onSuccess, onError)
    }

    private fun forgotPassword() {
        val email = binding.editEmail.text.toString().trim()
        if (email.isBlank()) {
            showForgotStatus(getString(R.string.forgot_password_empty_email))
            return
        }
        binding.buttonForgot.isEnabled = false
        showForgotStatus(getString(R.string.loading))
        SupabaseClient.recoverPassword(email,
            onSuccess = {
                binding.buttonForgot.isEnabled = true
                showForgotStatus(getString(R.string.forgot_password_sent))
            },
            onError = { err ->
                binding.buttonForgot.isEnabled = true
                showForgotStatus(
                    getString(
                        if (err == "NETWORK") R.string.error_network else R.string.forgot_password_error
                    )
                )
            }
        )
    }

    private fun showForgotStatus(text: String) {
        binding.textForgotStatus.text = text
        binding.textForgotStatus.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
