package com.bosla.client

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.FragmentWalletBinding
import java.net.URLEncoder

class WalletFragment : Fragment() {

    private var _binding: FragmentWalletBinding? = null
    private val binding get() = _binding!!

    private var wallet: SupabaseClient.Wallet? = null
    private var settings = SupabaseClient.Settings(100, 10, 25, 50)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWalletBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SupabaseClient.getSettings(
            onSuccess = { loaded -> settings = loaded },
            onError = {}
        )
        loadWallet()
        binding.buttonTopup.setOnClickListener { askMethodAndSend() }
        binding.buttonLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }
    }

    private fun loadWallet() {
        SupabaseClient.ensureWallet(
            onSuccess = { w ->
                wallet = w
                binding.textWalletNumber.text = w.walletNumber
                binding.textBalance.text = "${w.balance} ${getString(R.string.points_unit)}"
                loadTransactions(w.walletNumber)
            },
            onError = {
                binding.textWalletNumber.text = getString(R.string.error_network)
            }
        )
    }

    private fun loadTransactions(walletNumber: String) {
        SupabaseClient.getTransactions(
            walletNumber,
            onSuccess = { entries ->
                binding.layoutTransactions.removeAllViews()
                if (entries.isEmpty()) {
                    binding.textNoTransactions.visibility = View.VISIBLE
                    return@getTransactions
                }
                binding.textNoTransactions.visibility = View.GONE
                for (entry in entries) {
                    val row = TextView(requireContext())
                    val sign = if (entry.amount >= 0) "+" else ""
                    val reason = when (entry.reason) {
                        "welcome gift" -> getString(R.string.reason_welcome_gift)
                        else -> entry.reason ?: entry.type
                    }
                    val date = entry.createdAt.take(10)
                    row.text = "$date  $sign${entry.amount} ${getString(R.string.points_unit)} — $reason"
                    row.textSize = 16f
                    row.setTextColor(Color.parseColor("#101D35"))
                    row.setPadding(0, 8, 0, 8)
                    binding.layoutTransactions.addView(row)
                }
            },
            onError = {}
        )
    }

    private fun askMethodAndSend() {
        val currentWallet = wallet ?: return
        val points = binding.editTopupPoints.text.toString().trim().toIntOrNull() ?: 0
        if (points <= 0) return
        val methods = arrayOf(
            getString(R.string.method_bankak),
            getString(R.string.method_sahel),
            getString(R.string.method_mycashi)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.topup_method_label)
            .setItems(methods) { _, which ->
                sendTopupWhatsApp(currentWallet, points, methods[which])
            }
            .show()
    }

    private fun sendTopupWhatsApp(wallet: SupabaseClient.Wallet, points: Int, method: String) {
        val amount = points * settings.pricePerPoint
        val name = wallet.displayName ?: ""
        val message = "مرحباً، أريد شحن نقاط في بوصلة التطبيقات." +
            " الاسم: $name، رقم المحفظة: ${wallet.walletNumber}،" +
            " النقاط المطلوبة: $points، المبلغ: $amount جنيه سوداني، طريقة الدفع: $method"
        val url = "https://wa.me/249917109959?text=" + URLEncoder.encode(message, "UTF-8")
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
