package com.loansai.unassisted.domain.usecase.application

import com.loansai.unassisted.domain.repository.LoanRepository
import javax.inject.Inject

/**
 * Use case for submitting a completed loan application
 */
class SubmitApplicationUseCase @Inject constructor(
    private val loanRepository: LoanRepository
) {
    /**
     * Submits the application for final processing
     *
     * @param applicationId The ID of the application to submit
     * @return True if the submission was successful, false otherwise
     */
    suspend operator fun invoke(applicationId: String): Boolean {
        return loanRepository.submitApplication(applicationId)
    }
}