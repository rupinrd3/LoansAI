package com.loansai.unassisted.domain.usecase.auth

import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for sending an OTP to the user's phone
 */
class SendOTPUseCase @Inject constructor(
    private val userRepository: UserRepository
) {
    /**
     * Sends an OTP to the user's phone number
     *
     * @param phoneNumber The user's phone number
     * @return True if the OTP was sent successfully, false otherwise
     */
    suspend operator fun invoke(phoneNumber: String): Boolean {
        val resource = userRepository.sendOTP(phoneNumber).first()
        return when (resource) {
            is Resource.Success -> resource.data
            else -> false
        }
    }
}