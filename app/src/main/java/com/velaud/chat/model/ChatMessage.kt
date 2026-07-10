package com.velaud.chat.model

data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val thinkingText: String? = null,
    val thinkingSeconds: Int? = null
)

enum class MessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
    THINKING("thinking"),
    ERROR("error")
}
