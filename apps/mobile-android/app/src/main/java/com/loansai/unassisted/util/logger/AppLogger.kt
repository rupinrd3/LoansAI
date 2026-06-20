package com.loansai.unassisted.util.logger

import com.loansai.unassisted.BuildConfig
import timber.log.Timber

/**
 * A wrapper around Timber for logging
 * Provides additional functionality like centralized control over logging
 */
object AppLogger {
    
    private const val TAG = "LoanSAI"
    
    /**
     * Log a debug message
     */
    fun d(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) {
            Timber.tag(tag).d(message)
        }
    }
    
    /**
     * Log an info message
     */
    fun i(message: String, tag: String = TAG) {
        Timber.tag(tag).i(message)
    }
    
    /**
     * Log a warning message
     */
    fun w(message: String, tag: String = TAG) {
        Timber.tag(tag).w(message)
    }
    
    /**
     * Log an error message
     */
    fun e(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) {
            Timber.tag(tag).e(throwable, message)
        } else {
            Timber.tag(tag).e(message)
        }
    }
    
    /**
     * Log an API request
     */
    fun logApiRequest(endpoint: String, params: Map<String, Any?> = emptyMap()) {
        if (BuildConfig.DEBUG) {
            Timber.tag("API-Request").d("Endpoint: $endpoint, Params: $params")
        }
    }
    
    /**
     * Log an API response
     */
    fun logApiResponse(endpoint: String, responseCode: Int, responseBody: String? = null) {
        if (BuildConfig.DEBUG) {
            val body = if (responseBody != null && responseBody.length > 500) {
                "${responseBody.take(500)}... (truncated)"
            } else {
                responseBody ?: "null"
            }
            
            Timber.tag("API-Response").d("Endpoint: $endpoint, Code: $responseCode, Body: $body")
        }
    }
    
    /**
     * Log an error from an API request
     */
    fun logApiError(endpoint: String, throwable: Throwable, responseCode: Int? = null) {
        Timber.tag("API-Error").e(
            throwable, 
            "Endpoint: $endpoint, Code: $responseCode, Error: ${throwable.message}"
        )
    }
    
    /**
     * Log a user action
     */
    fun logUserAction(action: String, details: String? = null) {
        Timber.tag("User-Action").i("Action: $action, Details: $details")
    }
}