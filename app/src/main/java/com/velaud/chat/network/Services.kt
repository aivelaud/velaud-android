package com.velaud.chat.network

import retrofit2.Response
import retrofit2.http.*

// ─── Data classes ────────────────────────────────────────────────────────────

data class AiRequest(
    val model: String,
    val messages: List<Map<String, String>>,
    val chatId: String? = null,
    val webSearch: Boolean = false
)

data class ChatHistoryItem(
    val id: String,
    val title: String,
    val updatedAt: String = ""
)

data class ChatMessageItem(
    val role: String,
    val content: String,
    val thinking: String? = null
)

// ─── Services ────────────────────────────────────────────────────────────────

interface AuthService {
    @POST("api/auth/send-code")
    suspend fun sendVerificationCode(@Body body: Map<String, String>): Response<Map<String, Any>>

    @POST("api/auth/verify-code")
    suspend fun verifyCode(@Body body: Map<String, String>): Response<Map<String, Any>>
}

interface ChatService {
    @POST("api/chat/message")
    suspend fun sendMessage(
        @Header("Authorization") token: String,
        @Body request: AiRequest
    ): Response<Map<String, Any>>

    @GET("api/chat/history")
    suspend fun getChatHistory(
        @Header("Authorization") token: String
    ): Response<List<ChatHistoryItem>>

    @GET("api/chat/{chatId}/messages")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Path("chatId") chatId: String
    ): Response<List<ChatMessageItem>>
}
