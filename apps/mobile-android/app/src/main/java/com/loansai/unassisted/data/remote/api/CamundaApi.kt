package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.camunda.CamundaMessageRequest
import com.loansai.unassisted.data.model.camunda.ProcessInstanceResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API interface for Camunda workflow engine
 */
interface CamundaApi {
    
    /**
     * Start a process instance by sending a message
     */
    @POST("engine-rest/message")
    suspend fun sendMessage(
        @Body request: CamundaMessageRequest
    ): Response<ProcessInstanceResponse>
    
    /**
     * Correlate a message to an existing process instance
     */
    @POST("engine-rest/message")
    suspend fun correlateMessage(
        @Body request: CamundaMessageRequest
    ): Response<Void>
}

