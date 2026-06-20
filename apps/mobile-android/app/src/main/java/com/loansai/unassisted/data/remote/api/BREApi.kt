package com.loansai.unassisted.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit API interface for direct BRE service
 */
interface BREApi {
    
    /**
     * Calculate loan offer directly from BRE
     */
    @POST("calculate-offer")
    suspend fun calculateOffer(
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<Map<String, @JvmSuppressWildcards Any>>
    
    /**
     * Health check endpoint
     */
    @GET("health")
    suspend fun healthCheck(): Response<Map<String, @JvmSuppressWildcards Any>>
}