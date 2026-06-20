package com.loansai.unassisted.domain.usecase.pan

import com.loansai.unassisted.domain.model.PANDetails
import com.loansai.unassisted.domain.repository.PANRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for verifying a PAN number with NSDL
 */
class VerifyPANUseCase @Inject constructor(
    private val panRepository: PANRepository
) {
    /**
     * Verifies a PAN number with NSDL
     *
     * @param panNumber The PAN number to verify
     * @return PAN details if verification is successful
     */
    suspend operator fun invoke(panNumber: String): PANDetails {
        // Validate PAN format
        if (!isValidPANFormat(panNumber)) {
            throw IllegalArgumentException("Invalid PAN format")
        }
        
        val resource = panRepository.verifyPAN(panNumber).first()
        return when (resource) {
            is Resource.Success -> resource.data
            else -> throw Exception(resource.errorOrNull() ?: "PAN verification failed")
        }
    }
    
    /**
     * Validates the format of a PAN number
     * PAN format: AAAAA1234A (5 letters, 4 numbers, 1 letter)
     *
     * @param panNumber The PAN number to validate
     * @return True if the format is valid, false otherwise
     */
    private fun isValidPANFormat(panNumber: String): Boolean {
        val panRegex = "[A-Z]{5}[0-9]{4}[A-Z]{1}".toRegex()
        return panRegex.matches(panNumber)
    }
}