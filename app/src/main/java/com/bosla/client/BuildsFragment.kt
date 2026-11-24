package com.bosla.client

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.FragmentBuildsBinding

class BuildsFragment : Fragment() {

    private var _binding: FragmentBuildsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBuildsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonRefresh.setOnClickListener { loadBuilds() }
        loadBuilds()
    }

    private fun loadBuilds() {
        SupabaseClient.ensureWallet(
            onSuccess = { wallet ->
                SupabaseClient.getBuilds(
                    wallet.walletNumber,
                    onSuccess = { builds -> renderBuilds(builds) },
                    onError = {}
                )
            },
            onError = {}
        )
    }

    private fun renderBuilds(builds: List<SupabaseClient.BuildItem>) {
        binding.layoutBuilds.removeAllViews()
        if (builds.isEmpty()) {
            binding.textNoBuilds.visibility = View.VISIBLE
            return
        }
        binding.textNoBuilds.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        for (build in builds) {
            val row = inflater.inflate(R.layout.item_build, binding.layoutBuilds, false)
            val promptView = row.findViewById<TextView>(R.id.text_build_prompt)
            val statusView = row.findViewById<TextView>(R.id.text_build_status)
            val pointsView = row.findViewById<TextView>(R.id.text_build_points)
            val downloadButton = row.findViewById<Button>(R.id.button_download)
            promptView.text = build.prompt
            statusView.text = getString(
                when (build.status) {
                    "building" -> R.string.status_building
                    "success" -> R.string.status_success
                    "failed" -> R.string.status_failed
                    else -> R.string.status_pending
                }
            )
            pointsView.text = getString(R.string.build_points_charged, build.pointsCharged)
            if (build.status == "success" && !build.apkUrl.isNullOrBlank()) {
                val url = build.apkUrl
                downloadButton.visibility = View.VISIBLE
                downloadButton.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            } else {
                downloadButton.visibility = View.GONE
            }
            binding.layoutBuilds.addView(row)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
