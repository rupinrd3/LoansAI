package com.loansai.unassisted.domain.model

import java.time.LocalDateTime

/**
 * Domain model for Loan Application
 */
data class LoanApplication(
    val id: String = "",
    val userId: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val lastUpdatedAt: LocalDateTime = LocalDateTime.now(),
    val applicationStatus: ApplicationStatus = ApplicationStatus.CREATED,
    
    // Data collected during application process
    val panNumber: String? = null,
    val panDetails: PANDetails? = null,
    val bureauReport: BureauReport? = null,
    val personalInfo: PersonalInfo? = null,
    val employmentDetails: EmploymentDetails? = null,
    val documents: List<Document> = emptyList(),
    val documentIds: List<String> = emptyList(),
    
    // Loan offer details
    val loanOffer: LoanOffer? = null,
    
    // Application progress tracking
    val currentStep: ApplicationStep = ApplicationStep.PAN_VERIFICATION,
    val completedSteps: List<ApplicationStep> = emptyList(),
    
    // Final submission details
    val submittedAt: LocalDateTime? = null,
    val applicationNumber: String? = null,
    
    // Processing and decision details
    val decisionStatus: DecisionStatus? = null,
    val decisionReason: String? = null,
    val underwriterComments: String? = null,

    val additionalData: Map<String, Any>? = null
)

/**
 * Loan offer model
 */
data class LoanOffer(
    val applicationId: String,
    val offerId: String,
    val generatedAt: LocalDateTime,
    val expiresAt: LocalDateTime? = null,
    val approvedLoanAmount: Double,
    val minLoanAmount: Double,
    val maxLoanAmount: Double,
    val selectedLoanAmount: Double? = null,
    val minTenure: Int, // in months
    val maxTenure: Int, // in months
    val selectedTenure: Int? = null, // in months
    val interestRate: Double,
    val processingFeePercentage: Double,
    val processingFeeAmount: Double,
    val emiAmount: Double? = null,
    val totalInterestAmount: Double? = null,
    val totalRepaymentAmount: Double? = null,
    val loanStartDate: LocalDateTime? = null,
    val loanEndDate: LocalDateTime? = null,
    val offerStatus: OfferStatus = OfferStatus.GENERATED,
    val offerAcceptedAt: LocalDateTime? = null
)

/**
 * Application status enum
 */
enum class ApplicationStatus {
    CREATED,
    IN_PROGRESS,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED,
    EXPIRED
}

/**
 * Decision status enum
 */
enum class DecisionStatus {
    AUTO_APPROVED,
    REJECTED,
    REFERRED_TO_UNDERWRITER,
    PENDING
}

/**
 * Offer status enum
 */
enum class OfferStatus {
    GENERATED,
    VIEWED,
    ACCEPTED,
    REJECTED,
    EXPIRED
}