package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.BureauReportDto
import com.loansai.unassisted.data.model.PANDetailsDto
import com.loansai.unassisted.data.model.PANVerificationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit API interface for PAN verification and bureau report endpoints
 */
interface PANApi {
    
    /**
     * Verify PAN number with NSDL
     */
    @POST("verify_pan")
    suspend fun verifyPAN(
        @Body request: PANVerificationRequest
    ): Response<PANDetailsDto>
    
    /**
     * Fetch bureau report for a PAN number
     */
    @GET("get_bureau_report")
    suspend fun fetchBureauReport(
        @Query("panNumber") panNumber: String
    ): Response<BureauReportDto>
    
    /**
     * Save bureau report to application
     */
    @POST(".")
    suspend fun saveBureauReport(
        @Query("applicationId") applicationId: String,
        @Body bureauReport: BureauReportDto
    ): Response<Map<String, Boolean>>
    
    /**
     * Get bureau report for an application
     */
    @GET(".")
    suspend fun getBureauReport(
        @Query("applicationId") applicationId: String
    ): Response<BureauReportDto>
}