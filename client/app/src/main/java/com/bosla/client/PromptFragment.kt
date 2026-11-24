package com.bosla.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.FragmentPromptBinding

class PromptFragment : Fragment() {

    private var _binding: FragmentPromptBinding? = null
    private val binding get() = _binding!!

    private var settings = SupabaseClient.Settings(100, 10, 25, 50)
    private var lastComplexity: Estimate.Complexity? = null
    private var lastPoints: Int = 0
    private var wallet: SupabaseClient.Wallet? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SupabaseClient.getSettings(
            onSuccess = { loaded -> settings = loaded },
            onError = {}
        )
        SupabaseClient.ensureWallet(
            onSuccess = { w -> wallet = w },
            onError = {}
        )
        binding.buttonEstimate.setOnClickListener { estimate() }
        binding.buttonBuild.setOnClickListener { build() }
    }

    private fun estimate() {
        val description = binding.editPrompt.text.toString().trim()
        if (description.isBlank()) {
            binding.textEstimate.text = getString(R.string.estimate_empty)
            lastComplexity = null
            return
        }
        val complexity = Estimate.estimate(description)
        val points = Estimate.pointsFor(complexity, settings)
        val price = points * settings.pricePerPoint
        lastComplexity = complexity
        lastPoints = points
        val label = getString(Estimate.labelFor(complexity))
        binding.textEstimate.text = "$label — " + getString(R.string.estimate_result, points, price)
    }

    private fun build() {
        val description = binding.editPrompt.text.toString().trim()
        if (description.isBlank()) {
            binding.textEstimate.text = getString(R.string.estimate_empty)
            return
        }
        if (lastComplexity == null) estimate()
        val complexity = lastComplexity ?: return
        val points = lastPoints
        val currentWallet = wallet
        if (currentWallet == null) {
            binding.textStatus.text = getString(R.string.error_network)
            return
        }
        if (currentWallet.balance < points) {
            binding.textStatus.text = getString(R.string.not_enough_points)
            return
        }
        binding.buttonBuild.isEnabled = false
        binding.textStatus.text = getString(R.string.loading)
        SupabaseClient.startBuild(
            prompt = description,
            complexity = complexity.name.lowercase(),
            points = points,
            onSuccess = {
                binding.buttonBuild.isEnabled = true
                binding.textStatus.text = getString(R.string.build_started)
            },
            onError = { err ->
                binding.buttonBuild.isEnabled = true
                binding.textStatus.text = getString(
                    if (err == "NETWORK") R.string.error_network else R.string.error_unknown
                )
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
