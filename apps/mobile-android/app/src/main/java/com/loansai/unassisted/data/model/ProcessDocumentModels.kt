package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request model for document processing via Backend LLM API
 */
data class ProcessDocumentRequest(
    @SerializedName("applicationId")
    val applicationId: String,
    
    @SerializedName("documentId") 
    val documentId: String,
    
    @SerializedName("documentType")
    val documentType: String,
    
    @SerializedName("userId")
    val userId: String? = null,
    
    @SerializedName("content")
    val content: String? = null,
    
    @SerializedName("sourceType")
    val sourceType: String? = null,
    
    @SerializedName("fileName")
    val fileName: String? = null,
    
    @SerializedName("fileType")
    val fileType: String? = null,
    
    @SerializedName("isBase64")
    val isBase64: Boolean = false,
    
    @SerializedName("modelPreference")
    val modelPreference: String? = null
)

/**
 * Response model from document processing Backend LLM API
 */
data class ProcessDocumentResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("documentId")
    val documentId: String,
    
    @SerializedName("applicationId")
    val applicationId: String,
    
    @SerializedName("status")
    val status: String, // SUCCESS, FAILURE, PROCESSING
    
    @SerializedName("documentType")
    val documentType: String,
    
    @SerializedName("modelUsed") 
    val modelUsed: String? = null,
    
    @SerializedName("processedAt")
    val processedAt: String? = null,
    
    @SerializedName("extractedData")
    val extractedData: Map<String, Any>? = null,
    
    @SerializedName("processingTimeMs")
    val processingTimeMs: Long? = null,
    
    @SerializedName("errorMessage")
    val errorMessage: String? = null
)

/**
 * Request model for obligation recalculation via Backend LLM API
 */
data class RecalculateObligationRequest(
    @SerializedName("applicationId")
    val applicationId: String,
    
    @SerializedName("obligationRefinementId")
    val obligationRefinementId: String,
    
    @SerializedName("userId")
    val userId: String? = null,
    
    @SerializedName("fetchDataFromFirestore")
    val fetchDataFromFirestore: Boolean = true,
    
    @SerializedName("tradelines")
    val tradelines: Map<String, Any>? = null,
    
    @SerializedName("userProvidedEmis")
    val userProvidedEmis: Map<String, Int>? = null,
    
    @SerializedName("userComments")
    val userComments: String? = null,
    
    @SerializedName("preferredModel")
    val preferredModel: String? = null
)

/**
 * Response model from obligation recalculation Backend LLM API
 */
data class RecalculateObligationResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("applicationId")
    val applicationId: String,
    
    @SerializedName("obligationRefinementId")
    val obligationRefinementId: String,
    
    @SerializedName("status")
    val status: String, // SUCCESS, FAILURE, PROCESSING
    
    @SerializedName("modelUsed")
    val modelUsed: String? = null,
    
    @SerializedName("processedAt")
    val processedAt: String? = null,
    
    @SerializedName("recalculatedObligation")
    val recalculatedObligation: Int? = null,
    
    @SerializedName("excludedLoans")
    val excludedLoans: List<ExcludedLoanDto>? = null,
    
    @SerializedName("processingTimeMs")
    val processingTimeMs: Long? = null,
    
    @SerializedName("errorMessage")
    val errorMessage: String? = null
)

/**
 * DTO for excluded loans in the response
 */
data class ExcludedLoanDto(
    @SerializedName("tradelineId")
    val tradelineId: String,
    
    @SerializedName("reason")
    val reason: String
)