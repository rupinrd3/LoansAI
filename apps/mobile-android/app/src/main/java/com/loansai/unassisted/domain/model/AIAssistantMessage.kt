package com.loansai.unassisted.domain.model

import java.time.LocalDateTime

/**
 * Domain model for AI Assistant interactions
 */
data class AIAssistantMessage(
    val id: String,
    val applicationId: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val messageType: MessageType,
    val message: String,
    val metadata: Map<String, Any>? = null,
    val screen: String? = null,
    val isRead: Boolean = false
)

/**
 * Chat conversation model
 */
data class AIConversation(
    val id: String,
    val applicationId: String? = null,
    val userId: String,
    val messages: List<AIAssistantMessage> = emptyList(),
    val startedAt: LocalDateTime = LocalDateTime.now(),
    val lastMessageAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Message type enum
 */
enum class MessageType {
    USER_MESSAGE,
    AI_RESPONSE,
    AI_SUGGESTION,
    SYSTEM_MESSAGE,
    ERROR_MESSAGE
}