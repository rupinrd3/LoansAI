package com.loansai.unassisted.domain.model

import java.time.LocalDateTime

/**
 * Domain model for Obligation Refinement data
 * This represents user input from the Bureau Confirmation screen
 * and results from the backend LLM obligation recalculation
 */
data class ObligationRefinement(
    val recordId: String,
    val applicationId: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val userProvidedEmis: Map<String, Int> = emptyMap(),
    val userComments: String? = null,
    val llmRecalculatedObligation: Int? = null,
    val llmExcludedLoans: List<ExcludedLoan> = emptyList(),
    val llmProcessingStatus: LlmProcessingStatus = LlmProcessingStatus.PENDING,
    val llmProcessedAt: LocalDateTime? = null
)

/**
 * Domain model for a loan excluded from recalculation by LLM
 */
data class ExcludedLoan(
    val tradelineId: String,
    val reason: String
)

/**
 * Enum for LLM processing status
 */
enum class LlmProcessingStatus {
    PENDING,
    SUCCESS,
    FAILED
}
