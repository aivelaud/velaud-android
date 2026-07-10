package com.velaud.chat.ui.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.velaud.chat.BuildConfig
import com.velaud.chat.databinding.ActivityChatBinding
import com.velaud.chat.model.ChatMessage
import com.velaud.chat.model.MessageRole
import com.velaud.chat.network.ApiClient
import com.velaud.chat.ui.main.AttachBottomSheet
import com.velaud.chat.ui.main.ModelBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private var currentModel = "claude-opus-4-7"
    private var currentModelDisplay = "Claude Opus 4.7"
    private var chatId: String? = null
    private val thinkingHandler = Handler(Looper.getMainLooper())
    private var thinkingSeconds = 0

    // Dedicated SSE client with long timeouts
    private val sseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // no read timeout for streaming
        .build()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        chatId = intent.getStringExtra("chat_id")
        currentModel = intent.getStringExtra("model") ?: "claude-opus-4-7"
        currentModelDisplay = intent.getStringExtra("model_display") ?: "Claude Opus 4.7"
        binding.tvCurrentModel.text = currentModelDisplay

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
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@ChatActivity).apply {
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
            AttachBottomSheet.newInstance().show(supportFragmentManager, "attach")
        }

        binding.btnModelPill.setOnClickListener {
            ModelBottomSheet.newInstance { modelId, modelName ->
                currentModel = modelId
                currentModelDisplay = modelName
                binding.tvCurrentModel.text = modelName
            }.show(supportFragmentManager, "model")
        }

        binding.etMessage.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) {
                val hasText = !s.isNullOrBlank()
                binding.btnSend.setBackgroundResource(
                    if (hasText) com.velaud.chat.R.drawable.bg_send_active
                    else com.velaud.chat.R.drawable.bg_send_inactive
                )
                binding.btnSend.setColorFilter(
                    if (hasText) android.graphics.Color.BLACK
                    else android.graphics.Color.parseColor("#6e6e6e")
                )
            }
        })
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isBlank()) return

        binding.etMessage.text?.clear()
        binding.btnSend.setBackgroundResource(com.velaud.chat.R.drawable.bg_send_inactive)
        binding.btnSend.setColorFilter(android.graphics.Color.parseColor("#6e6e6e"))

        messages.add(ChatMessage(role = MessageRole.USER, content = text))

        val thinkingMsg = ChatMessage(role = MessageRole.THINKING, content = "", thinkingText = "", thinkingSeconds = 0)
        messages.add(thinkingMsg)
        adapter.submitMessages(messages.toList())
        scrollToBottom()

        startThinkingTimer()
        callAiApiSse(text)
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

    /**
     * Stream a chat message via SSE from the backend /api/chat/message endpoint.
     * The backend sends events: thinking_start, thinking_delta, thinking_end, delta, end, error.
     */
    private fun callAiApiSse(userText: String) {
        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val idToken = withContext(Dispatchers.IO) {
                    user?.getIdToken(false)?.result?.token ?: ""
                }

                val bodyJson = JSONObject().apply {
                    put("message", userText)
                    put("model", currentModel)
                    if (chatId != null) put("conversationId", chatId)
                    put("webSearch", false)
                    put("showThinking", false)
                }.toString()

                val request = Request.Builder()
                    .url("${BuildConfig.BACKEND_URL}/api/chat/message")
                    .addHeader("Authorization", "Bearer $idToken")
                    .addHeader("Accept", "text/event-stream")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()

                var accumulatedContent = ""
                var accumulatedThinking = ""
                var isThinking = false

                // Remove thinking placeholder — replace with empty AI bubble as we stream in
                val thinkingIdx = messages.indexOfLast { it.role == MessageRole.THINKING }

                withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        val factory = EventSources.createFactory(sseClient)
                        val eventSource = factory.newEventSource(request, object : EventSourceListener() {
                            override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                                if (data == "[DONE]") return
                                try {
                                    val json = JSONObject(data)
                                    when (json.optString("type")) {
                                        "thinking_start" -> {
                                            isThinking = true
                                        }
                                        "thinking_delta" -> {
                                            accumulatedThinking += json.optString("text")
                                        }
                                        "thinking_end" -> {
                                            isThinking = false
                                        }
                                        "delta" -> {
                                            accumulatedContent += json.optString("text")
                                            runOnUiThread {
                                                val idx = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
                                                if (idx < 0) {
                                                    // Replace thinking placeholder
                                                    val ti = messages.indexOfLast { it.role == MessageRole.THINKING }
                                                    if (ti >= 0) {
                                                        messages[ti] = ChatMessage(
                                                            role = MessageRole.ASSISTANT,
                                                            content = accumulatedContent,
                                                            thinkingText = accumulatedThinking,
                                                            thinkingSeconds = thinkingSeconds
                                                        )
                                                        stopThinkingTimer()
                                                        adapter.updateItem(ti, messages[ti])
                                                    } else {
                                                        messages.add(ChatMessage(role = MessageRole.ASSISTANT, content = accumulatedContent))
                                                        adapter.submitMessages(messages.toList())
                                                    }
                                                } else {
                                                    messages[idx] = messages[idx].copy(content = accumulatedContent)
                                                    adapter.updateItem(idx, messages[idx])
                                                }
                                                scrollToBottom()
                                            }
                                        }
                                        "end" -> {
                                            val newChatId = json.optString("conversationId").takeIf { it.isNotBlank() }
                                            if (newChatId != null) chatId = newChatId
                                            runOnUiThread { stopThinkingTimer() }
                                            if (cont.isActive) cont.resume(Unit)
                                        }
                                        "error" -> {
                                            val errMsg = json.optString("error", "AI hatası")
                                            runOnUiThread {
                                                stopThinkingTimer()
                                                replaceThinkingWithError(errMsg)
                                            }
                                            if (cont.isActive) cont.resume(Unit)
                                        }
                                    }
                                } catch (_: Exception) {}
                            }

                            override fun onFailure(source: EventSource, t: Throwable?, response: Response?) {
                                runOnUiThread {
                                    stopThinkingTimer()
                                    val code = response?.code ?: 0
                                    when {
                                        code == 401 -> { finish() } // session expired
                                        code == 503 -> replaceThinkingWithError("Servis şu anda kullanılamıyor")
                                        else -> replaceThinkingWithError(t?.message ?: "Bağlantı hatası")
                                    }
                                }
                                if (cont.isActive) cont.resumeWithException(t ?: Exception("SSE failed"))
                            }
                        })
                        cont.invokeOnCancellation { eventSource.cancel() }
                    }
                }
            } catch (e: Exception) {
                stopThinkingTimer()
                replaceThinkingWithError(e.message ?: "Bilinmeyen hata")
            }
        }
    }

    private fun replaceThinkingWithError(msg: String) {
        val idx = messages.indexOfLast { it.role == MessageRole.THINKING }
        if (idx >= 0) {
            messages[idx] = ChatMessage(role = MessageRole.ERROR, content = msg)
            adapter.updateItem(idx, messages[idx])
        } else {
            messages.add(ChatMessage(role = MessageRole.ERROR, content = msg))
            adapter.submitMessages(messages.toList())
        }
        scrollToBottom()
    }

    private fun loadChatHistory() {
        val cId = chatId ?: return
        lifecycleScope.launch {
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val token = withContext(Dispatchers.IO) {
                    user?.getIdToken(false)?.result?.token ?: ""
                }
                val response = ApiClient.chatService.getChatMessages("Bearer $token", cId)
                if (response.isSuccessful) {
                    val items = response.body()?.messages ?: emptyList()
                    messages.clear()
                    items.forEach { item ->
                        val role = when (item.role) {
                            "user" -> MessageRole.USER
                            "assistant" -> MessageRole.ASSISTANT
                            else -> return@forEach
                        }
                        messages.add(ChatMessage(
                            role = role,
                            content = item.content,
                            thinkingText = item.thinking ?: ""
                        ))
                    }
                    adapter.submitMessages(messages.toList())
                    scrollToBottom()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ChatActivity, "Geçmiş yüklenemedi", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scrollToBottom() {
        if (messages.isNotEmpty()) {
            binding.rvChat.smoothScrollToPosition(messages.size - 1)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopThinkingTimer()
    }
}
