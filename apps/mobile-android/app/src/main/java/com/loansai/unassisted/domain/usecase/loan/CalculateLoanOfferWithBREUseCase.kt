package com.loansai.unassisted.domain.usecase.loan

import com.loansai.unassisted.domain.model.DecisionStatus
import com.loansai.unassisted.domain.model.LlmProcessingStatus
import com.loansai.unassisted.domain.model.LoanOfferUI
import com.loansai.unassisted.domain.model.ObligationRefinement
import com.loansai.unassisted.domain.repository.BRERepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.ObligationRefinementRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.firstOrNull  
import kotlinx.coroutines.delay
import java.time.Duration
import javax.inject.Inject

/**
 * Use case for calculating a loan offer using the Business Rules Engine
 * Updated for v1.5.0 to support LLM-recalculated obligation
 */
class CalculateLoanOfferWithBREUseCase @Inject constructor(
    private val breRepository: BRERepository,
    private val loanRepository: LoanRepository,
    private val obligationRefinementRepository: ObligationRefinementRepository
) {
    /**
    * Triggers the BRE process via direct API and gets the decision
    *
    * @return The calculated loan offer UI model or null if not available
    */

    suspend operator fun invoke(): LoanOfferUI? {
        try {
            // First get the current application
            val application = loanRepository.getCurrentApplication()
            if (application == null) {
                AppLogger.w("No current application found in CalculateLoanOfferWithBREUseCase.")
                return null
            }

            AppLogger.d("Starting direct BRE process for application: ${application.id}")

            // Check for recalculated obligation from the obligationRefinement subcollection
            var recalculatedObligation: Int? = null
            var emiToUse: Int = 0

            try {
                val obligationRefinementResource = obligationRefinementRepository
                    .getLatestObligationRefinement(application.id)
                    .filterNot { it is Resource.Loading }
                    .firstOrNull()

                when (obligationRefinementResource) {
                    is Resource.Success -> {
                        val refinement = obligationRefinementResource.data
                        AppLogger.d("Obligation refinement found: Status=${refinement.llmProcessingStatus}")
                        if (refinement.llmProcessingStatus == LlmProcessingStatus.SUCCESS &&
                            refinement.llmRecalculatedObligation != null) {
                            recalculatedObligation = refinement.llmRecalculatedObligation
                            emiToUse = recalculatedObligation!!
                            AppLogger.d("Found valid recalculated obligation: $recalculatedObligation")
                        }
                    }
                    is Resource.Error -> {
                        AppLogger.w("Error fetching obligation refinement: ${obligationRefinementResource.message}")
                        // Use declared EMI from employment details
                        emiToUse = application.employmentDetails?.monthlyEmi?.toInt() ?: 0
                        AppLogger.d("Using declared EMI from employment details: $emiToUse")
                    }
                    is Resource.Loading,
                    null -> {
                        AppLogger.d("No obligation refinement found, using declared EMI")
                        // Use declared EMI from employment details
                        emiToUse = application.employmentDetails?.monthlyEmi?.toInt() ?: 0
                        AppLogger.d("Using declared EMI from employment details: $emiToUse")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Exception checking for recalculated obligation: ${e.message}", e)
                // Use declared EMI from employment details as fallback
                emiToUse = application.employmentDetails?.monthlyEmi?.toInt() ?: 0
                AppLogger.d("Exception occurred, using declared EMI from employment details: $emiToUse")
            }

            // Create BRE request data manually for direct API call
            val requestData = mutableMapOf<String, Any?>()
            requestData["applicationId"] = application.id

            // Add personal info
            val personalInfoMap = mutableMapOf<String, Any?>()
            personalInfoMap["dateOfBirth"] = application.personalInfo?.dateOfBirth?.toString()
            personalInfoMap["name"] = application.personalInfo?.name
            requestData["personalInfo"] = personalInfoMap

            // Add employment details with EMI fallback logic
            val employmentDetailsMap = mutableMapOf<String, Any?>()
            employmentDetailsMap["employmentType"] = application.employmentDetails?.employmentType?.name
            employmentDetailsMap["monthlySalary"] = application.employmentDetails?.monthlySalary
            employmentDetailsMap["monthlyEmi"] = emiToUse
            requestData["employmentDetails"] = employmentDetailsMap

            // Add bureau data
            val bureauDataMap = mutableMapOf<String, Any?>()
            bureauDataMap["creditScore"] = application.bureauReport?.creditScore ?: 0
            requestData["bureauData"] = bureauDataMap

            // Add recalculated obligation if available
            if (recalculatedObligation != null) {
                requestData["llmRecalculatedObligation"] = recalculatedObligation
            }

            AppLogger.d("BRE request data: ${requestData.toString().take(500)}...")

            // Try starting the BRE process with proper error handling
            try {
                AppLogger.d("Calling breRepository.startBREProcess")
                
                val startResult = breRepository.startBREProcess(application.id)
                    .filterNot { it is Resource.Loading }
                    .firstOrNull()

                when (startResult) {
                    null -> {
                        val errorMsg = "No result received from starting BRE process"
                        AppLogger.e(errorMsg)
                        throw Exception(errorMsg)
                    }
                    is Resource.Error -> {
                        val errorMsg = "BRE API call failed during start: ${startResult.message}"
                        AppLogger.e(errorMsg)
                        throw Exception(errorMsg)
                    }
                    is Resource.Success -> {
                        AppLogger.d("BRE process started successfully (ID: ${startResult.data}), now getting the decision")
                        
                        // Add a short delay for Firestore updates
                        delay(1000)

                        AppLogger.d("Calling breRepository.getLatestDecision")
                        val decisionResult = breRepository.getLatestDecision(application.id)
                            .filterNot { it is Resource.Loading }
                            .firstOrNull()

                        AppLogger.d("Result from getLatestDecision: ${decisionResult?.javaClass?.simpleName}")

                        // Handle the decision
                        if (decisionResult is Resource.Success) {
                            val decisionStatus = decisionResult.data
                            AppLogger.d("BRE decision obtained: $decisionStatus")

                            when(decisionStatus) {
                                DecisionStatus.AUTO_APPROVED -> {
                                    AppLogger.d("Decision is AUTO_APPROVED. Fetching loan offer.")
                                    val offerResponse = breRepository.getLoanOffer(application.id)
                                        .filterNot { it is Resource.Loading }
                                        .firstOrNull()

                                    if (offerResponse is Resource.Success) {
                                        val offer = offerResponse.data
                                        AppLogger.d("Successfully retrieved BRE loan offer: amount=${offer.approvedLoanAmount}")

                                        return LoanOfferUI(
                                            id = offer.offerId,
                                            status = LoanOfferUI.Status.APPROVED,
                                            minAmount = offer.minLoanAmount.toFloat(),
                                            maxAmount = offer.maxLoanAmount.toFloat(),
                                            defaultAmount = offer.approvedLoanAmount.toFloat(),
                                            minTenure = offer.minTenure,
                                            maxTenure = offer.maxTenure,
                                            defaultTenure = offer.minTenure + ((offer.maxTenure - offer.minTenure) / 2),
                                            interestRate = offer.interestRate.toFloat(),
                                            processingFeePercentage = offer.processingFeePercentage.toFloat()
                                        )
                                    } else {
                                        val errorMsg = "Failed to get loan offer after approval: ${(offerResponse as? Resource.Error)?.message ?: "Offer response was null"}"
                                        AppLogger.e(errorMsg)
                                        throw Exception(errorMsg)
                                    }
                                }
                                DecisionStatus.REJECTED -> {
                                    AppLogger.d("BRE decision is REJECTED")
                                    return LoanOfferUI(
                                        id = "",
                                        status = LoanOfferUI.Status.REJECTED,
                                        minAmount = 0f, maxAmount = 0f, defaultAmount = 0f,
                                        minTenure = 0, maxTenure = 0, defaultTenure = 0,
                                        interestRate = 0f, processingFeePercentage = 0f,
                                        message = application.decisionReason ?: "Application rejected"
                                    )
                                }
                                DecisionStatus.REFERRED_TO_UNDERWRITER -> {
                                    AppLogger.d("BRE decision is REFERRED_TO_UNDERWRITER")
                                    val monthlySalary = application.employmentDetails?.monthlySalary?.toFloat() ?: 50000f
                                    val minLoanAmount = (monthlySalary * 12).coerceAtLeast(100000f)
                                    val maxLoanAmount = (monthlySalary * 36).coerceAtMost(2000000f)
                                    val defaultAmount = (minLoanAmount + maxLoanAmount) / 2

                                    return LoanOfferUI(
                                        id = "",
                                        status = LoanOfferUI.Status.REFERRAL,
                                        minAmount = minLoanAmount, maxAmount = maxLoanAmount, defaultAmount = defaultAmount,
                                        minTenure = 12, maxTenure = 60, defaultTenure = 36,
                                        interestRate = 10.5f, processingFeePercentage = 1.0f,
                                        message = application.decisionReason ?: "Your application requires manual review"
                                    )
                                }
                                DecisionStatus.PENDING -> {
                                    AppLogger.w("BRE decision is still PENDING")
                                    throw Exception("BRE decision is still PENDING")
                                }
                            }
                        } else {
                            val errorMsg = "Failed to get decision from BRE: ${(decisionResult as? Resource.Error)?.message ?: "Decision response was null"}"
                            AppLogger.e(errorMsg)
                            throw Exception(errorMsg)
                        }
                    }
                    is Resource.Loading -> {
                        val errorMsg = "Unexpected loading state after filtering startBREProcess"
                        AppLogger.e(errorMsg)
                        throw Exception(errorMsg)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error during BRE process execution: ${e.javaClass.simpleName}: ${e.message}", e)
                AppLogger.d("Falling back to existing loan offer method due to BRE failure")
            }

            // Fallback to existing method if BRE process failed
            AppLogger.d("Falling back to existing loan offer method (after BRE attempt)")
            return loanRepository.getLoanOffer()

        } catch (e: Exception) {
            val errorMessage = "Error calculating loan offer with BRE (Outer Catch): ${e.message}"
            AppLogger.e(errorMessage, e)
            
            return LoanOfferUI(
                id = "", status = LoanOfferUI.Status.ERROR,
                minAmount = 0f, maxAmount = 0f, defaultAmount = 0f,
                minTenure = 0, maxTenure = 0, defaultTenure = 0,
                interestRate = 0f, processingFeePercentage = 0f,
                message = "Error calculating loan offer: ${e.message}"
            )
        }
    }

}