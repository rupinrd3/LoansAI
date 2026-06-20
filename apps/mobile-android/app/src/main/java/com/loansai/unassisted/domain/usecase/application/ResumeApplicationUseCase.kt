package com.loansai.unassisted.domain.usecase.application

import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.repository.LoanRepository
import javax.inject.Inject

/**
 * Use case for resuming an existing loan application
 */
class ResumeApplicationUseCase @Inject constructor(
    private val loanRepository: LoanRepository
) {
    /**
     * Gets the current loan application if one exists
     *
     * @return The current loan application or null if none exists
     */
    suspend operator fun invoke(): LoanApplication? {
        return loanRepository.getCurrentApplication()
    }
}