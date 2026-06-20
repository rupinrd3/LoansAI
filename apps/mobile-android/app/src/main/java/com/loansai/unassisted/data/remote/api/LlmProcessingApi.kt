package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.ProcessDocumentRequest
import com.loansai.unassisted.data.model.ProcessDocumentResponse
import com.loansai.unassisted.data.model.RecalculateObligationRequest
import com.loansai.unassisted.data.model.RecalculateObligationResponse
import com.loansai.unassisted.util.constants.ApiConstants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API interface for LLM processing endpoints
 * This API allows interaction with backend LLM services for document processing
 * and obligation recalculation as per v1.5.0 requirements
 */
interface LlmProcessingApi {
    
    /**
     * Process a document using the backend LLM service
     * 
     * @param request The document processing request with application ID, document ID, and other metadata
     * @return Response containing the document processing result or status
     */
    @POST(ApiConstants.ENDPOINT_PROCESS_DOCUMENT_LLM)
    suspend fun processDocument(
        @Body request: ProcessDocumentRequest
    ): Response<ProcessDocumentResponse>
    
    /**
     * Recalculate obligation based on user input and bureau data
     * 
     * @param request The obligation recalculation request with application ID, refinement ID, and other data
     * @return Response containing the recalculated obligation result
     */
    @POST(ApiConstants.ENDPOINT_RECALCULATE_OBLIGATION)
    suspend fun recalculateObligation(
        @Body request: RecalculateObligationRequest
    ): Response<RecalculateObligationResponse>
}