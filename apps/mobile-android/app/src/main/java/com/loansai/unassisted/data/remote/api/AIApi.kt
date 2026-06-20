package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.AIAssistantMessageDto
import com.loansai.unassisted.data.model.AIAssistantRequest
import com.loansai.unassisted.data.model.AIConversationDto
import com.loansai.unassisted.util.constants.ApiConstants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API interface for AI assistant-related endpoints
 */
interface AIApi {
    
    /**
     * Get AI assistance for a user query
     */
    @POST(ApiConstants.ENDPOINT_AI_ASSISTANT)
    suspend fun getAssistance(
        @Body request: AIAssistantRequest
    ): Response<AIAssistantMessageDto>
    
    /**
     * Get AI suggestions for current context
     */
    @GET("ai/suggestions")
    suspend fun getSuggestions(
        @Query("screen") screen: String,
        @Query("applicationId") applicationId: String?,
        @Query("context") context: Map<String, String>?
    ): Response<List<AIAssistantMessageDto>>
    
    /**
     * Get conversation history
     */
    @GET("ai/conversation")
    suspend fun getConversationHistory(
        @Query("applicationId") applicationId: String?
    ): Response<AIConversationDto>
    
    /**
     * Save a message to conversation history
     */
    @POST("ai/message")
    suspend fun saveMessage(
        @Body message: AIAssistantMessageDto
    ): Response<AIAssistantMessageDto>
    
    /**
     * Mark messages as read
     */
    @PUT("ai/messages/read")
    suspend fun markMessagesAsRead(
        @Body messageIds: List<String>
    ): Response<Map<String, Boolean>>
    
    /**
     * Clear conversation history
     */
    @DELETE("ai/conversation")
    suspend fun clearConversationHistory(
        @Query("applicationId") applicationId: String?
    ): Response<Map<String, Boolean>>
}