package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.AIConversation
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.MessageType
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for AI assistant operations
 */
interface AIRepository {
    
    /**
     * Get AI assistance for a query
     *
     * @param query The user's query
     * @param applicationId The ID of the current application (if any)
     * @param screen The current screen the user is on
     * @param applicationContext Additional context about the application state
     * @return Flow of Resource<AIAssistantMessage> with the AI response
     */
    suspend fun getAssistance(
        query: String,
        applicationId: String? = null,
        screen: String? = null,
        applicationContext: Map<String, Any>? = null
    ): Flow<Resource<AIAssistantMessage>>
    
    /**
     * Get AI suggestions for the current screen/context
     *
     * @param screen The current screen
     * @param applicationId The ID of the current application (if any)
     * @param applicationContext Additional context about the application state
     * @return Flow of Resource<List<AIAssistantMessage>> with AI suggestions
     */
    suspend fun getSuggestions(
        screen: String,
        applicationId: String? = null,
        applicationContext: Map<String, Any>? = null
    ): Flow<Resource<List<AIAssistantMessage>>>
    
    /**
     * Get conversation history
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<AIConversation> with conversation history
     */
    fun getConversationHistory(applicationId: String? = null): Flow<Resource<AIConversation>>
    
    /**
     * Save a message to the conversation history
     *
     * @param applicationId The ID of the application (if any)
     * @param message The message content
     * @param messageType The type of message
     * @param screen The current screen
     * @return Flow of Resource<AIAssistantMessage> with the saved message
     */
    suspend fun saveMessage(
        applicationId: String? = null,
        message: String,
        messageType: MessageType,
        screen: String? = null
    ): Flow<Resource<AIAssistantMessage>>
    
    /**
     * Mark messages as read
     *
     * @param messageIds The IDs of the messages to mark as read
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun markMessagesAsRead(messageIds: List<String>): Flow<Resource<Boolean>>
    
    /**
     * Clear conversation history
     *
     * @param applicationId The ID of the application (if any)
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun clearConversationHistory(applicationId: String? = null): Flow<Resource<Boolean>>
    
    /**
     * Show AI assistant for the current context
     *
     * @param context Additional context about the current state
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun showAssistant(context: Map<String, Any>): Flow<Resource<Boolean>>
    
    /**
     * Get a final review of the application from the AI assistant
     *
     * @param application The loan application to review
     * @return List of suggestions from the AI
     */
    suspend fun getFinalApplicationReview(application: LoanApplication): List<String>
}