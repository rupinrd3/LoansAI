package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName
import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.DecisionStatus
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.LoanOffer
import com.loansai.unassisted.domain.model.OfferStatus
import java.time.LocalDateTime

/**
 * Data Transfer Object for Loan Application
 */
data class LoanApplicationDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("created_at")
    val createdAt: String,
    
    @SerializedName("last_updated_at")
    val lastUpdatedAt: String,
    
    @SerializedName("application_status")
    val applicationStatus: String = "CREATED",
    
    @SerializedName("pan_details")
    val panDetails: PANDetailsDto? = null,
    
    @SerializedName("bureau_report")
    val bureauReport: BureauReportDto? = null,
    
    @SerializedName("personal_info")
    val personalInfo: PersonalInfoDto? = null,
    
    @SerializedName("employment_details")
    val employmentDetails: EmploymentDetailsDto? = null,
    
    @SerializedName("documents")
    val documents: List<DocumentDto> = emptyList(),
    
    @SerializedName("loan_offer")
    val loanOffer: LoanOfferDto? = null,
    
    @SerializedName("current_step")
    val currentStep: String = "PAN_VERIFICATION",
    
    @SerializedName("completed_steps")
    val completedSteps: List<String> = emptyList(),
    
    @SerializedName("submitted_at")
    val submittedAt: String? = null,
    
    @SerializedName("application_number")
    val applicationNumber: String? = null,
    
    @SerializedName("decision_status")
    val decisionStatus: String? = null,
    
    @SerializedName("decision_reason")
    val decisionReason: String? = null,
    
    @SerializedName("underwriter_comments")
    val underwriterComments: String? = null
)

/**
 * Data Transfer Object for Loan Offer
 */
data class LoanOfferDto(
    @SerializedName("application_id")
    val applicationId: String,
    
    @SerializedName("offer_id")
    val offerId: String,
    
    @SerializedName("generated_at")
    val generatedAt: String,
    
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    
    @SerializedName("approved_loan_amount")
    val approvedLoanAmount: Double,
    
    @SerializedName("min_loan_amount")
    val minLoanAmount: Double,
    
    @SerializedName("max_loan_amount")
    val maxLoanAmount: Double,
    
    @SerializedName("selected_loan_amount")
    val selectedLoanAmount: Double? = null,
    
    @SerializedName("min_tenure")
    val minTenure: Int,
    
    @SerializedName("max_tenure")
    val maxTenure: Int,
    
    @SerializedName("selected_tenure")
    val selectedTenure: Int? = null,
    
    @SerializedName("interest_rate")
    val interestRate: Double,
    
    @SerializedName("processing_fee_percentage")
    val processingFeePercentage: Double,
    
    @SerializedName("processing_fee_amount")
    val processingFeeAmount: Double,
    
    @SerializedName("emi_amount")
    val emiAmount: Double? = null,
    
    @SerializedName("total_interest_amount")
    val totalInterestAmount: Double? = null,
    
    @SerializedName("total_repayment_amount")
    val totalRepaymentAmount: Double? = null,
    
    @SerializedName("loan_start_date")
    val loanStartDate: String? = null,
    
    @SerializedName("loan_end_date")
    val loanEndDate: String? = null,
    
    @SerializedName("offer_status")
    val offerStatus: String = "GENERATED",
    
    @SerializedName("offer_accepted_at")
    val offerAcceptedAt: String? = null
)

/**
 * Loan calculation request model
 */
data class LoanCalculationRequest(
    @SerializedName("application_id")
    val applicationId: String,
    
    @SerializedName("employment_type")
    val employmentType: String,
    
    @SerializedName("monthly_salary")
    val monthlySalary: Double,
    
    @SerializedName("existing_emi")
    val existingEmi: Double? = null,
    
    @SerializedName("credit_score")
    val creditScore: Int? = null
)

/**
 * Offer acceptance request model
 */
data class OfferAcceptanceRequest(
    @SerializedName("application_id")
    val applicationId: String,
    
    @SerializedName("offer_id")
    val offerId: String,
    
    @SerializedName("selected_loan_amount")
    val selectedLoanAmount: Double,
    
    @SerializedName("selected_tenure")
    val selectedTenure: Int
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun LoanApplicationDto.toLoanApplication(): LoanApplication {
    return LoanApplication(
        id = id,
        userId = userId,
        createdAt = LocalDateTime.parse(createdAt),
        lastUpdatedAt = LocalDateTime.parse(lastUpdatedAt),
        applicationStatus = ApplicationStatus.valueOf(applicationStatus),
        panDetails = panDetails?.toPANDetails(),
        bureauReport = bureauReport?.toBureauReport(),
        personalInfo = personalInfo?.toPersonalInfo(),
        employmentDetails = employmentDetails?.toEmploymentDetails(),
        documents = documents.map { it.toDocument() },
        loanOffer = loanOffer?.toLoanOffer(),
        currentStep = ApplicationStep.valueOf(currentStep),
        completedSteps = completedSteps.map { ApplicationStep.valueOf(it) },
        submittedAt = submittedAt?.let { LocalDateTime.parse(it) },
        applicationNumber = applicationNumber,
        decisionStatus = decisionStatus?.let { DecisionStatus.valueOf(it) },
        decisionReason = decisionReason,
        underwriterComments = underwriterComments
    )
}

/**
 * Mapping extension function to convert DTO to domain model
 */
fun LoanOfferDto.toLoanOffer(): LoanOffer {
    return LoanOffer(
        applicationId = applicationId,
        offerId = offerId,
        generatedAt = LocalDateTime.parse(generatedAt),
        expiresAt = expiresAt?.let { LocalDateTime.parse(it) },
        approvedLoanAmount = approvedLoanAmount,
        minLoanAmount = minLoanAmount,
        maxLoanAmount = maxLoanAmount,
        selectedLoanAmount = selectedLoanAmount,
        minTenure = minTenure,
        maxTenure = maxTenure,
        selectedTenure = selectedTenure,
        interestRate = interestRate,
        processingFeePercentage = processingFeePercentage,
        processingFeeAmount = processingFeeAmount,
        emiAmount = emiAmount,
        totalInterestAmount = totalInterestAmount,
        totalRepaymentAmount = totalRepaymentAmount,
        loanStartDate = loanStartDate?.let { LocalDateTime.parse(it) },
        loanEndDate = loanEndDate?.let { LocalDateTime.parse(it) },
        offerStatus = OfferStatus.valueOf(offerStatus),
        offerAcceptedAt = offerAcceptedAt?.let { LocalDateTime.parse(it) }
    )
}