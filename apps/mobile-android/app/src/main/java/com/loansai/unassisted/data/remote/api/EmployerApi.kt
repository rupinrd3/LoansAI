package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.EmployerDto
import com.loansai.unassisted.util.constants.ApiConstants
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit API interface for employer-related endpoints
 */
interface EmployerApi {
    
    /**
     * Search employers by name
     */
    @GET(ApiConstants.ENDPOINT_SEARCH_EMPLOYERS)
    suspend fun searchEmployers(
        @Query("query") query: String,
        @Query("limit") limit: Int = 10
    ): Response<List<EmployerDto>>
    
    /**
     * Get employer details by ID
     */
    @GET(ApiConstants.ENDPOINT_GET_EMPLOYER_DETAILS)
    suspend fun getEmployerDetails(
        @Path("id") employerId: String
    ): Response<EmployerDto>
    
    /**
     * Verify if email domain matches employer
     */
    @GET("employer/{id}/verify-domain")
    suspend fun verifyWorkEmailDomain(
        @Path("id") employerId: String,
        @Query("domain") emailDomain: String
    ): Response<Map<String, Boolean>>
    
    /**
     * Get valid email domains for an employer
     */
    @GET("employer/{id}/email-domains")
    suspend fun getEmployerEmailDomains(
        @Path("id") employerId: String
    ): Response<List<String>>
}