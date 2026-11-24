package com.bosla.client

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    // الوضع الحالي: دخول (false) أو إنشاء حساب (true)
    private var isSignUp = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonMain.setOnClickListener { doAuth() }
        binding.textToggleMode.setOnClickListener { setMode(!isSignUp) }
        binding.buttonForgot.setOnClickListener { forgotPassword() }
        setMode(false)
        // رسالة وصول من رابط التفعيل (boslaapp://verified): تم تفعيل البريد
        (activity as? MainActivity)?.loginNotice?.let { notice ->
            showForgotStatus(notice)
            (activity as? MainActivity)?.loginNotice = null
        }
    }

    private fun setMode(signUp: Boolean) {
        isSignUp = signUp
        clearFieldErrors()
        binding.textError.visibility = View.GONE
        binding.textFormNotice.visibility = View.GONE
        val show = if (signUp) View.VISIBLE else View.GONE
        binding.layoutName.visibility = show
        binding.layoutUsername.visibility = show
        binding.layoutConfirmPassword.visibility = show
        binding.buttonForgot.visibility = if (signUp) View.GONE else View.VISIBLE
        binding.textFormTitle.text = getString(if (signUp) R.string.signup_title else R.string.login_title)
        binding.textFormSubtitle.text = getString(if (signUp) R.string.signup_subtitle else R.string.login_subtitle)
        binding.buttonMain.text = getString(if (signUp) R.string.signup_button else R.string.login_button)
        binding.textToggleMode.text = getString(if (signUp) R.string.toggle_to_login else R.string.toggle_to_signup)
    }

    private fun clearFieldErrors() {
        binding.layoutName.error = null
        binding.layoutUsername.error = null
        binding.layoutEmail.error = null
        binding.layoutPassword.error = null
        binding.layoutConfirmPassword.error = null
    }

    private fun setLoading(loading: Boolean) {
        binding.buttonMain.isEnabled = !loading
        binding.textToggleMode.isEnabled = !loading
        binding.buttonForgot.isEnabled = !loading
        if (loading) {
            binding.buttonMain.text = getString(if (isSignUp) R.string.loading_signup else R.string.loading_login)
        } else {
            binding.buttonMain.text = getString(if (isSignUp) R.string.signup_button else R.string.login_button)
        }
    }

    private fun doAuth() {
        clearFieldErrors()
        binding.textError.visibility = View.GONE
        binding.textFormNotice.visibility = View.GONE

        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString()
        var valid = true

        var name = ""
        var username = ""
        var confirmPassword = ""
        if (isSignUp) {
            name = binding.editName.text.toString().trim()
            username = binding.editUsername.text.toString().trim()
            confirmPassword = binding.editConfirmPassword.text.toString()
            if (name.length < 2) {
                binding.layoutName.error = getString(R.string.error_name)
                valid = false
            }
            if (!username.matches(Regex("[a-zA-Z0-9_.-]{3,}"))) {
                binding.layoutUsername.error = getString(R.string.error_username)
                valid = false
            }
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.layoutEmail.error = getString(R.string.error_email)
            valid = false
        }
        if (password.length < 6) {
            binding.layoutPassword.error = getString(R.string.error_password_short)
            valid = false
        }
        if (isSignUp && password != confirmPassword) {
            binding.layoutConfirmPassword.error = getString(R.string.error_password_mismatch)
            valid = false
        }
        if (!valid) return

        setLoading(true)
        val onSuccess: (String) -> Unit = { token ->
            // المحفظة تُنشأ تلقائياً عند تسجيل الدخول — بلا خطوة منفصلة
            SupabaseClient.ensureWallet(onSuccess = {}, onError = {})
            (activity as? MainActivity)?.onLoggedIn(token)
        }
        val onError: (String) -> Unit = { err ->
            setLoading(false)
            when (err) {
                "CONFIRM_EMAIL" -> {
                    // الحساب أُنشئ — بقي تفعيل البريد من الرابط
                    binding.textFormNotice.text = getString(R.string.signup_success)
                    binding.textFormNotice.visibility = View.VISIBLE
                    binding.editPassword.text?.clear()
                    binding.editConfirmPassword.text?.clear()
                    setMode(false)
                }
                "EMAIL_NOT_CONFIRMED" -> showError(getString(R.string.email_not_confirmed_notice))
                "NETWORK" -> showError(getString(R.string.error_network))
                else -> {
                    if (err.contains("already registered", ignoreCase = true)) {
                        showError(getString(R.string.email_already_registered))
                    } else {
                        showError(getString(if (isSignUp) R.string.signup_error else R.string.login_error))
                    }
                }
            }
        }
        if (isSignUp) SupabaseClient.signUp(email, password, name, username, onSuccess, onError)
        else SupabaseClient.signIn(email, password, onSuccess, onError)
    }

    private fun showError(text: String) {
        binding.textError.text = text
        binding.textError.visibility = View.VISIBLE
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
