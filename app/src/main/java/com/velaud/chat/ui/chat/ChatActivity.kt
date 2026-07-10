package com.velaud.chat.ui.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.velaud.chat.databinding.ActivityChatBinding
import com.velaud.chat.model.ChatMessage
import com.velaud.chat.model.MessageRole
import com.velaud.chat.network.AiRequest
import com.velaud.chat.network.ApiClient
import com.velaud.chat.ui.main.AttachBottomSheet
import com.velaud.chat.ui.main.ModelBottomSheet
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var currentModel = "Claude Opus 4.7"
    private var chatId: String? = null
    private val thinkingHandler = Handler(Looper.getMainLooper())
    private var thinkingSeconds = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra("chat_id")
        currentModel = intent.getStringExtra("model") ?: "Claude Opus 4.7"
        binding.tvCurrentModel.text = currentModel

        setupRecyclerView()
        setupClickListeners()

        val initialMessage = intent.getStringExtra("initial_message")
        if (!initialMessage.isNullOrBlank()) {
            binding.etMessage.setText(initialMessage)
            sendMessage()
        } else if (chatId != null) {
            loadChatHistory()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = this@ChatActivity.adapter
        }
    }

    private fun setupClickListeners() {
        binding.btnHamburger.setOnClickListener { finish() }

        binding.btnNewChat.setOnClickListener {
            messages.clear()
            adapter.submitMessages(messages)
            chatId = null
            binding.etMessage.text?.clear()
        }

        binding.btnSend.setOnClickListener { sendMessage() }

        binding.btnPlus.setOnClickListener {
            val sheet = AttachBottomSheet.newInstance()
            sheet.show(supportFragmentManager, "attach")
        }

        binding.btnModelPill.setOnClickListener {
            val sheet = ModelBottomSheet.newInstance { modelName ->
                currentModel = modelName
                binding.tvCurrentModel.text = modelName
            }
            sheet.show(supportFragmentManager, "model")
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                if (hasText) {
                    binding.btnSend.setBackgroundResource(com.velaud.chat.R.drawable.bg_send_active)
                    binding.btnSend.setColorFilter(android.graphics.Color.BLACK)
                } else {
                    binding.btnSend.setBackgroundResource(com.velaud.chat.R.drawable.bg_send_inactive)
                    binding.btnSend.setColorFilter(android.graphics.Color.parseColor("#6e6e6e"))
                }
            }
        })
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isBlank()) return

        binding.etMessage.text?.clear()
        binding.btnSend.setBackgroundResource(com.velaud.chat.R.drawable.bg_send_inactive)
        binding.btnSend.setColorFilter(android.graphics.Color.parseColor("#6e6e6e"))

        val userMessage = ChatMessage(
            role = MessageRole.USER,
            content = text
        )
        messages.add(userMessage)

        // Add thinking placeholder
        val thinkingMessage = ChatMessage(
            role = MessageRole.THINKING,
            content = "",
            thinkingText = "",
            thinkingSeconds = 0
        )
        messages.add(thinkingMessage)
        adapter.submitMessages(messages.toList())
        scrollToBottom()

        startThinkingTimer()
        callAiApi(text)
    }

    private fun startThinkingTimer() {
        thinkingSeconds = 0
        thinkingHandler.post(object : Runnable {
            override fun run() {
                thinkingSeconds++
                val idx = messages.indexOfLast { it.role == MessageRole.THINKING }
                if (idx >= 0) {
                    messages[idx] = messages[idx].copy(thinkingSeconds = thinkingSeconds)
                    adapter.updateItem(idx, messages[idx])
                    thinkingHandler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun stopThinkingTimer() {
        thinkingHandler.removeCallbacksAndMessages(null)
    }

    private fun callAiApi(userText: String) {
        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val token = user?.getIdToken(false)?.result?.token ?: ""

                val history = messages
                    .filter { it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT }
                    .map { mapOf("role" to it.role.value, "content" to it.content) }

                val request = AiRequest(
                    model = currentModel,
                    messages = history,
                    chatId = chatId,
                    webSearch = false
                )

                val response = ApiClient.chatService.sendMessage("Bearer $token", request)
                if (response.isSuccessful) {
                    val body = response.body()
                    val aiContent = body?.get("content") as? String ?: ""
                    val thinking = body?.get("thinking") as? String ?: ""
                    val newChatId = body?.get("chatId") as? String
                    if (newChatId != null) chatId = newChatId

                    stopThinkingTimer()

                    // Replace thinking placeholder with real AI message
                    val thinkingIdx = messages.indexOfLast { it.role == MessageRole.THINKING }
                    if (thinkingIdx >= 0) {
                        messages[thinkingIdx] = ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = aiContent,
                            thinkingText = thinking,
                            thinkingSeconds = thinkingSeconds
                        )
                    } else {
                        messages.add(ChatMessage(
                            role = MessageRole.ASSISTANT,
                            content = aiContent,
                            thinkingText = thinking,
                            thinkingSeconds = thinkingSeconds
                        ))
                    }
                    adapter.submitMessages(messages.toList())
                    scrollToBottom()
                } else {
                    stopThinkingTimer()
                    handleApiError(response.code(), response.errorBody()?.string())
                }
            } catch (e: Exception) {
                stopThinkingTimer()
                removeThinking()
                Toast.makeText(this@ChatActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleApiError(code: Int, errorBody: String?) {
        removeThinking()
        val msg = when (code) {
            401 -> "Kimlik doğrulama hatası. Lütfen tekrar giriş yapın."
            503 -> "Model şu an meşgul. Lütfen tekrar deneyin."
            429 -> "İstek limiti aşıldı. Biraz bekleyin."
            else -> "Sunucu hatası ($code). Lütfen tekrar deneyin."
        }
        messages.add(ChatMessage(role = MessageRole.ERROR, content = msg))
        adapter.submitMessages(messages.toList())
        scrollToBottom()
    }

    private fun removeThinking() {
        val idx = messages.indexOfLast { it.role == MessageRole.THINKING }
        if (idx >= 0) {
            messages.removeAt(idx)
            adapter.submitMessages(messages.toList())
        }
    }

    private fun loadChatHistory() {
        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val token = user?.getIdToken(false)?.result?.token ?: return@launch
                val cId = chatId ?: return@launch
                val response = ApiClient.chatService.getChatMessages("Bearer $token", cId)
                if (response.isSuccessful) {
                    val chatMessages = response.body() ?: emptyList()
                    messages.clear()
                    messages.addAll(chatMessages.map {
                        ChatMessage(
                            role = if (it.role == "user") MessageRole.USER else MessageRole.ASSISTANT,
                            content = it.content,
                            thinkingText = it.thinking ?: ""
                        )
                    })
                    adapter.submitMessages(messages.toList())
                    scrollToBottom()
                }
            } catch (e: Exception) {
                // Silent
            }
        }
    }

    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            binding.rvChat.post {
                binding.rvChat.smoothScrollToPosition(messages.size - 1)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopThinkingTimer()
    }
}
