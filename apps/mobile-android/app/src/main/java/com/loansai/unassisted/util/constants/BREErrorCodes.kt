package com.loansai.unassisted.util.constants

/**
 * Standardized error codes for BRE process to enable better tracking and debugging
 */
object BREErrorCodes {
    // Flow-related errors
    const val FLOW_EXCEPTION = "BRE_001"
    const val FLOW_COLLECTION_TIMEOUT = "BRE_002"
    const val FLOW_PREMATURE_TERMINATION = "BRE_003"
    
    // Data access errors
    const val APPLICATION_NOT_FOUND = "BRE_101"
    const val OBLIGATION_REFINEMENT_NOT_FOUND = "BRE_102"
    const val OBLIGATION_REFINEMENT_PARSE_ERROR = "BRE_103"
    const val DATA_CONSISTENCY_ERROR = "BRE_104"
    
    // API errors
    const val BRE_API_CONNECTION_FAILED = "BRE_201"
    const val BRE_API_TIMEOUT = "BRE_202"
    const val BRE_API_INVALID_RESPONSE = "BRE_203"
    const val BRE_API_AUTHENTICATION_FAILED = "BRE_204"
    
    // Processing errors
    const val INVALID_INPUT_DATA = "BRE_301"
    const val DOCUMENT_EXTRACTION_FAILED = "BRE_302"
    const val CALCULATION_ERROR = "BRE_303"
    
    // State management errors
    const val STATE_SYNCHRONIZATION_ERROR = "BRE_401"
    const val RACE_CONDITION_DETECTED = "BRE_402"
    const val INCONSISTENT_DATA_STATE = "BRE_403"
    
    // Retry mechanism errors
    const val MAX_RETRIES_EXCEEDED = "BRE_501"
    const val RETRY_MECHANISM_FAILURE = "BRE_502"
    
    /**
     * Get human-readable description for error code
     */
    fun getErrorDescription(errorCode: String): String {
        return when (errorCode) {
            FLOW_EXCEPTION -> "Flow encountered an unexpected exception"
            FLOW_COLLECTION_TIMEOUT -> "Flow collection operation timed out"
            FLOW_PREMATURE_TERMINATION -> "Flow terminated prematurely"
            APPLICATION_NOT_FOUND -> "Application data not found in database"
            OBLIGATION_REFINEMENT_NOT_FOUND -> "Obligation refinement data not found"
            OBLIGATION_REFINEMENT_PARSE_ERROR -> "Failed to parse obligation refinement data"
            DATA_CONSISTENCY_ERROR -> "Data consistency check failed"
            BRE_API_CONNECTION_FAILED -> "Failed to connect to BRE API"
            BRE_API_TIMEOUT -> "BRE API call timed out"
            BRE_API_INVALID_RESPONSE -> "Invalid response from BRE API"
            BRE_API_AUTHENTICATION_FAILED -> "BRE API authentication failed"
            INVALID_INPUT_DATA -> "Invalid input data provided to BRE"
            DOCUMENT_EXTRACTION_FAILED -> "Failed to extract document data"
            CALCULATION_ERROR -> "Error in loan calculation logic"
            STATE_SYNCHRONIZATION_ERROR -> "Application state synchronization failed"
            RACE_CONDITION_DETECTED -> "Race condition detected in data access"
            INCONSISTENT_DATA_STATE -> "Inconsistent data state detected"
            MAX_RETRIES_EXCEEDED -> "Maximum retry attempts exceeded"
            RETRY_MECHANISM_FAILURE -> "Retry mechanism encountered a failure"
            else -> "Unknown error code: $errorCode"
        }
    }
}
