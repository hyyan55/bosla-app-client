package com.bosla.client

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bosla.client.databinding.FragmentPromptBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * تبويب البوت — وكيل ذكاء اصطناعي حقيقي:
 * التحليل والتقدير يأتيان من Gemini عبر Edge Function `chat` في Supabase
 * (لا ردود مسبقة ولا تقدير محلي). أفاتار البوت في كل رد، مؤشر كتابة متحرك
 * مع خطوات تفكير، حركات دخول للرسائل، اقتراحات سريعة، بطاقة تقدير مضغوطة.
 * الأسعار تُقرأ من إعدادات Supabase (سعر النقطة وشرائح النقاط).
 */
class PromptFragment : Fragment() {

    private var _binding: FragmentPromptBinding? = null
    private val binding get() = _binding!!

    private var settings = SupabaseClient.Settings(50, 100, 400, 800)
    private var lastComplexity: Estimate.Complexity? = null
    private var lastPoints: Int = 0
    private var lastDescription: String = ""
    private var wallet: SupabaseClient.Wallet? = null
    private var greeted = false
    private var isThinking = false
    private val handler = Handler(Looper.getMainLooper())
    private val chatScope = MainScope()
    private val chatHistory = ArrayList<Pair<String, String>>()

    // مؤشر الكتابة المتحرك
    private var typingRow: View? = null
    private var typingText: TextView? = null
    private var typingStep = 0
    private lateinit var thinkingSteps: Array<String>
    private val stepRunnable = Runnable { typingStep = 1 }
    private val dotsRunnable = object : Runnable {
        private var dots = 0
        override fun run() {
            val tv = typingText ?: return
            dots = (dots + 1) % 4
            tv.text = thinkingSteps[typingStep] + ".".repeat(dots)
            handler.postDelayed(this, 350)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        thinkingSteps = arrayOf(
            getString(R.string.chat_thinking_1),
            getString(R.string.chat_thinking_2)
        )
        SupabaseClient.getSettings(
            onSuccess = { loaded ->
                settings = loaded
                updatePriceInfo()
                greetOnce()
            },
            onError = { greetOnce() }
        )
        SupabaseClient.ensureWallet(
            onSuccess = { w ->
                wallet = w
                binding.textBalanceChip.text = getString(R.string.chat_balance_chip, w.balance)
            },
            onError = {}
        )
        binding.editPrompt.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }
        binding.buttonSend.setOnClickListener {
            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90)
                .withEndAction { it.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
                .start()
            sendMessage()
        }
        binding.buttonSuggest1.setOnClickListener { sendMessage(getString(R.string.suggest_1)) }
        binding.buttonSuggest2.setOnClickListener { sendMessage(getString(R.string.suggest_2)) }
        binding.buttonSuggest3.setOnClickListener { sendMessage(getString(R.string.suggest_3)) }
        binding.buttonBuild.setOnClickListener { build() }
    }

    private fun updatePriceInfo() {
        binding.textPriceInfo.text = getString(R.string.chat_online_price, settings.pricePerPoint)
    }

    private fun greetOnce() {
        if (greeted) return
        greeted = true
        addBotMessage(getString(R.string.chat_greeting))
        scrollToBottom()
    }

    private fun sendMessage() {
        sendMessage(binding.editPrompt.text.toString().trim())
    }

    private fun sendMessage(text: String) {
        if (text.isBlank() || isThinking) return
        // دالة المحادثة تتطلب جلسة مستخدم صالحة — بلا جلسة لا نتصل أبداً
        if (SupabaseClient.accessToken.isNullOrBlank()) {
            addBotMessage(getString(R.string.chat_ai_unavailable))
            scrollToBottom()
            return
        }
        isThinking = true
        lastDescription = text
        addUserMessage(text)
        binding.editPrompt.text?.clear()
        binding.buttonBuild.visibility = View.GONE
        binding.layoutSuggestions.visibility = View.GONE
        showTyping()
        scrollToBottom()
        val historySnapshot = chatHistory.takeLast(10).toList()
        chatHistory.add("user" to text)
        trimHistory()
        chatScope.launch {
            try {
                val result = SupabaseClient.chat(text, historySnapshot)
                stopTyping()
                addBotMessage(buildBotText(result))
                chatHistory.add("bot" to result.reply)
                trimHistory()
                val estimate = result.estimate
                if (estimate != null) {
                    lastComplexity = estimate.complexity
                    lastPoints = estimate.points
                    showEstimateCard(estimate)
                    binding.buttonBuild.visibility = View.VISIBLE
                } else {
                    lastComplexity = null
                    lastPoints = 0
                }
                isThinking = false
                scrollToBottom()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stopTyping()
                addBotMessage(getString(R.string.chat_ai_unavailable))
                isThinking = false
                scrollToBottom()
            }
        }
    }

    private fun trimHistory() {
        while (chatHistory.size > 10) chatHistory.removeAt(0)
    }

    /** نص رد البوت: الرد + أهم المزايا + سبب التقدير (النقاط والسعر في البطاقة فقط). */
    private fun buildBotText(result: SupabaseClient.ChatResult): String {
        val sb = StringBuilder(result.reply)
        val estimate = result.estimate
        if (estimate != null) {
            if (estimate.features.isNotEmpty()) {
                sb.append("\n\n").append(getString(R.string.chat_features_title))
                for (feature in estimate.features) {
                    sb.append("\n• ").append(feature)
                }
            }
            if (estimate.reasoning.isNotBlank()) {
                sb.append("\n\n").append(getString(R.string.chat_reasoning, estimate.reasoning))
            }
        }
        return sb.toString()
    }

    private fun build() {
        val complexity = lastComplexity ?: return
        val points = lastPoints
        val currentWallet = wallet
        if (currentWallet == null) {
            addBotMessage(getString(R.string.error_network))
            scrollToBottom()
            return
        }
        if (currentWallet.balance < points) {
            addBotMessage(getString(R.string.chat_need_points, currentWallet.balance, points))
            scrollToBottom()
            return
        }
        binding.buttonBuild.isEnabled = false
        addBotMessage(getString(R.string.loading))
        scrollToBottom()
        SupabaseClient.startBuild(
            prompt = lastDescription,
            complexity = complexity.name.lowercase(),
            points = points,
            onSuccess = {
                binding.buttonBuild.isEnabled = true
                binding.buttonBuild.visibility = View.GONE
                addBotMessage(getString(R.string.build_started))
                scrollToBottom()
            },
            onError = { err ->
                binding.buttonBuild.isEnabled = true
                addBotMessage(
                    getString(
                        if (err == "NETWORK") R.string.error_network else R.string.error_unknown
                    )
                )
                scrollToBottom()
            }
        )
    }

    // ---- بناء الرسائل ----

    private fun addUserMessage(text: String) {
        val density = resources.displayMetrics.density
        val bubble = makeBubble(text, true)
        bubble.maxWidth = (resources.displayMetrics.widthPixels * 0.8).toInt()
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val marginV = (3 * density).toInt()
        rowParams.setMargins(0, marginV, 0, marginV)
        row.layoutParams = rowParams
        row.addView(bubble)
        binding.layoutChat.addView(row)
        animateIn(row)
    }

    private fun addBotMessage(text: String) {
        val bubble = makeBubble(text, false)
        addBotRow(bubble)
    }

    /** صف رد البوت: أفاتار + فقاعة. */
    private fun addBotRow(content: View): View {
        val density = resources.displayMetrics.density
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.START or Gravity.TOP
        val avatar = ImageView(requireContext())
        avatar.setImageResource(R.drawable.bosla_logo_transparent)
        avatar.scaleType = ImageView.ScaleType.FIT_CENTER
        avatar.setBackgroundResource(R.drawable.bg_avatar_frame)
        val size = (28 * density).toInt()
        avatar.layoutParams = LinearLayout.LayoutParams(size, size)
        val pad = (2 * density).toInt()
        avatar.setPadding(pad, pad, pad, pad)
        row.addView(avatar)
        val contentParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        contentParams.marginStart = (8 * density).toInt()
        content.layoutParams = contentParams
        if (content is TextView) {
            content.maxWidth = (resources.displayMetrics.widthPixels * 0.78).toInt()
        }
        row.addView(content)
        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val marginV = (3 * density).toInt()
        rowParams.setMargins(0, marginV, 0, marginV)
        row.layoutParams = rowParams
        binding.layoutChat.addView(row)
        animateIn(row)
        return row
    }

    private fun makeBubble(text: String, isUser: Boolean): TextView {
        val density = resources.displayMetrics.density
        val tv = TextView(requireContext())
        tv.text = text
        tv.textSize = 15f
        val padH = (14 * density).toInt()
        val padV = (8 * density).toInt()
        tv.setPadding(padH, padV, padH, padV)
        if (isUser) {
            tv.setBackgroundResource(R.drawable.bg_bubble_user)
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            tv.setBackgroundResource(R.drawable.bg_bubble_bot)
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.bosla_navy))
        }
        return tv
    }

    /** بطاقة التقدير المضغوطة: التعقيد + النقاط + السعر (من تحليل الذكاء الاصطناعي). */
    private fun showEstimateCard(estimate: SupabaseClient.ChatEstimate) {
        val card = layoutInflater.inflate(R.layout.item_estimate_card, binding.layoutChat, false)
        val complexityView = card.findViewById<TextView>(R.id.text_estimate_complexity)
        val pointsView = card.findViewById<TextView>(R.id.text_estimate_points)
        val priceView = card.findViewById<TextView>(R.id.text_estimate_price)
        complexityView.text = getString(Estimate.labelFor(estimate.complexity))
        pointsView.text = getString(R.string.chat_estimate_points, estimate.points)
        priceView.text = getString(R.string.chat_estimate_price, formatNumber(estimate.priceSdg))
        binding.layoutChat.addView(card)
        animateIn(card)
    }

    // ---- مؤشر الكتابة المتحرك ----

    private fun showTyping() {
        typingStep = 0
        val bubble = makeBubble(thinkingSteps[0], false)
        typingText = bubble
        typingRow = addBotRow(bubble)
        handler.post(dotsRunnable)
        handler.postDelayed(stepRunnable, 700)
    }

    private fun stopTyping() {
        handler.removeCallbacks(dotsRunnable)
        handler.removeCallbacks(stepRunnable)
        typingRow?.let { binding.layoutChat.removeView(it) }
        typingRow = null
        typingText = null
    }

    private fun animateIn(view: View) {
        view.alpha = 0f
        view.translationY = 24f
        view.animate().alpha(1f).translationY(0f).setDuration(250).start()
    }

    private fun scrollToBottom() {
        binding.scrollChat.post { binding.scrollChat.fullScroll(View.FOCUS_DOWN) }
    }

    private fun formatNumber(n: Int): String = String.format("%,d", n)

    override fun onDestroyView() {
        chatScope.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }
}
