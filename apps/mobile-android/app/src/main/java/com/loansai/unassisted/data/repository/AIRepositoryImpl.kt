package com.loansai.unassisted.data.repository

import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.data.model.AIAssistantMessageDto
import com.loansai.unassisted.data.model.AIAssistantRequest
import com.loansai.unassisted.data.model.toAIAssistantMessage
import com.loansai.unassisted.data.model.toAIConversation
import com.loansai.unassisted.data.remote.api.AIApi
import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.AIConversation
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.MessageType
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.util.constants.PreferenceConstants
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import com.loansai.unassisted.domain.usecase.ai.impl.GPTAIAssistantService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AI Repository
 */
@Singleton
class AIRepositoryImpl @Inject constructor(
    private val aiApi: AIApi,
    private val preferencesDataSource: PreferencesDataSource,
    private val gson: Gson,
    private val aiAssistantService: GPTAIAssistantService  
) : AIRepository {

    private val messagesKey = PreferenceConstants.PREF_AI_MESSAGES

    /**
     * Get AI assistance for a query
     */
    override suspend fun getAssistance(
        query: String,
        applicationId: String?,
        screen: String?,
        applicationContext: Map<String, Any>?
    ): Flow<Resource<AIAssistantMessage>> = flow {
        emit(Resource.Loading())
        
        try {
            // Save user query to history
            val userMessage = AIAssistantMessage(
                id = UUID.randomUUID().toString(),
                applicationId = applicationId,
                timestamp = LocalDateTime.now(),
                messageType = MessageType.USER_MESSAGE,
                message = query,
                screen = screen,
                isRead = true
            )
            saveMessageToLocal(userMessage)
            
            // Create API request
            // Process query using OpenAI service
            val aiMessage = aiAssistantService.processUserQuery(
                query = query,
                screen = screen,
                applicationId = applicationId,
                applicationContext = applicationContext
            )

            // Save AI response to history
            saveMessageToLocal(aiMessage)

            emit(Resource.Success(aiMessage))
        } catch (e: Exception) {
            val errorMessage = "Error getting AI assistance: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // Create error message
            val errorMsg = AIAssistantMessage(
                id = UUID.randomUUID().toString(),
                applicationId = applicationId,
                timestamp = LocalDateTime.now(),
                messageType = MessageType.ERROR_MESSAGE,
                message = "Sorry, something went wrong. Please try again later.",
                screen = screen,
                isRead = true
            )
            saveMessageToLocal(errorMsg)
            
            emit(Resource.Error(errorMessage))
        } 
    }

    /**
     * Get AI suggestions for the current screen/context
     */
    override suspend fun getSuggestions(
        screen: String,
        applicationId: String?,
        applicationContext: Map<String, Any>?
    ): Flow<Resource<List<AIAssistantMessage>>> = flow {
        emit(Resource.Loading())

        try {
            // Convert Map<String, Any>? to Map<String, String>?
            val stringContext = applicationContext?.mapValues { it.value.toString() }

            // Use the converted map
            val response = aiApi.getSuggestions(screen, applicationId, stringContext)

            // Rest of your code...
            
            if (response.isSuccessful && response.body() != null) {
                val suggestions = response.body()!!.map { it.toAIAssistantMessage() }
                
                // Save suggestions to local
                suggestions.forEach { saveMessageToLocal(it) }
                
                emit(Resource.Success(suggestions))
            } else {
                val errorMessage = "Failed to get AI suggestions: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting AI suggestions: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Get conversation history
     */
    override fun getConversationHistory(applicationId: String?): Flow<Resource<AIConversation>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try to get from API first
            val response = aiApi.getConversationHistory(applicationId)
            
            if (response.isSuccessful && response.body() != null) {
                val conversation = response.body()!!.toAIConversation()
                
                // Cache conversation
                conversation.messages.forEach { saveMessageToLocal(it) }
                
                emit(Resource.Success(conversation))
            } else {
                // If API fails, get from local storage
                val messages = getLocalMessages(applicationId)
                
                val conversationId = UUID.randomUUID().toString()
                val userId = "local_user" // This should be replaced with actual user ID
                
                val conversation = AIConversation(
                    id = conversationId,
                    applicationId = applicationId,
                    userId = userId,
                    messages = messages,
                    startedAt = messages.minOfOrNull { it.timestamp } ?: LocalDateTime.now(),
                    lastMessageAt = messages.maxOfOrNull { it.timestamp } ?: LocalDateTime.now()
                )
                
                emit(Resource.Success(conversation))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting conversation history: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // If API fails, get from local storage
            val messages = getLocalMessages(applicationId)
            
            if (messages.isNotEmpty()) {
                val conversationId = UUID.randomUUID().toString()
                val userId = "local_user" // This should be replaced with actual user ID
                
                val conversation = AIConversation(
                    id = conversationId,
                    applicationId = applicationId,
                    userId = userId,
                    messages = messages,
                    startedAt = messages.minOfOrNull { it.timestamp } ?: LocalDateTime.now(),
                    lastMessageAt = messages.maxOfOrNull { it.timestamp } ?: LocalDateTime.now()
                )
                
                emit(Resource.Success(conversation))
            } else {
                emit(Resource.Error(errorMessage))
            }
        }
    }

    /**
     * Save a message to the conversation history
     */
    override suspend fun saveMessage(
        applicationId: String?,
        message: String,
        messageType: MessageType,
        screen: String?
    ): Flow<Resource<AIAssistantMessage>> = flow {
        emit(Resource.Loading())
        
        try {
            val messageId = UUID.randomUUID().toString()
            val aiMessage = AIAssistantMessage(
                id = messageId,
                applicationId = applicationId,
                timestamp = LocalDateTime.now(),
                messageType = messageType,
                message = message,
                screen = screen,
                isRead = messageType == MessageType.USER_MESSAGE // User messages are auto-read
            )
            
            // Save to local
            saveMessageToLocal(aiMessage)
            
            // Save to API (if applicable)
            if (messageType != MessageType.ERROR_MESSAGE) {
                try {
                    val messageDto = AIAssistantMessageDto(
                        id = aiMessage.id,
                        applicationId = aiMessage.applicationId,
                        timestamp = aiMessage.timestamp.toString(),
                        messageType = aiMessage.messageType.name,
                        message = aiMessage.message,
                        screen = aiMessage.screen,
                        isRead = aiMessage.isRead
                    )
                    
                    aiApi.saveMessage(messageDto)
                } catch (e: Exception) {
                    // Non-critical error - just log and continue
                    AppLogger.e("Failed to save message to API: ${e.message}", e)
                }
            }
            
            emit(Resource.Success(aiMessage))
        } catch (e: Exception) {
            val errorMessage = "Error saving message: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Mark messages as read
     */
    override suspend fun markMessagesAsRead(messageIds: List<String>): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Mark as read in local storage
            val messages = getLocalMessages(null)
            val updatedMessages = messages.map { message ->
                if (messageIds.contains(message.id)) {
                    message.copy(isRead = true)
                } else {
                    message
                }
            }
            saveMessagesToLocal(updatedMessages)
            
            // Mark as read in API
            try {
                val response = aiApi.markMessagesAsRead(messageIds)
                if (!response.isSuccessful) {
                    AppLogger.w("Failed to mark messages as read in API: ${response.message()}")
                }
            } catch (e: Exception) {
                // Non-critical error - just log and continue
                AppLogger.e("Failed to mark messages as read in API: ${e.message}", e)
            }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            val errorMessage = "Error marking messages as read: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // If local storage fails, try to continue with API
            try {
                val response = aiApi.markMessagesAsRead(messageIds)
                if (response.isSuccessful) {
                    emit(Resource.Success(true))
                } else {
                    emit(Resource.Error(errorMessage))
                }
            } catch (e2: Exception) {
                emit(Resource.Error(errorMessage))
            }
        }
    }

    /**
     * Clear conversation history
     */
    override suspend fun clearConversationHistory(applicationId: String?): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Clear from local storage
            if (applicationId != null) {
                val allMessages = getLocalMessages(null)
                val filteredMessages = allMessages.filter { it.applicationId != applicationId }
                saveMessagesToLocal(filteredMessages)
            } else {
                preferencesDataSource.remove(messagesKey)
            }
            
            // Clear from API
            try {
                val response = aiApi.clearConversationHistory(applicationId)
                if (!response.isSuccessful) {
                    AppLogger.w("Failed to clear conversation in API: ${response.message()}")
                }
            } catch (e: Exception) {
                // Non-critical error - just log and continue
                AppLogger.e("Failed to clear conversation in API: ${e.message}", e)
            }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            val errorMessage = "Error clearing conversation history: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
    * Show AI assistant for the current context
    */
    override suspend fun showAssistant(context: Map<String, Any>): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get the screen from context if available
            val screen = context["screen"] as? String
            
            // Get the application ID if available
            val applicationId = context["application_id"] as? String
            
            // Create a special system message to trigger AI assistant
            val message = "SYSTEM: Show assistant for ${screen ?: "current screen"}"
            
            // Save message to trigger assistant
            saveMessage(
                applicationId = applicationId,
                message = message,
                messageType = MessageType.SYSTEM_MESSAGE,
                screen = screen
            ).collect { /* Collect and ignore result */ }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            val errorMessage = "Error showing AI assistant: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Get a final review of the application from the AI assistant
     *
     * @param application The loan application to review
     * @return List of suggestions from the AI
     */
    override suspend fun getFinalApplicationReview(application: LoanApplication): List<String> {
        try {
            // Create a context map with relevant information
            val context = mapOf(
                "application_id" to application.id,
                "application_status" to application.applicationStatus.name,
                "pan_verified" to (application.panDetails?.isVerified ?: false),
                "has_employment_details" to (application.employmentDetails != null),
                "documents_count" to application.documents.size,
                "has_loan_offer" to (application.loanOffer != null)
            )
            
            // Create a request for the AI to review the application
            val request = AIAssistantRequest(
                query = "Review this loan application and provide suggestions.",
                applicationId = application.id,
                screen = "REVIEW_AND_SUBMIT",
                context = context
            )
            
            // Try to get the assistance from the AI API
            val response = aiApi.getAssistance(request)
            
            if (response.isSuccessful && response.body() != null) {
                val aiMessage = response.body()!!.toAIAssistantMessage()
                
                // Save the AI response to history
                saveMessageToLocal(aiMessage)
                
                // Parse the message into a list of suggestions
                // For this implementation, we'll simply split by newlines and filter empty lines
                return aiMessage.message.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }
            
            // Fallback suggestions if the API call fails
            return listOf(
                "Make sure all your personal information is accurate.",
                "Double-check that your employment details are correct.",
                "Verify all uploaded documents are clearly visible.",
                "Confirm that your selected loan amount and tenure match your needs."
            )
        } catch (e: Exception) {
            AppLogger.e("Error getting final application review: ${e.message}", e)
            
            // Return fallback suggestions in case of an error
            return listOf(
                "Make sure all your personal information is accurate.",
                "Double-check that your employment details are correct.",
                "Verify all uploaded documents are clearly visible.",
                "Confirm that your selected loan amount and tenure match your needs."
            )
        }
    }

    /**
     * Helper function to save a message to local storage
     */
    private suspend fun saveMessageToLocal(message: AIAssistantMessage) {
        val messages = getLocalMessages(null).toMutableList()
        
        // Remove message with same ID if exists
        messages.removeIf { it.id == message.id }
        
        // Add the new message
        messages.add(message)
        
        // Save all messages
        saveMessagesToLocal(messages)
    }
    
    /**
     * Helper function to save all messages to local storage
     */
    private suspend fun saveMessagesToLocal(messages: List<AIAssistantMessage>) {
        val json = gson.toJson(messages)
        preferencesDataSource.saveString(messagesKey, json)
    }
    
    /**
     * Helper function to get messages from local storage
     */
    private suspend fun getLocalMessages(applicationId: String?): List<AIAssistantMessage> {
        val json = preferencesDataSource.getString(messagesKey)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<AIAssistantMessage>>() {}.type
                val allMessages: List<AIAssistantMessage> = gson.fromJson(json, type)
                
                // Filter by applicationId if needed
                if (applicationId != null) {
                    allMessages.filter { it.applicationId == applicationId }
                } else {
                    allMessages
                }
            } catch (e: Exception) {
                AppLogger.e("Error parsing stored messages: ${e.message}", e)
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}