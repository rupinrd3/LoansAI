package com.loansai.unassisted.service.camunda.impl

import com.loansai.unassisted.data.model.camunda.CamundaMessageRequest
import com.loansai.unassisted.data.model.camunda.CamundaVariable
import com.loansai.unassisted.data.remote.api.CamundaApi
import com.loansai.unassisted.service.camunda.CamundaService
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CamundaService
 * Updated for v1.5 to support obligation recalculation
 */
@Singleton
class CamundaServiceImpl @Inject constructor(
    private val camundaApi: CamundaApi
) : CamundaService {
    
    /**
     * Start a loan application workflow
     */
    override suspend fun startLoanApplicationProcess(
        applicationId: String,
        panNumber: String
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())
        
        try {
            // Create the process variables
            val variables = mapOf(
                "applicationId" to CamundaVariable.createString(applicationId),
                "panNumber" to CamundaVariable.createString(panNumber)
            )
            
            // Create the message request
            val messageRequest = CamundaMessageRequest(
                messageName = "startLoanApplication",
                businessKey = applicationId,
                processVariables = variables
            )
            
            // Send the message
            val response = camundaApi.sendMessage(messageRequest)
            
            if (response.isSuccessful && response.body() != null) {
                val processInstanceId = response.body()!!.id
                AppLogger.d("Started Camunda process instance: $processInstanceId")
                emit(Resource.Success(processInstanceId))
            } else {
                val errorMessage = "Failed to start process: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error starting loan application process: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Send message to trigger the BRE calculation
     * Updated for v1.5 to support obligation recalculation results
     */
    override suspend fun triggerBRECalculation(
        applicationId: String,
        recalculatedObligation: Int?
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Create the process variables
            val variables = mutableMapOf<String, Any>(
                "applicationId" to applicationId
            )
            
            // Include recalculated obligation if available 
            if (recalculatedObligation != null) {
                AppLogger.d("Adding recalculated obligation to BRE calculation: $recalculatedObligation")
                variables["llmRecalculatedObligation"] = recalculatedObligation.toString()
            } else {
                AppLogger.d("No recalculated obligation provided for BRE calculation")
            }
            
            // Send message to trigger BRE calculation
            val result = sendProcessMessage(
                messageName = "Msg_CalculateOffer",
                businessKey = applicationId,
                variables = variables
            )
            
            result.collect {
                emit(it)
            }
        } catch (e: Exception) {
            val errorMessage = "Error triggering BRE calculation: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Send a message to update an existing process
     */
    override suspend fun sendProcessMessage(
        messageName: String,
        businessKey: String,
        variables: Map<String, Any>
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Log the message details 
            AppLogger.d("Sending message to Camunda: $messageName for businessKey: $businessKey")
            if (variables.containsKey("llmRecalculatedObligation")) {
                AppLogger.d("Message includes recalculated obligation: ${variables["llmRecalculatedObligation"]}")
            }
            
            // Convert variables to Camunda format
            val processVariables = variables.mapValues { (_, value) ->
                when (value) {
                    is String -> CamundaVariable.createString(value)
                    is Int -> CamundaVariable.createInteger(value)
                    is Long -> CamundaVariable.createLong(value)
                    is Double -> CamundaVariable.createDouble(value)
                    is Boolean -> CamundaVariable.createBoolean(value)
                    else -> CamundaVariable.createString(value.toString())
                }
            }
            
            // Create correlation keys
            val correlationKeys = mapOf(
                "businessKey" to CamundaVariable.createString(businessKey)
            )
            
            // Create the message request
            val messageRequest = CamundaMessageRequest(
                messageName = messageName,
                correlationKeys = correlationKeys,
                processVariables = processVariables
            )
            
            // Send the message
            val response = camundaApi.correlateMessage(messageRequest)
            
            if (response.isSuccessful) {
                AppLogger.d("Message sent successfully: $messageName to process $businessKey")
                emit(Resource.Success(true))
            } else {
                val errorMessage = "Failed to send message: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error sending process message: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
}