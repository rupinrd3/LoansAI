package com.loansai.unassisted.ui.screens.offer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.LlmProcessingStatus
import com.loansai.unassisted.domain.model.LoanOfferUI
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.model.ScreenVisit
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.usecase.loan.CalculateLoanOfferUseCase
import com.loansai.unassisted.domain.usecase.loan.CalculateLoanOfferWithBREUseCase
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.roundToInt
import com.loansai.unassisted.domain.repository.ObligationRefinementRepository
import kotlinx.coroutines.flow.first
import com.loansai.unassisted.util.extensions.Resource

data class LoanOfferState(
    val offerStatus: LoanOfferUI.Status = LoanOfferUI.Status.LOADING,
    val minLoanAmount: Float = 100000f,
    val maxLoanAmount: Float = 1000000f,
    val selectedLoanAmount: Float = 500000f,
    val minTenure: Float = 12f,
    val maxTenure: Float = 60f,
    val selectedTenure: Float = 36f,
    val interestRate: Float = 10.5f,
    val calculatedEMI: Int = 0,
    val processingFee: Int = 0,
    val errorMessage: String = ""
)

@HiltViewModel
class LoanOfferViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val aiRepository: AIRepository,
    private val calculateLoanOfferUseCase: CalculateLoanOfferUseCase,
    private val calculateLoanOfferWithBREUseCase: CalculateLoanOfferWithBREUseCase,
    private val metadataRepository: MetadataRepository,
    private val obligationRefinementRepository: ObligationRefinementRepository
    
) : ViewModel() {
    
    private val BRE_TIMEOUT_DURATION: Long = 15000L // 15 seconds timeout for BRE
    private val _state = MutableStateFlow(LoanOfferState())
    val state: StateFlow<LoanOfferState> = _state
    
    // Variables to track screen visit and section timings
    private var screenVisitStartTime: LocalDateTime? = null
    private var sectionTimingId: String? = null
    private var currentApplicationId: String? = null
    
    
    init {
        fetchLoanOffer()
        startScreenVisit() // Track screen visit
    }

    // Track when user enters the screen
    private fun startScreenVisit() {
        viewModelScope.launch {
            try {
                val application = loanRepository.getCurrentApplication()
                if (application != null) {
                    currentApplicationId = application.id
                    
                    // Record screen visit start
                    screenVisitStartTime = LocalDateTime.now()
                    metadataRepository.recordScreenVisit(
                        applicationId = application.id,
                        screenName = "LoanOfferScreen"
                    ).collect { /* collect to execute the flow */ }
                    
                    // Start section timing for main offer section
                    sectionTimingId = loanRepository.startSectionTiming(
                        applicationId = application.id,
                        screenName = "LoanOfferScreen",
                        sectionName = "LoanOfferCustomization"
                    )
                    
                    // Send event to orchestrator
                    val eventData = mapOf(
                        "screenName" to "LoanOfferScreen",
                        "timestamp" to screenVisitStartTime.toString(),
                        "offerStatus" to _state.value.offerStatus.name
                    )
                    
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = application.id,
                        eventType = MetadataEventType.SCREEN_VISIT,
                        metadata = eventData
                    ).collect { /* collect to execute the flow */ }
                }
            } catch (e: Exception) {
                AppLogger.e("Error tracking screen visit start: ${e.message}", e)
            }
        }
    }
    
    // Track when user leaves the screen
    fun endScreenVisit() {
        viewModelScope.launch {
            try {
                if (currentApplicationId != null && screenVisitStartTime != null) {
                    // End screen visit
                    metadataRepository.endScreenVisit(
                        applicationId = currentApplicationId!!,
                        screenName = "LoanOfferScreen"
                    ).collect { /* collect to execute the flow */ }
                    
                    // End section timing if started
                    if (sectionTimingId != null) {
                        loanRepository.completeSectionTiming(
                            applicationId = currentApplicationId!!,
                            sectionTimingId = sectionTimingId!!
                        ).collect { /* collect to execute the flow */ }
                    }
                    
                    // Calculate duration for our own tracking
                    val endTime = LocalDateTime.now()
                    val durationSeconds = java.time.Duration.between(screenVisitStartTime, endTime).seconds
                    
                    // Log screen exit
                    AppLogger.d("User spent $durationSeconds seconds on Loan Offer Screen")
                    
                    // Record detailed screen metrics
                    val eventData = mapOf(
                        "screenName" to "LoanOfferScreen",
                        "durationSeconds" to durationSeconds,
                        "endTime" to endTime.toString(),
                        "selectedAmount" to _state.value.selectedLoanAmount,
                        "selectedTenure" to _state.value.selectedTenure,
                        "offerStatus" to _state.value.offerStatus.name
                    )
                    
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = currentApplicationId!!,
                        eventType = MetadataEventType.SCREEN_VISIT,
                        metadata = eventData
                    ).collect { /* collect to execute the flow */ }
                }
            } catch (e: Exception) {
                AppLogger.e("Error tracking screen visit end: ${e.message}", e)
            }
        }
    }


    private fun fetchLoanOffer() {
        viewModelScope.launch {
            _state.update { it.copy(offerStatus = LoanOfferUI.Status.LOADING) }
            
            try {
                AppLogger.d("Initiating loan offer calculation from BRE")
                
                // NEW: Get obligation refinement data first
                var refinedObligation: Int? = null
                var applicationId: String? = null
                
                try {
                    // Get current application ID
                    val currentApp = loanRepository.getCurrentApplication()
                    if (currentApp != null) {
                        applicationId = currentApp.id
                        AppLogger.d("Looking for obligation refinement data for application: $applicationId")
                        
                        // Get the latest obligation refinement
                        val refinementResource = obligationRefinementRepository.getLatestObligationRefinement(applicationId).first()
                        
                        if (refinementResource is Resource.Success) {
                            val refinement = refinementResource.data
                            AppLogger.d("Found obligation refinement: ${refinement.recordId}, status: ${refinement.llmProcessingStatus}")
                            
                            // Check if it was successfully processed by Gemini
                            if (refinement.llmProcessingStatus == LlmProcessingStatus.SUCCESS) {
                                refinedObligation = refinement.llmRecalculatedObligation
                                AppLogger.d("Using Gemini recalculated obligation: $refinedObligation")
                                
                                // Log excluded loans if any
                                if (refinement.llmExcludedLoans.isNotEmpty()) {
                                    AppLogger.d("Gemini excluded ${refinement.llmExcludedLoans.size} loans from calculation")
                                    refinement.llmExcludedLoans.forEach { loan ->
                                        AppLogger.d("Excluded loan ${loan.tradelineId}: ${loan.reason}")
                                    }
                                }
                            } else {
                                AppLogger.w("Obligation refinement status is not SUCCESS: ${refinement.llmProcessingStatus}")
                            }
                        } else {
                            AppLogger.d("No obligation refinement found for application: $applicationId")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error retrieving obligation refinement: ${e.message}", e)
                    // Continue with BRE call but without refinement data
                }
                
                // Try to get offer from BRE first with timeout handling
                val breLoanOffer = try {
                    AppLogger.d("API call to direct BRE service initiated" + 
                            (refinedObligation?.let { " with obligation amount: $it" } ?: " with default obligation"))
                    
                    // Call the direct BRE use case that will use our BREApi
                    val offer = calculateLoanOfferWithBREUseCase()
                    
                    if (offer != null) {
                        AppLogger.d("API response received from BRE service: status=${offer.status}")
                        recordLlmInteractionEvent("BRE_CALCULATION", "COMPLETED")
                    } else {
                        AppLogger.w("BRE service returned null offer")
                        recordLlmInteractionEvent("BRE_CALCULATION", "FAILED", "Null response from BRE")
                    }
                    offer
                } catch (e: Exception) {
                    AppLogger.e("Error getting offer from BRE: ${e.message}", e)
                    recordLlmInteractionEvent("BRE_CALCULATION", "FAILED", "Error: ${e.message}")
                    null
                }
                
                if (breLoanOffer != null) {
                    // Use the BRE offer
                    AppLogger.d("Using BRE loan offer with status: ${breLoanOffer.status}")
                    
                    _state.update {
                        it.copy(
                            offerStatus = breLoanOffer.status,
                            minLoanAmount = breLoanOffer.minAmount,
                            maxLoanAmount = breLoanOffer.maxAmount,
                            selectedLoanAmount = breLoanOffer.defaultAmount,
                            minTenure = breLoanOffer.minTenure.toFloat(),
                            maxTenure = breLoanOffer.maxTenure.toFloat(),
                            selectedTenure = breLoanOffer.defaultTenure.toFloat(),
                            interestRate = breLoanOffer.interestRate,
                            errorMessage = breLoanOffer.message
                        )
                    }
                    
                    // If approved, calculate initial EMI and processing fee
                    if (breLoanOffer.status == LoanOfferUI.Status.APPROVED) {
                        calculateEMI()
                    }
                    
                    // Update offer status in Firestore
                    saveOfferStatusToFirestore(breLoanOffer.status.toString())
                    
                    // Record BRE offer data in metadata
                    recordOfferMetadata(breLoanOffer.status.toString(), breLoanOffer.minAmount, 
                                        breLoanOffer.maxAmount, breLoanOffer.interestRate)
                    
                    return@launch
                }
                
                // Fallback to the original method if BRE doesn't return an offer
                AppLogger.d("BRE service did not return an offer, falling back to default calculation")
                
                val fallbackLoanOffer = try {
                    AppLogger.d("API call to fallback loan offer calculation initiated")
                    val offer = calculateLoanOfferUseCase()
                    if (offer != null) {
                        AppLogger.d("API response received from fallback calculation")
                    } else {
                        AppLogger.w("Fallback calculation returned null offer")
                    }
                    offer
                } catch (e: Exception) {
                    AppLogger.e("Error getting fallback offer: ${e.message}", e)
                    null
                }
                
                if (fallbackLoanOffer != null) {
                    AppLogger.d("Using fallback loan offer with status: ${fallbackLoanOffer.status}")
                    
                    _state.update {
                        it.copy(
                            offerStatus = fallbackLoanOffer.status,
                            minLoanAmount = fallbackLoanOffer.minAmount,
                            maxLoanAmount = fallbackLoanOffer.maxAmount,
                            selectedLoanAmount = fallbackLoanOffer.defaultAmount,
                            minTenure = fallbackLoanOffer.minTenure.toFloat(),
                            maxTenure = fallbackLoanOffer.maxTenure.toFloat(),
                            selectedTenure = fallbackLoanOffer.defaultTenure.toFloat(),
                            interestRate = fallbackLoanOffer.interestRate
                        )
                    }
                    
                    // Calculate initial EMI and processing fee
                    calculateEMI()
                    
                    // Update offer status in Firestore
                    saveOfferStatusToFirestore(fallbackLoanOffer.status.toString())
                    
                    // Record fallback offer data in metadata
                    recordOfferMetadata(fallbackLoanOffer.status.toString(), fallbackLoanOffer.minAmount, 
                                        fallbackLoanOffer.maxAmount, fallbackLoanOffer.interestRate, "FALLBACK")
                } else {
                    // Both BRE and fallback failed, use income-based "Refer to Underwriter" treatment
                    AppLogger.w("Both BRE and fallback loan offer calculations failed, using income-based referral")
                    
                    try {
                        // Get current application to access employment details
                        val currentApp = loanRepository.getCurrentApplication()
                        
                        // Get monthly salary from employment details (default to 50000 if not available)
                        val monthlySalary = currentApp?.employmentDetails?.monthlySalary?.toFloat() ?: 50000f
                        
                        // Calculate reasonable loan amount range based on income - with safety checks
                        val calculatedMin = (monthlySalary * 12).coerceAtLeast(50000f)
                        val calculatedMax = (monthlySalary * 36).coerceAtMost(2000000f)
                        
                        // Ensure min is less than max (this is the key fix)
                        val minLoanAmount = calculatedMin.coerceAtMost(calculatedMax - 50000f)
                        val maxLoanAmount = calculatedMax.coerceAtLeast(calculatedMin + 50000f)
                        val defaultAmount = (minLoanAmount + maxLoanAmount) / 2
                        
                        // Update state with calculated values
                        _state.update {
                            it.copy(
                                offerStatus = LoanOfferUI.Status.REFERRAL,
                                minLoanAmount = minLoanAmount,
                                maxLoanAmount = maxLoanAmount,
                                selectedLoanAmount = defaultAmount,
                                minTenure = 12f,
                                maxTenure = 60f,
                                selectedTenure = 36f,
                                interestRate = 10.5f,
                                errorMessage = "Your application has been referred to an underwriter for manual review."
                            )
                        }
                        
                        // Calculate initial EMI based on these values
                        calculateEMI()
                        
                        // Update offer status in Firestore
                        saveOfferStatusToFirestore("REFERRAL")
                        
                        // Record income-based offer in metadata
                        recordOfferMetadata("REFERRAL", minLoanAmount, maxLoanAmount, 
                                            10.5f, "INCOME_BASED")
                    } catch (e: Exception) {
                        AppLogger.e("Error calculating income-based loan offer: ${e.message}", e)
                        
                        // Fallback to sensible defaults if we can't get income
                        _state.update {
                            it.copy(
                                offerStatus = LoanOfferUI.Status.REFERRAL,
                                minLoanAmount = 100000f,
                                maxLoanAmount = 1000000f,
                                selectedLoanAmount = 500000f,
                                minTenure = 12f,
                                maxTenure = 60f,
                                selectedTenure = 36f,
                                interestRate = 10.5f,
                                errorMessage = "Your application has been referred to an underwriter for manual review."
                            )
                        }
                        
                        // Calculate EMI based on defaults
                        calculateEMI()
                        
                        // Update offer status in Firestore
                        saveOfferStatusToFirestore("REFERRAL")
                        
                        // Record default fallback offer in metadata
                        recordOfferMetadata("REFERRAL", 100000f, 1000000f, 
                                            10.5f, "DEFAULT_FALLBACK")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Unhandled error in fetchLoanOffer: ${e.message}", e)
                
                // Use income-based "Refer to Underwriter" treatment in case of unexpected errors
                try {
                    // Get current application to access employment details
                    val currentApp = loanRepository.getCurrentApplication()
                    
                    // Get monthly salary from employment details (default to 50000 if not available)
                    val monthlySalary = currentApp?.employmentDetails?.monthlySalary?.toFloat() ?: 50000f
                    
                    // Calculate reasonable loan amount range based on income
                    val minLoanAmount = (monthlySalary * 12).coerceAtLeast(100000f)
                    val maxLoanAmount = (monthlySalary * 36).coerceAtMost(2000000f)
                    val defaultAmount = (minLoanAmount + maxLoanAmount) / 2
                    
                    // Update state with calculated values
                    _state.update {
                        it.copy(
                            offerStatus = LoanOfferUI.Status.REFERRAL,
                            minLoanAmount = minLoanAmount,
                            maxLoanAmount = maxLoanAmount,
                            selectedLoanAmount = defaultAmount,
                            minTenure = 12f,
                            maxTenure = 60f,
                            selectedTenure = 36f,
                            interestRate = 10.5f,
                            errorMessage = "We encountered an issue processing your loan offer. Your application will be reviewed manually."
                        )
                    }
                    
                    // Calculate initial EMI
                    calculateEMI()
                    
                    // Update offer status in Firestore
                    saveOfferStatusToFirestore("REFERRAL")
                    
                    // Record error fallback offer in metadata
                    recordOfferMetadata("REFERRAL", minLoanAmount, maxLoanAmount, 
                                    10.5f, "ERROR_FALLBACK", e.message ?: "Unknown error")
                } catch (e2: Exception) {
                    AppLogger.e("Error calculating income-based loan offer: ${e2.message}", e2)
                    
                    // Fallback to sensible defaults if we can't get income
                    _state.update {
                        it.copy(
                            offerStatus = LoanOfferUI.Status.REFERRAL,
                            minLoanAmount = 100000f,
                            maxLoanAmount = 2000000f,
                            selectedLoanAmount = 500000f,
                            minTenure = 12f,
                            maxTenure = 60f,
                            selectedTenure = 36f,
                            interestRate = 10.5f,
                            errorMessage = "We encountered an issue processing your loan offer. Your application will be reviewed manually."
                        )
                    }
                    
                    // Calculate EMI based on defaults
                    calculateEMI()
                    
                    // Update offer status in Firestore
                    saveOfferStatusToFirestore("REFERRAL")
                    
                    // Record last-resort fallback in metadata
                    recordOfferMetadata("REFERRAL", 100000f, 1000000f, 
                                    10.5f, "FINAL_FALLBACK", e.message ?: "Multiple errors")
                }
            }
        }
    }

    fun updateLoanAmount(amount: Float) {
        _state.update { it.copy(selectedLoanAmount = amount) }
        calculateEMI()
        
        // Track the loan amount adjustment in metadata
        viewModelScope.launch {
            try {
                if (currentApplicationId != null) {
                    // Log a metadata event for loan amount adjustment
                    val eventData = mapOf(
                        "eventType" to "LOAN_AMOUNT_ADJUSTED",
                        "screenName" to "LoanOfferScreen",
                        "timestamp" to LocalDateTime.now().toString(),
                        "oldAmount" to _state.value.selectedLoanAmount,
                        "newAmount" to amount
                    )
                    
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = currentApplicationId!!,
                        eventType = MetadataEventType.SCREEN_VISIT,
                        metadata = eventData
                    ).collect { /* collect to execute the flow */ }
                }
            } catch (e: Exception) {
                AppLogger.e("Error tracking loan amount adjustment: ${e.message}", e)
            }
        }
    }

    fun updateTenure(tenure: Float) {
        _state.update { it.copy(selectedTenure = tenure) }
        calculateEMI()
        
        // Track the tenure adjustment in metadata
        viewModelScope.launch {
            try {
                if (currentApplicationId != null) {
                    // Log a metadata event for tenure adjustment
                    val eventData = mapOf(
                        "eventType" to "TENURE_ADJUSTED",
                        "screenName" to "LoanOfferScreen",
                        "timestamp" to LocalDateTime.now().toString(),
                        "oldTenure" to _state.value.selectedTenure,
                        "newTenure" to tenure
                    )
                    
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = currentApplicationId!!,
                        eventType = MetadataEventType.SCREEN_VISIT,
                        metadata = eventData
                    ).collect { /* collect to execute the flow */ }
                }
            } catch (e: Exception) {
                AppLogger.e("Error tracking tenure adjustment: ${e.message}", e)
            }
        }
    }

    fun calculateEMI() {
        val principal = _state.value.selectedLoanAmount
        val ratePerMonth = _state.value.interestRate / (12 * 100)
        val tenure = _state.value.selectedTenure
        
        // EMI formula: P * r * (1 + r)^n / ((1 + r)^n - 1)
        val emi = if (ratePerMonth > 0) {
            val term = (1 + ratePerMonth).toDouble().pow(tenure.toDouble())
            principal * ratePerMonth * term / (term - 1)
        } else {
            principal / tenure
        }
        
        // Calculate processing fee (e.g., 1% of loan amount)
        val processingFee = (principal * 0.01).toInt()
        
        _state.update {
            it.copy(
                calculatedEMI = emi.toInt(),
                processingFee = processingFee
            )
        }
        
        // Save the selected offer details to repository
        viewModelScope.launch {
            try {
                AppLogger.d("Saving loan offer selection")
                loanRepository.saveLoanOfferSelection(
                    amount = _state.value.selectedLoanAmount.toLong(),
                    tenure = _state.value.selectedTenure.toInt(),
                    interestRate = _state.value.interestRate,
                    emi = _state.value.calculatedEMI.toLong(),
                    processingFee = _state.value.processingFee.toLong()
                )
                AppLogger.d("Successfully saved loan offer selection")
                
                // Record EMI recalculation in metadata
                if (currentApplicationId != null) {
                    val eventData = mapOf(
                        "eventType" to "EMI_RECALCULATED",
                        "screenName" to "LoanOfferScreen",
                        "timestamp" to LocalDateTime.now().toString(),
                        "principal" to principal,
                        "tenure" to tenure,
                        "interestRate" to _state.value.interestRate,
                        "emi" to emi.toInt(),
                        "processingFee" to processingFee
                    )
                    
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = currentApplicationId!!,
                        eventType = MetadataEventType.SECTION_COMPLETED,
                        metadata = eventData
                    ).collect { /* collect to execute the flow */ }
                }
            } catch (e: Exception) {
                AppLogger.e("Error saving loan offer selection: ${e.message}", e)
            }
        }
    }

    fun retryLoanOffer() {
        fetchLoanOffer()
    }

    fun showAIAssistant() {
        // Show AI assistant with loan offer context
        viewModelScope.launch {
            val context = mapOf(
                "screen" to "loan_offer",
                "loanAmount" to _state.value.selectedLoanAmount,
                "tenure" to _state.value.selectedTenure,
                "interestRate" to _state.value.interestRate,
                "emi" to _state.value.calculatedEMI,
                "status" to _state.value.offerStatus.name
            )
            
            aiRepository.showAssistant(context)
        }
    }
    
    /**
     * Manually bypass the loan offer decision and set to "Refer to Underwriter"
     */
    fun bypassApproval() {
        AppLogger.d("Manual bypass of loan offer decision - setting to REFERRAL")
        
        viewModelScope.launch {
            try {
                // Get current application to access employment details
                val currentApp = loanRepository.getCurrentApplication()
                
                // Get monthly salary from employment details (default to 50000 if not available)
                val monthlySalary = currentApp?.employmentDetails?.monthlySalary?.toFloat() ?: 50000f
                
                // Calculate reasonable loan amount range based on income
                val minLoanAmount = (monthlySalary * 12).coerceAtLeast(100000f)
                val maxLoanAmount = (monthlySalary * 36).coerceAtMost(2000000f)
                val defaultAmount = (minLoanAmount + maxLoanAmount) / 2
                
                _state.update { currentState ->
                    currentState.copy(
                        offerStatus = LoanOfferUI.Status.REFERRAL,
                        minLoanAmount = minLoanAmount,
                        maxLoanAmount = maxLoanAmount,
                        selectedLoanAmount = defaultAmount,
                        minTenure = 12f,
                        maxTenure = 60f,
                        selectedTenure = 36f,
                        interestRate = 10.5f,
                        errorMessage = "Your application has been referred to an underwriter for manual review."
                    )
                }
                
                // Calculate EMI based on income-derived values
                calculateEMI()
                
                // Record bypass action in metadata
                recordOfferMetadata("REFERRAL", minLoanAmount, maxLoanAmount, 
                                    10.5f, "MANUAL_BYPASS")
            } catch (e: Exception) {
                AppLogger.e("Error in income-based bypassApproval: ${e.message}", e)
                
                // Fallback to sensible defaults if we can't get income
                _state.update { currentState ->
                    currentState.copy(
                        offerStatus = LoanOfferUI.Status.REFERRAL,
                        minLoanAmount = 100000f,
                        maxLoanAmount = 2000000f,
                        selectedLoanAmount = 500000f,
                        minTenure = 12f,
                        maxTenure = 60f,
                        selectedTenure = 36f,
                        interestRate = 10.5f,
                        errorMessage = "Your application has been referred to an underwriter for manual review."
                    )
                }
                
                // Calculate EMI based on defaults
                calculateEMI()
            }
        }
        
        // Save the bypass decision to repository
        viewModelScope.launch {
            try {
                AppLogger.d("Saving loan offer bypass selection to repository")
                loanRepository.saveLoanOfferSelection(
                    amount = _state.value.selectedLoanAmount.toLong(),
                    tenure = _state.value.selectedTenure.toInt(),
                    interestRate = _state.value.interestRate,
                    emi = _state.value.calculatedEMI.toLong(),
                    processingFee = _state.value.processingFee.toLong()
                )
                AppLogger.d("Successfully saved loan offer bypass selection")
                
                // Update offer status in Firestore
                saveOfferStatusToFirestore("REFERRAL")
            } catch (e: Exception) {
                AppLogger.e("Error saving loan offer bypass selection: ${e.message}", e)
            }
        }
    }
    
    /**
     * Record offer metadata to track offer generation and user interactions
     */
    private fun recordOfferMetadata(
        status: String,
        minAmount: Float,
        maxAmount: Float,
        interestRate: Float,
        source: String = "BRE", 
        errorDetails: String? = null
    ) {
        viewModelScope.launch {
            try {
                if (currentApplicationId != null) {
                    val eventData = mutableMapOf<String, Any>(
                        "offerStatus" to status,
                        "minAmount" to minAmount,
                        "maxAmount" to maxAmount,
                        "interestRate" to interestRate,
                        "timestamp" to LocalDateTime.now().toString(),
                        "offerSource" to source
                    )
                    
                    // Add error details if present
                    if (errorDetails != null) {
                        eventData["errorDetails"] = errorDetails
                    }
                    
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = currentApplicationId!!,
                        eventType = MetadataEventType.SECTION_COMPLETED,
                        metadata = eventData
                    ).collect { /* collect to execute the flow */ }
                }
            } catch (e: Exception) {
                AppLogger.e("Error recording offer metadata: ${e.message}", e)
            }
        }
    }
    
    /**
     * Record LLM interaction events for tracking API usage and performance
     */
    private fun recordLlmInteractionEvent(
        callType: String,
        status: String,
        failureReason: String? = null
    ) {
        viewModelScope.launch {
            try {
                if (currentApplicationId != null) {
                    val eventData = mutableMapOf<String, Any>(
                        "llmCallType" to callType,
                        "status" to status,
                        "timestamp" to LocalDateTime.now().toString()
                    )
                    
                    // Add failure reason if present
                    if (failureReason != null && status == "FAILED") {
                        eventData["failureReason"] = failureReason
                    }
                    
                    // Determine event type based on status
                    val eventType = if (status == "INITIATED") {
                        MetadataEventType.LLM_CALL_INITIATED
                    } else {
                        MetadataEventType.LLM_CALL_COMPLETED
                    }
                    
                    metadataRepository.recordLlmInteractionEvent(
                        applicationId = currentApplicationId!!,
                        llmInteraction = com.loansai.unassisted.domain.model.LlmInteractionEvent(
                            llmCallType = callType,
                            status = status,
                            failureReason = failureReason
                        )
                    ).collect { /* collect to execute the flow */ }
                }
            } catch (e: Exception) {
                AppLogger.e("Error recording LLM interaction: ${e.message}", e)
            }
        }
    }
    
    /**
     * Save the loan offer status to Firestore database
     */
    private fun saveOfferStatusToFirestore(status: String) {
        viewModelScope.launch {
            try {
                val application = loanRepository.getCurrentApplication()
                if (application != null) {
                    AppLogger.d("Updating loan offer status in Firestore: $status")
                    // You would typically call a repository method here, but for simplicity:
                    loanRepository.updateApplicationStatus(
                        applicationId = application.id,
                        status = ApplicationStatus.IN_PROGRESS
                    )
                    AppLogger.d("Successfully updated loan offer status in Firestore")
                } else {
                    AppLogger.w("Could not update loan offer status in Firestore: no current application")
                }
            } catch (e: Exception) {
                AppLogger.e("Error updating loan offer status in Firestore: ${e.message}", e)
            }
        }
    }

    fun saveProgress() {
        viewModelScope.launch {
            try {
                // Get current application
                val currentApp = loanRepository.getCurrentApplication() ?: return@launch
                currentApplicationId = currentApp.id
                
                // Create list of completed steps
                val completedSteps = currentApp.completedSteps.toMutableList()
                
                // Add LOAN_OFFER to completed steps if not already there
                if (!completedSteps.contains(ApplicationStep.LOAN_OFFER)) {
                    completedSteps.add(ApplicationStep.LOAN_OFFER)
                    
                    // Log step completion in metadata
                    val eventData = mapOf(
                        "stepName" to ApplicationStep.LOAN_OFFER.name,
                        "timestamp" to LocalDateTime.now().toString(),
                        "offerStatus" to _state.value.offerStatus.name,
                        "loanAmount" to _state.value.selectedLoanAmount,
                        "tenure" to _state.value.selectedTenure,
                        "emi" to _state.value.calculatedEMI
                    )
                    
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = currentApp.id,
                        eventType = MetadataEventType.SECTION_COMPLETED,
                        metadata = eventData
                    ).collect { /* collect to execute the flow */ }
                }
                
                // Set next step as EMPLOYMENT_VERIFICATION
                val nextStep = ApplicationStep.EMPLOYMENT_VERIFICATION
                
                // Update application progress
                loanRepository.saveApplicationProgressWithFlow(
                    applicationId = currentApp.id,
                    completedSteps = completedSteps,
                    currentStep = nextStep
                ).collect { /* Ignore the result */ }
                
                AppLogger.d("Saved progress: currentStep=${nextStep.name}, completed=${completedSteps.size} steps")
                
                // End screen visit when user proceeds to next screen
                endScreenVisit()
            } catch (e: Exception) {
                AppLogger.e("Error saving progress: ${e.message}", e)
            }
        }
    }
    
    // Override onCleared to ensure screen visit is tracked properly when ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        endScreenVisit()
    }
}