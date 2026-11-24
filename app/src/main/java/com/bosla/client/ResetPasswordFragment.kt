package com.bosla.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.FragmentResetPasswordBinding

class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!
    private var recoveryToken: String? = null
    private var resetDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recoveryToken = arguments?.getString("recovery_token")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonReset.setOnClickListener {
            if (resetDone) {
                (activity as? MainActivity)?.goToLogin()
            } else {
                resetPassword()
            }
        }
    }

    private fun resetPassword() {
        val token = recoveryToken
        val password = binding.editNewPassword.text.toString()
        val confirm = binding.editConfirmPassword.text.toString()
        if (token.isNullOrBlank()) {
            showStatus(getString(R.string.reset_password_invalid_link))
            return
        }
        if (password.length < 6 || password != confirm) {
            showStatus(getString(R.string.reset_password_mismatch))
            return
        }
        binding.buttonReset.isEnabled = false
        showStatus(getString(R.string.loading))
        SupabaseClient.updatePassword(token, password,
            onSuccess = {
                resetDone = true
                showStatus(getString(R.string.reset_password_done))
                binding.buttonReset.text = getString(R.string.login_button)
                binding.buttonReset.isEnabled = true
            },
            onError = { err ->
                binding.buttonReset.isEnabled = true
                showStatus(
                    getString(
                        if (err == "NETWORK") R.string.error_network else R.string.error_unknown
                    )
                )
            }
        )
    }

    private fun showStatus(text: String) {
        binding.textResetStatus.text = text
        binding.textResetStatus.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
