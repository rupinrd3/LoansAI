package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.BankStatementDataDto
import com.loansai.unassisted.data.model.DocumentDto
import com.loansai.unassisted.data.model.DocumentProcessingRequest
import com.loansai.unassisted.data.model.UploadDocumentRequest
import com.loansai.unassisted.util.constants.ApiConstants
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API interface for document-related endpoints
 */
interface DocumentApi {
    
    /**
     * Upload document metadata (to get pre-signed URL)
     */
    @POST(ApiConstants.ENDPOINT_UPLOAD_DOCUMENT)
    suspend fun uploadDocumentMetadata(
        @Body request: UploadDocumentRequest
    ): Response<DocumentDto>
    
    /**
     * Upload document file directly (alternative to pre-signed URL)
     */
    @Multipart
    @POST(ApiConstants.ENDPOINT_UPLOAD_DOCUMENT)
    suspend fun uploadDocumentFile(
        @Query("applicationId") applicationId: String,
        @Query("documentType") documentType: String,
        @Query("fileType") fileType: String,
        @Part file: MultipartBody.Part
    ): Response<DocumentDto>
    
    /**
     * Process document for data extraction
     */
    @POST(ApiConstants.ENDPOINT_PROCESS_DOCUMENT)
    suspend fun processDocument(
        @Body request: DocumentProcessingRequest
    ): Response<DocumentDto>
    
    /**
     * Get all documents for an application
     */
    @GET("application/{applicationId}/documents")
    suspend fun getApplicationDocuments(
        @Path("applicationId") applicationId: String
    ): Response<List<DocumentDto>>
    
    /**
     * Get documents of a specific type for an application
     */
    @GET("application/{applicationId}/documents")
    suspend fun getDocumentsByType(
        @Path("applicationId") applicationId: String,
        @Query("documentType") documentType: String
    ): Response<List<DocumentDto>>
    
    /**
     * Delete a document
     */
    @DELETE("document/{documentId}")
    suspend fun deleteDocument(
        @Path("documentId") documentId: String
    ): Response<Map<String, Boolean>>
    
    /**
     * Extract bank statement data from a document
     */
    @GET("document/{documentId}/bank-statement-data")
    suspend fun extractBankStatementData(
        @Path("documentId") documentId: String
    ): Response<BankStatementDataDto>
}