package com.loansai.unassisted.service.bre

import com.loansai.unassisted.data.remote.api.BREApi
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.time.withTimeout
import javax.inject.Inject

/**
 * Service interface for direct BRE interactions
 */
interface DirectBREProcessingService {
    /**
     * Calculate loan offer using the Python BRE worker
     */
    suspend fun calculateOffer(applicationId: String, inputData: Map<String, Any?>): Flow<Resource<Map<String, Any?>>>
}

/**
 * Implementation of DirectBREProcessingService using Retrofit API
 */
class DirectBREProcessingServiceImpl @Inject constructor(
    private val breApi: BREApi
) : DirectBREProcessingService {
    
    override suspend fun calculateOffer(
        applicationId: String, 
        data: Map<String, Any?>
    ): Flow<Resource<Map<String, Any?>>> = flow {
        emit(Resource.Loading())
        
        try {
            AppLogger.d("============== STARTING BRE API CALL ==============")
            AppLogger.d("BRE API URL: ${breApi.toString()}")
            AppLogger.d("Request for application: $applicationId")
            
            // Print request data in a safely formatted way
            val sanitizedData = mutableMapOf<String, Any?>()
            data.forEach { (key, value) -> 
                val safeValue = when {
                    key.contains("password", ignoreCase = true) -> "******"
                    value is Map<*, *> -> "Map with ${(value as Map<*, *>).size} entries"
                    value is Collection<*> -> "Collection with ${(value as Collection<*>).size} items"
                    else -> value?.toString()?.take(100) // Limit string length
                }
                sanitizedData[key] = safeValue
            }
            AppLogger.d("BRE request data: $sanitizedData")
            
            // Make the actual API call without timeout
            val response = breApi.calculateOffer(data as Map<String, @JvmSuppressWildcards Any>)
            
            // Log response details
            AppLogger.d("BRE API response received: code=${response.code()}, isSuccessful=${response.isSuccessful}")
            
            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                
                // Log a summary of the response body
                val decisionStatus = responseBody["decisionStatus"]
                val approvedAmount = responseBody["approvedLoanAmount"]
                val reasons = responseBody["rejectionReasons"] ?: responseBody["referralReasons"]
                
                AppLogger.d("BRE API success: decision=$decisionStatus, amount=$approvedAmount, reasons=$reasons")
                
                emit(Resource.Success(responseBody))
            } else {
                // Handle error response
                val errorBody = response.errorBody()?.string() ?: "No error body available"
                val errorMessage = "BRE API call failed: HTTP ${response.code()} - ${response.message()}"
                
                AppLogger.e("$errorMessage\nError body: $errorBody")
                emit(Resource.Error("$errorMessage\nDetails: $errorBody"))
            }
        } catch (e: Exception) {
            val errorMessage = "BRE API call exception: ${e.javaClass.simpleName} - ${e.message}"
            AppLogger.e(errorMessage, e)
            e.printStackTrace() // Print full stack trace for network errors
            emit(Resource.Error(errorMessage))
        } finally {
            AppLogger.d("============== COMPLETED BRE API CALL ==============")
        }
    }
}
