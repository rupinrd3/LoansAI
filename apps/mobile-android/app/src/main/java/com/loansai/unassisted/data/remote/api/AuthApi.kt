package com.loansai.unassisted.data.remote.api

import com.loansai.unassisted.data.model.OTPRequest
import com.loansai.unassisted.data.model.OTPVerificationRequest
import com.loansai.unassisted.data.model.PrivacyPolicyAcceptanceRequest
import com.loansai.unassisted.data.model.UserDto
import com.loansai.unassisted.util.constants.ApiConstants
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit API interface for authentication-related endpoints
 */
interface AuthApi {
    
    /**
     * Request OTP for phone number
     */
    @POST(ApiConstants.ENDPOINT_GENERATE_OTP)
    suspend fun requestOTP(
        @Body request: OTPRequest
    ): Response<Map<String, Any>>
    
    /**
     * Verify OTP and authenticate user
     */
    @POST(ApiConstants.ENDPOINT_VERIFY_OTP)
    suspend fun verifyOTP(
        @Body request: OTPVerificationRequest
    ): Response<UserDto>
    
    /**
     * Update privacy policy acceptance status
     */
    @POST("user/privacy-policy")
    suspend fun updatePrivacyPolicyAcceptance(
        @Body request: PrivacyPolicyAcceptanceRequest
    ): Response<Map<String, Boolean>>
}