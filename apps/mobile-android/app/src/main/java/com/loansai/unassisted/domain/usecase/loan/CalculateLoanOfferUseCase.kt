package com.loansai.unassisted.domain.usecase.loan

import com.loansai.unassisted.domain.model.LoanOfferUI
import com.loansai.unassisted.domain.repository.LoanRepository
import javax.inject.Inject

/**
 * Use case for calculating a loan offer based on the applicant's information
 */
class CalculateLoanOfferUseCase @Inject constructor(
    private val loanRepository: LoanRepository
) {
    /**
     * Calculates a loan offer based on the current application
     *
     * @return The calculated loan offer UI model
     */
    suspend operator fun invoke(): LoanOfferUI? {
        return loanRepository.getLoanOffer()
    }
}