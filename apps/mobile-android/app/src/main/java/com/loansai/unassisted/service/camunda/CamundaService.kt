package com.loansai.unassisted.service.camunda

import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for Camunda workflow engine interactions
 * Updated for v1.5 to support obligation recalculation
 */
interface CamundaService {
    
    /**
     * Start a loan application workflow
     *
     * @param applicationId The ID of the loan application
     * @param panNumber The PAN number of the applicant
     * @return Flow of Resource<String> with the created process instance ID
     */
    suspend fun startLoanApplicationProcess(
        applicationId: String,
        panNumber: String
    ): Flow<Resource<String>>
    
    /**
     * Trigger the BRE calculation process
     * 
     * @param applicationId The ID of the loan application
     * @param recalculatedObligation Optional recalculated obligation amount from LLM
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun triggerBRECalculation(
        applicationId: String,
        recalculatedObligation: Int? = null
    ): Flow<Resource<Boolean>>
    
    /**
     * Send a message to update an existing process
     *
     * @param messageName The name of the message
     * @param businessKey The business key for correlation
     * @param variables Additional process variables to include
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun sendProcessMessage(
        messageName: String,
        businessKey: String,
        variables: Map<String, Any>
    ): Flow<Resource<Boolean>>
}