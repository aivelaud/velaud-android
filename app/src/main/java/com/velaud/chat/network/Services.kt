package com.velaud.chat.network

import retrofit2.Response
import retrofit2.http.*

// ─── Data classes ────────────────────────────────────────────────────────────

data class SendMessageRequest(
    val message: String,
    val model: String,
    val conversationId: String? = null,
    val webSearch: Boolean = false,
    val showThinking: Boolean = false
)

data class ChatHistoryItem(
    val id: String,
    val title: String,
    val updatedAt: String = ""
)

data class ChatMessageItem(
    val id: String = "",
    val role: String,
    val content: String,
    val thinking: String? = null,
    val model: String? = null
)

data class ChatHistoryResponse(
    val conversations: List<ChatHistoryItem>
)

data class ChatMessagesResponse(
    val messages: List<ChatMessageItem>
)

// ─── Services ────────────────────────────────────────────────────────────────

interface AuthService {
    @POST("api/auth/send-code")
    suspend fun sendVerificationCode(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/verify-code")
    suspend fun verifyCode(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/sync-google")
    suspend fun syncGoogle(@Body body: Map<String, String>): Response<Map<String, Any>>
}

interface ChatService {
    // Note: /api/chat/message returns text/event-stream (SSE).
    // Use OkHttp directly in ChatActivity for streaming; this interface
    // is kept for history/messages JSON endpoints only.

    @GET("api/chat/history")
    suspend fun getChatHistory(
        @Header("Authorization") token: String
    ): Response<ChatHistoryResponse>

    @GET("api/chat/{chatId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String
    ): Response<ChatMessagesResponse>

    @DELETE("api/chat/{chatId}")
    suspend fun deleteConversation(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String
    ): Response<Map<String, Any>>
}
