package com.loansai.unassisted.util.validation

import com.google.firebase.firestore.FirebaseFirestore
import com.loansai.unassisted.domain.model.BREInput
import com.loansai.unassisted.domain.model.ObligationRefinement
import com.loansai.unassisted.util.constants.BREErrorCodes
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/**
 * Validates and ensures consistency of BRE-related data structures
 */
object BREDataStateChecker {
    
    private const val MAX_CONSISTENCY_WAIT_MILLIS = 3000L
    private const val CONSISTENCY_CHECK_INTERVAL_MILLIS = 300L
    
    /**
     * Validates that BREInput data is complete and consistent
     */
    fun validateBREInput(breInput: BREInput): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check required fields
        if (breInput.applicationId.isEmpty()) {
            errors.add("${BREErrorCodes.INVALID_INPUT_DATA}: applicationId is empty")
        }
        if (breInput.userId.isEmpty()) {
            errors.add("${BREErrorCodes.INVALID_INPUT_DATA}: userId is empty")
        }
        
        // Validate critical data components
        if (breInput.employmentDetails?.monthlySalary == null || breInput.employmentDetails.monthlySalary <= 0) {
            errors.add("${BREErrorCodes.INVALID_INPUT_DATA}: Valid monthly salary required")
        }
        
        // Log complete data state for debugging
        AppLogger.d("[BRE_DATA_CHECK] BREInput Structure: applicationId=${breInput.applicationId}, " +
                "hasPersonalInfo=${breInput.personalInfo != null}, " +
                "hasEmploymentDetails=${breInput.employmentDetails != null}, " +
                "hasBureauData=${breInput.bureauData != null}, " +
                "llmRecalculatedObligation=${breInput.llmRecalculatedObligation}")
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Validates obligation refinement data structure
     */
    fun validateObligationRefinement(refinement: ObligationRefinement): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Check if data is properly structured
        if (refinement.recordId.isEmpty()) {
            errors.add("${BREErrorCodes.OBLIGATION_REFINEMENT_PARSE_ERROR}: recordId is empty")
        }
        if (refinement.applicationId.isEmpty()) {
            errors.add("${BREErrorCodes.OBLIGATION_REFINEMENT_PARSE_ERROR}: applicationId is empty")
        }
        
        // Log data state
        AppLogger.d("[BRE_DATA_CHECK] ObligationRefinement Structure: recordId=${refinement.recordId}, " +
                "applicationId=${refinement.applicationId}, " +
                "llmProcessingStatus=${refinement.llmProcessingStatus}, " +
                "llmRecalculatedObligation=${refinement.llmRecalculatedObligation}, " +
                "userProvidedEmis=${refinement.userProvidedEmis}")
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * Checks for data consistency between write and read operations
     */
    suspend fun waitForDataConsistency(
        firestore: FirebaseFirestore,
        applicationId: String,
        refinementId: String
    ): ConsistencyResult {
        AppLogger.d("[BRE_DATA_CHECK] Starting consistency check for refinement: $refinementId")
        
        val startTime = System.currentTimeMillis()
        var lastException: Exception? = null
        
        while (System.currentTimeMillis() - startTime < MAX_CONSISTENCY_WAIT_MILLIS) {
            try {
                val doc = firestore.collection("applications")
                    .document(applicationId)
                    .collection("obligationRefinement")
                    .document(refinementId)
                    .get()
                    .await()
                
                if (doc.exists()) {
                    AppLogger.d("[BRE_DATA_CHECK] Data found after ${System.currentTimeMillis() - startTime}ms")
                    return ConsistencyResult.Consistent(doc.data)
                }
                
                AppLogger.d("[BRE_DATA_CHECK] Data not found yet, waiting...")
                delay(CONSISTENCY_CHECK_INTERVAL_MILLIS)
            } catch (e: Exception) {
                lastException = e
                AppLogger.w("[BRE_DATA_CHECK] Error during consistency check: ${e.message}")
                delay(CONSISTENCY_CHECK_INTERVAL_MILLIS)
            }
        }
        
        return ConsistencyResult.Inconsistent(
            BREErrorCodes.DATA_CONSISTENCY_ERROR,
            "Data not found after $MAX_CONSISTENCY_WAIT_MILLIS ms",
            lastException
        )
    }
    
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
    }
    
    sealed class ConsistencyResult {
        data class Consistent(val data: Map<String, Any>?) : ConsistencyResult()
        data class Inconsistent(
            val errorCode: String,
            val message: String,
            val exception: Exception?
        ) : ConsistencyResult()
    }
}
