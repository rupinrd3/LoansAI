package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.User
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user authentication and management
 */
interface UserRepository {
    
    /**
     * Send OTP to the provided phone number
     *
     * @param phoneNumber The phone number to send OTP to
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun sendOTP(phoneNumber: String): Flow<Resource<Boolean>>
    
    /**
     * Verify OTP and authenticate user
     *
     * @param phoneNumber The phone number
     * @param otp The OTP received
     * @return Flow of Resource<User> with authenticated user details
     */
    suspend fun verifyOTP(phoneNumber: String, otp: String): Flow<Resource<User>>
    
    /**
     * Get the current authenticated user
     *
     * @return Flow of Resource<User> with current user details
     */
    fun getCurrentUser(): Flow<Resource<User>>
    
    /**
     * Update the privacy policy acceptance status
     *
     * @param accepted Whether the user has accepted the privacy policy
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun updatePrivacyPolicyAcceptance(accepted: Boolean): Flow<Resource<Boolean>>
    
    /**
     * Update the current application ID for the user
     *
     * @param applicationId The ID of the current application
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun updateCurrentApplicationId(applicationId: String?): Flow<Resource<Boolean>>
    
    /**
     * Sign out the current user
     *
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun signOut(): Flow<Resource<Boolean>>
    
    /**
     * Check if the user is authenticated
     *
     * @return Flow of Boolean indicating authentication status
     */
    fun isUserAuthenticated(): Flow<Boolean>
    
    /**
     * Save email OTP for verification
     *
     * @param applicationId The application ID
     * @param email The email to send OTP to
     * @param otp The OTP to save
     * @return True if saved successfully, false otherwise
     */
    suspend fun saveEmailOTP(applicationId: String, email: String, otp: String): Boolean
    
    /**
     * Verify email OTP
     *
     * @param applicationId The application ID
     * @param email The email that received the OTP
     * @param otp The OTP to verify
     * @return True if OTP is valid, false otherwise
     */
    suspend fun verifyEmailOTP(applicationId: String, email: String, otp: String): Boolean
}