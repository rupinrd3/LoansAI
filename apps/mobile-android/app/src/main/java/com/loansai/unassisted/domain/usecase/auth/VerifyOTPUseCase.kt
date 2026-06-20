package com.loansai.unassisted.domain.usecase.auth

import com.loansai.unassisted.domain.model.User
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for verifying the OTP sent to the user's phone
 */
class VerifyOTPUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    /**
     * Verifies the OTP entered by the user
     *
     * @param phoneNumber The user's phone number
     * @param otp The OTP entered by the user
     * @return The authenticated user if OTP is valid
     */
    suspend operator fun invoke(phoneNumber: String, otp: String): User {
        val resource = userRepository.verifyOTP(phoneNumber, otp).first()
        return when (resource) {
            is Resource.Success -> resource.data
            else -> throw Exception(resource.errorOrNull() ?: "Invalid OTP")
        }
    }
}