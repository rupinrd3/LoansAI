package com.loansai.unassisted.domain.model

/**
 * UI model for displaying loan offers in the presentation layer
 * This replaces the original LoanOffer class to avoid conflict with the one in LoanApplication.kt
 */
data class LoanOfferUI(
    val id: String,
    val status: Status,
    val minAmount: Float,
    val maxAmount: Float,
    val defaultAmount: Float,
    val minTenure: Int,
    val maxTenure: Int,
    val defaultTenure: Int,
    val interestRate: Float,
    val processingFeePercentage: Float,
    val message: String = "",
    val reasons: List<String> = emptyList()
) {
    enum class Status {
        APPROVED,
        REJECTED,
        REFERRAL,
        LOADING,
        ERROR
    }
}