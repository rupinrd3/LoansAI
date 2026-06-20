package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.EmploymentDetailsDto
import com.loansai.unassisted.data.model.LoanApplicationDto
import com.loansai.unassisted.data.model.LoanCalculationRequest
import com.loansai.unassisted.data.model.LoanOfferDto
import com.loansai.unassisted.data.model.OfferAcceptanceRequest
import com.loansai.unassisted.data.model.PersonalInfoDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API interface for loan application related endpoints
 */
interface LoanApi {
    
    /**
     * Get an application by ID
     */
    @GET("{id}")  
    suspend fun getApplication(
        @Path("id") applicationId: String
    ): Response<LoanApplicationDto>
    
    /**
     * Get all applications for a user
     */
    @GET(".")  // Use "." instead of empty string
    suspend fun getUserApplications(
        @Query("userId") userId: String
    ): Response<List<LoanApplicationDto>>
    
    /**
     * Update personal information
     */
    @POST(".")  // Use "." instead of empty string
    suspend fun updatePersonalInfo(
        @Query("id") applicationId: String,
        @Body personalInfo: PersonalInfoDto
    ): Response<LoanApplicationDto>
    
    /**
     * Update employment details
     */
    @POST(".")  // Use "." instead of empty string
    suspend fun updateEmploymentDetails(
        @Query("id") applicationId: String,
        @Body employmentDetails: EmploymentDetailsDto
    ): Response<LoanApplicationDto>
    
    /**
     * Submit application for review
     */
    @POST("{id}")  // Use path parameter instead of query parameter
    suspend fun submitApplication(
        @Path("id") applicationId: String
    ): Response<LoanApplicationDto>
    
    /**
     * Update application status
     */
    @POST(".")  // Use "." instead of empty string
    suspend fun updateApplicationStatus(
        @Query("id") applicationId: String,
        @Query("status") status: String
    ): Response<LoanApplicationDto>
    
    /**
     * Calculate loan offer
     */
    @POST(".")  // Use "." instead of empty string
    suspend fun calculateLoanOffer(
        @Body request: LoanCalculationRequest
    ): Response<LoanOfferDto>
    
    /**
     * Accept offer
     */
    @POST(".")  // Use "." instead of empty string
    suspend fun acceptOffer(
        @Body request: OfferAcceptanceRequest
    ): Response<LoanApplicationDto>
    
    /**
     * Get application decision
     */
    @GET(".")  // Use "." instead of empty string
    suspend fun getApplicationDecision(
        @Query("id") applicationId: String
    ): Response<Map<String, String>>
}