package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName
import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.AIConversation
import com.loansai.unassisted.domain.model.MessageType
import java.time.LocalDateTime

/**
 * Data Transfer Object for AI Assistant Message
 */
data class AIAssistantMessageDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("application_id")
    val applicationId: String? = null,
    
    @SerializedName("timestamp")
    val timestamp: String = LocalDateTime.now().toString(),
    
    @SerializedName("message_type")
    val messageType: String,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("metadata")
    val metadata: Map<String, Any>? = null,
    
    @SerializedName("screen")
    val screen: String? = null,
    
    @SerializedName("is_read")
    val isRead: Boolean = false
)

/**
 * Data Transfer Object for AI Conversation
 */
data class AIConversationDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("application_id")
    val applicationId: String? = null,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("messages")
    val messages: List<AIAssistantMessageDto> = emptyList(),
    
    @SerializedName("started_at")
    val startedAt: String = LocalDateTime.now().toString(),
    
    @SerializedName("last_message_at")
    val lastMessageAt: String = LocalDateTime.now().toString()
)

/**
 * AI Assistant request model
 */
data class AIAssistantRequest(
    @SerializedName("query")
    val query: String,
    
    @SerializedName("application_id")
    val applicationId: String? = null,
    
    @SerializedName("screen")
    val screen: String? = null,
    
    @SerializedName("context")
    val context: Map<String, Any>? = null
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun AIAssistantMessageDto.toAIAssistantMessage(): AIAssistantMessage {
    return AIAssistantMessage(
        id = id,
        applicationId = applicationId,
        timestamp = LocalDateTime.parse(timestamp),
        messageType = MessageType.valueOf(messageType),
        message = message,
        metadata = metadata,
        screen = screen,
        isRead = isRead
    )
}

/**
 * Mapping extension function to convert domain model to DTO
 */
fun AIAssistantMessage.toAIAssistantMessageDto(): AIAssistantMessageDto {
    return AIAssistantMessageDto(
        id = id,
        applicationId = applicationId,
        timestamp = timestamp.toString(),
        messageType = messageType.name,
        message = message,
        metadata = metadata,
        screen = screen,
        isRead = isRead
    )
}

/**
 * Mapping extension function to convert DTO to domain model
 */
fun AIConversationDto.toAIConversation(): AIConversation {
    return AIConversation(
        id = id,
        applicationId = applicationId,
        userId = userId,
        messages = messages.map { it.toAIAssistantMessage() },
        startedAt = LocalDateTime.parse(startedAt),
        lastMessageAt = LocalDateTime.parse(lastMessageAt)
    )
}