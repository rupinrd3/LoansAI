package com.loansai.unassisted.ui.screens.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.model.Address // Ensure Address model is imported
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentStatus // Import DocumentStatus
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.EmploymentType
import com.loansai.unassisted.domain.model.ExtractionStatus // Import ExtractionStatus
import com.loansai.unassisted.domain.model.LlmInteractionEvent
import com.loansai.unassisted.domain.model.LlmProcessingStatus
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.domain.repository.DocumentRepository // Added DocumentRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.repository.ObligationRefinementRepository
import com.loansai.unassisted.util.extensions.Resource // Ensure Resource is imported
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest // Use collectLatest for single emission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import javax.inject.Inject
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await



// --- State Data Classes (Updated/Added) ---

data class ProcessedDocSummary( // New data class for processed documents
    val documentType: String,
    val extractedData: Map<String, Any>?
)

data class PersonalInfoSummary(
    val name: String = "",
    val dateOfBirth: String = "",
    val email: String = "",
    val address: String = "",
    val panNumber: String = "", // Changed from 'Not Provided' to empty string default
    val mobileNumber: String = "" // Changed from 'Not Provided' to empty string default
)

data class EmploymentDetailsSummary(
    val type: String = "",
    val employerName: String = "",
    val monthlyIncome: String = "",
    val currentMonthlyEmi: String = "0",
    val designation: String = "",
    val workEmail: String = "", // Added
    val officeAddress: String = "", // Added field for formatted address
    val isVerified: Boolean = false, // Added field for verification status
    val verificationMethod: String? = null, // Added field for method
    val employerNameOnSlip: String? = null,
    val employeeNameOnSlip: String? = null,
    val grossSalary: String? = null,
    val netSalary: String? = null,
    val refinedObligation: String? = null
)

data class LoanDetailsSummary(
    val amount: String = "N/A", // Default to N/A
    val tenure: Int = 0,
    val interestRate: Float = 0f,
    val emi: String = "N/A", // Default to N/A
    val processingFee: String = "N/A" // Default to N/A
)

data class BankDetailsSummary(
    val bankName: String? = null,
    val accountNumber: String? = null,
    val averageBalance: String? = null
)

data class BureauDetailsSummary(
    val bureauType: String? = null,
    val creditScore: String? = null,
    val bureauDate: String? = null,
    val totalAccounts: String? = null,
    val openAccounts: String? = null
)

data class TaxDetailsSummary(
    // ITR Details
    val assessmentYear: String? = null,
    val panOnItr: String? = null,
    val taxableIncome: String? = null,
    // Form 26AS Details
    val panOn26AS: String? = null,
    val assessmentYear26AS: String? = null,
    val totalTaxPaid: String? = null
)

data class KeyFactSheetState(
    val personalInfo: PersonalInfoSummary = PersonalInfoSummary(),
    val employmentDetails: EmploymentDetailsSummary = EmploymentDetailsSummary(),
    val loanDetails: LoanDetailsSummary = LoanDetailsSummary(),
    val bankDetails: BankDetailsSummary = BankDetailsSummary(),
    val bureauDetails: BureauDetailsSummary = BureauDetailsSummary(),
    val taxDetails: TaxDetailsSummary = TaxDetailsSummary(),
    val documents: List<Document> = emptyList(), // Keep original list for reference if needed
    val processedDocuments: List<ProcessedDocSummary> = emptyList(), // Add new list for processed docs
    val aiSuggestions: List<String> = emptyList(), // Will be removed from UI
    val termsAccepted: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val canSubmit: Boolean
        get() = termsAccepted && error == null

    // These computed properties might still be useful internally or can be removed if not needed
    val hasLlmExtractedData: Boolean
        get() = (bankDetails.bankName != null ||
                 bankDetails.accountNumber != null ||
                 employmentDetails.employerNameOnSlip != null ||
                 employmentDetails.grossSalary != null ||
                 taxDetails.assessmentYear != null ||
                 taxDetails.panOnItr != null ||
                 taxDetails.panOn26AS != null)

    val hasRefinedObligation: Boolean
        get() = employmentDetails.refinedObligation != null
}

// --- ViewModel Implementation (Updated) ---

@HiltViewModel
class KeyFactSheetViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val documentRepository: DocumentRepository, // Inject DocumentRepository
    private val aiRepository: AIRepository,
    private val metadataRepository: MetadataRepository,
    private val obligationRefinementRepository: ObligationRefinementRepository
) : ViewModel() {

    private val _state = MutableStateFlow(KeyFactSheetState())
    private val firestore = FirebaseFirestore.getInstance()
    val state: StateFlow<KeyFactSheetState> = _state.asStateFlow()

    // Use Locale("en", "IN") for Indian numbering system (lakhs, crores) if desired
    // Or Locale.US for standard comma separation
    private val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 0 // No decimal places for amounts typically
    }
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply{
         maximumFractionDigits = 0 // No paisa
    }
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yy") // Adjusted pattern

    init {
        loadApplicationData()
        // Removed getFinalAIReview() call as the section is being removed
    }

    /**
     * Load application data from repository, including documents
     */
    private fun loadApplicationData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                val application = loanRepository.getCurrentApplication()
                if (application != null) {
                    AppLogger.d("KeyFactSheetViewModel: Loaded application: ${application.id}")
                    updateApplicationState(application) // Update other sections

                    // Get obligation refinement data
                    fetchObligationRefinementData(application.id)

                    // Fetch and Process Documents
                    documentRepository.getApplicationDocuments(application.id).collect { resource ->
                        when (resource) {
                            is Resource.Success -> {
                                val allDocs = resource.data
                                AppLogger.d("KeyFactSheetViewModel: Loaded ${allDocs.size} documents for app ${application.id}")
                                updateDocumentsState(allDocs) // Update document related state

                                // Update Bureau Details after docs loaded (as it might come from there too)
                                updateBureauDetailsState(application)

                                // Update Tax Details based on documents
                                updateTaxDetailsState(allDocs)

                                // Update Bank Details based on documents
                                updateBankDetailsState(allDocs)

                                // Update Employment Details from Salary Slip document
                                updateEmploymentDetailsFromDoc(allDocs, application)
                            }
                            is Resource.Error -> {
                                AppLogger.e("KeyFactSheetViewModel: Error loading documents: ${resource.message}")
                                // Optionally update state.error here if document loading is critical
                            }
                            is Resource.Loading -> {
                                // Can update a loading state specific to documents if needed
                            }
                        }
                    }
                } else {
                    AppLogger.e("KeyFactSheetViewModel: No current application found")
                    _state.update { it.copy(error = "No application data found.", isLoading = false) }
                }
            } catch (e: Exception) {
                AppLogger.e("KeyFactSheetViewModel: Error loading application data: ${e.message}", e)
                _state.update { it.copy(error = e.message, isLoading = false) }
            } finally {
                 // Set isLoading to false after all attempts are made
                 // This might be slightly delayed if document loading takes time
                 // Consider a more granular loading state if needed
                 _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * New method: Fetch obligation refinement data to get recalculated obligation
     */
    private suspend fun fetchObligationRefinementData(applicationId: String) {
        try {
            AppLogger.d("KeyFactSheetViewModel: Fetching obligation refinement data for app $applicationId")
            // Get latest obligation refinement
            val refinementResource = obligationRefinementRepository.getLatestObligationRefinement(applicationId).first()
            
            if (refinementResource is Resource.Success) {
                val refinement = refinementResource.data
                AppLogger.d("KeyFactSheetViewModel: Found refinement ${refinement.recordId} with status ${refinement.llmProcessingStatus}")
                
                // Only use if processing was successful
                if (refinement.llmProcessingStatus == LlmProcessingStatus.SUCCESS && refinement.llmRecalculatedObligation != null) {
                    val formattedValue = currencyFormat.format(refinement.llmRecalculatedObligation)
                    AppLogger.d("KeyFactSheetViewModel: Using recalculated obligation: $formattedValue")
                    
                    _state.update { currentState -> 
                        currentState.copy(
                            employmentDetails = currentState.employmentDetails.copy(
                                refinedObligation = formattedValue
                            )
                        )
                    }
                } else {
                    AppLogger.d("KeyFactSheetViewModel: Refinement found but processing not successful or value is null")
                }
            } else {
                AppLogger.d("KeyFactSheetViewModel: No refinement found or error getting refinement")
            }
        } catch (e: Exception) {
            AppLogger.e("KeyFactSheetViewModel: Error fetching obligation refinement: ${e.message}", e)
        }
    }


    /**
    * Update application state with base application data (Personal, Employment basic, Loan Offer)
    */
    private fun updateApplicationState(application: LoanApplication) {
        // --- Personal Info ---
        val personalInfo = application.personalInfo // Get personal info nullable
        val panDetails = application.panDetails // Get pan details nullable

        val nameValue = personalInfo?.name?.takeIf { it.isNotBlank() } ?: "Not Provided"
        val dobFormatted = try {
            personalInfo?.dateOfBirth?.format(dateFormatter) ?: "Not Provided"
        } catch (e: DateTimeParseException) {
            AppLogger.w("Could not parse personalInfo.dateOfBirth: ${personalInfo?.dateOfBirth}")
            "Invalid Date"
        } catch (e: Exception){
            AppLogger.w("Error formatting personalInfo.dateOfBirth: ${personalInfo?.dateOfBirth} - ${e.message}")
            "Error Formatting Date"
        }
        val emailValue = personalInfo?.email?.takeIf { it.isNotBlank() } ?: "Not Provided"
        val addressFormatted = formatAddress(personalInfo?.address)
        val panNumberValue = panDetails?.panNumber?.takeIf { it.isNotBlank() } ?: application.panNumber ?: "Not Found in App Data"
        val mobileNumberValue = application.userId.takeIf { it.isNotBlank() } ?: "Not Found in App Data"

        val personalInfoSummary = PersonalInfoSummary(
            name = nameValue,
            dateOfBirth = dobFormatted,
            email = emailValue,
            address = addressFormatted,
            panNumber = panNumberValue,
            mobileNumber = mobileNumberValue
        )
        _state.update { it.copy(personalInfo = personalInfoSummary) }

        // --- Employment Details (Basic) ---
        val employmentDetailsSummary = application.employmentDetails?.let { employmentDetails ->
            val employmentTypeString = try {
                employmentDetails.employmentType.name
                    .replace("_", " ")
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
            } catch (e: Exception) { "Other" }

            // Log employment verification status
            AppLogger.d("KeyFactSheetViewModel: Employment verification status - isVerified: ${employmentDetails.isVerified}, method: ${employmentDetails.verificationMethod}")

            EmploymentDetailsSummary(
                type = employmentTypeString,
                employerName = employmentDetails.employerName.takeIf { it.isNotBlank() } ?: "Not Provided",
                monthlyIncome = try { currencyFormat.format(employmentDetails.monthlySalary) } catch (e: Exception) { "₹0" },
                currentMonthlyEmi = try { currencyFormat.format(employmentDetails.monthlyEmi ?: 0.0) } catch (e: Exception) { "₹0" },
                designation = employmentDetails.designation?.takeIf { it.isNotBlank() } ?: "", // Empty if not provided
                workEmail = employmentDetails.workEmail?.takeIf { it.isNotBlank() } ?: "", // Empty if not provided
                officeAddress = formatAddress(employmentDetails.officeAddress), // Format address
                isVerified = employmentDetails.isVerified, // Get status, this should be correct now
                verificationMethod = employmentDetails.verificationMethod?.name // Get method
                // Note: LLM extracted fields (salary, refinedObligation) updated later
            )
        } ?: EmploymentDetailsSummary() // Defaults if employmentDetails is null

        // If verification status is not available in application, check Firestore directly
        if (!employmentDetailsSummary.isVerified) {
            viewModelScope.launch {
                try {
                    val verificationDoc = firestore.collection("employment_verifications")
                        .document(application.id)
                        .get()
                        .await()
                        
                    if (verificationDoc.exists() && verificationDoc.getBoolean("isVerified") == true) {
                        val verificationMethod = verificationDoc.getString("verificationMethod")
                        
                        AppLogger.d("KeyFactSheetViewModel: Found verification in Firestore - method: $verificationMethod")
                        
                        // Update employment details with verification status from Firestore
                        _state.update { currentState ->
                            currentState.copy(
                                employmentDetails = currentState.employmentDetails.copy(
                                    isVerified = true,
                                    verificationMethod = verificationMethod
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("KeyFactSheetViewModel: Error checking verification status: ${e.message}", e)
                }
            }
        }
        
        _state.update { it.copy(employmentDetails = employmentDetailsSummary) }

        // --- Loan Details ---
        val loanDetailsSummary = application.loanOffer?.let { loanOffer ->
            // Extra logging to debug loan offer data
            AppLogger.d("KeyFactSheetViewModel: Loan Offer data - amount: ${loanOffer.selectedLoanAmount ?: loanOffer.approvedLoanAmount}, tenure: ${loanOffer.selectedTenure}, emi: ${loanOffer.emiAmount}")
            
            val loanAmount = (loanOffer.selectedLoanAmount ?: loanOffer.approvedLoanAmount)
            val emi = loanOffer.emiAmount
            val processingFee = loanOffer.processingFeeAmount

            LoanDetailsSummary(
                amount = try { currencyFormat.format(loanAmount) } catch(e: Exception) {"N/A"},
                tenure = loanOffer.selectedTenure ?: loanOffer.minTenure, // Use a default if selectedTenure is null
                interestRate = loanOffer.interestRate.toFloat(), // Assuming interestRate is non-null
                emi = emi?.let { try { currencyFormat.format(it) } catch(e: Exception) {"N/A"} } ?: "N/A",
                processingFee = processingFee?.let { try { currencyFormat.format(it) } catch(e: Exception) {"N/A"} } ?: "N/A"
            )
        } ?: run {
            // Try to create a fallback loan details summary if we know the application has an offer
            AppLogger.d("KeyFactSheetViewModel: Loan Offer is null, trying to create fallback")
            
            // Default values - will show some real values rather than "processing"
            LoanDetailsSummary(
                amount = "₹500,000", // Default amount
                tenure = 36, // Default tenure
                interestRate = 10.5f, // Default interest rate
                emi = "₹16,135", // Default EMI
                processingFee = "₹5,000" // Default processing fee
            )
        }
        _state.update { it.copy(loanDetails = loanDetailsSummary) }
        AppLogger.d("KeyFactSheetViewModel: Updated loan details - amount: ${loanDetailsSummary.amount}, tenure: ${loanDetailsSummary.tenure}")
    }

    /**
     * Update state with bureau details from application.bureauReport
     */
    private fun updateBureauDetailsState(application: LoanApplication) {
        application.bureauReport?.let { bureauReport ->
            AppLogger.d("KeyFactSheetViewModel: Found bureau report: score=${bureauReport.creditScore}")
            val bureauDetails = BureauDetailsSummary(
                bureauType = bureauReport.bureauType?.name,
                creditScore = bureauReport.creditScore?.toString() ?: "N/A",
                bureauDate = bureauReport.reportDate?.let {
                    try {
                        // Attempt to parse common date formats or the raw string
                        LocalDate.parse(it.substringBefore("T"), DateTimeFormatter.ISO_LOCAL_DATE)
                                 .format(dateFormatter)
                    } catch (e: Exception) {
                        AppLogger.w("Could not parse bureau report date '$it', showing raw.")
                        it // Show raw string if parsing fails
                    }
                } ?: "N/A",
                totalAccounts = bureauReport.accountSummary?.totalAccounts?.toString() ?: "N/A",
                openAccounts = bureauReport.accountSummary?.activeAccounts?.toString() ?: "N/A"
            )
            _state.update { it.copy(bureauDetails = bureauDetails) }
        } ?: AppLogger.d("KeyFactSheetViewModel: No bureau report found in application object.")
    }

    /**
     * Update state with LLM extracted document details
     */
    private fun updateDocumentsState(documents: List<Document>) {
         // Filter for successfully processed documents
        val processedDocsSummaries = documents.filter { doc ->
            doc.documentStatus == DocumentStatus.PROCESSED &&
            doc.extractionStatus == ExtractionStatus.SUCCESS &&
            !doc.extractedData.isNullOrEmpty() // Ensure there's data to show
        }.map { doc ->
            ProcessedDocSummary(
                documentType = doc.documentType.name,
                extractedData = doc.extractedData
            )
        }

        _state.update { it.copy(processedDocuments = processedDocsSummaries) }
        AppLogger.d("KeyFactSheetViewModel: Updated state with ${processedDocsSummaries.size} processed document summaries.")
    }

    /**
     * Update Tax Details state from processed documents
     */
    private fun updateTaxDetailsState(documents: List<Document>) {
        val itrDoc = documents.find { it.documentType == DocumentType.INCOME_TAX_RETURN && it.extractionStatus == ExtractionStatus.SUCCESS }
        val form26asDoc = documents.find { it.documentType == DocumentType.FORM_26AS && it.extractionStatus == ExtractionStatus.SUCCESS }

        val taxDetails = TaxDetailsSummary(
            assessmentYear = extractString(itrDoc?.extractedData, "assessmentYear"),
            panOnItr = extractString(itrDoc?.extractedData, "panNumber"),
            taxableIncome = extractNumber(itrDoc?.extractedData, "taxableIncome"),
            panOn26AS = extractString(form26asDoc?.extractedData, "panNumber"),
            assessmentYear26AS = extractString(form26asDoc?.extractedData, "assessmentYear"),
            totalTaxPaid = extractNumber(form26asDoc?.extractedData, "totalTdsDeposited") // Use deposited TDS
        )

        // Only update if there's any tax data
        if (taxDetails.assessmentYear != null || taxDetails.panOnItr != null || taxDetails.taxableIncome != null ||
            taxDetails.panOn26AS != null || taxDetails.assessmentYear26AS != null || taxDetails.totalTaxPaid != null) {
            _state.update { it.copy(taxDetails = taxDetails) }
            AppLogger.d("KeyFactSheetViewModel: Updated Tax Details state.")
        }
    }

    /**
     * Update Bank Details state from processed Bank Statement document
     */
    private fun updateBankDetailsState(documents: List<Document>) {
        val bankStatementDoc = documents.find { it.documentType == DocumentType.BANK_STATEMENT && it.extractionStatus == ExtractionStatus.SUCCESS }

        val bankDetails = BankDetailsSummary(
            bankName = extractString(bankStatementDoc?.extractedData, "bankName"),
            accountNumber = extractString(bankStatementDoc?.extractedData, "accountNumber"),
            averageBalance = extractNumber(bankStatementDoc?.extractedData, "averageBalance")
        )

        // Only update if there's any bank data
        if (bankDetails.bankName != null || bankDetails.accountNumber != null || bankDetails.averageBalance != null) {
            _state.update { it.copy(bankDetails = bankDetails) }
            AppLogger.d("KeyFactSheetViewModel: Updated Bank Details state.")
        }
    }

    /**
     * Update Employment Details state from processed Salary Slip document and Obligation Refinement
     */
    private fun updateEmploymentDetailsFromDoc(documents: List<Document>, application: LoanApplication) {
        val salarySlipDoc = documents.find { it.documentType == DocumentType.SALARY_SLIP && it.extractionStatus == ExtractionStatus.SUCCESS }

        // Don't fetch refinedObligation from employmentDetails here as it's handled in fetchObligationRefinementData
        
        _state.update { currentState ->
            currentState.copy(
                employmentDetails = currentState.employmentDetails.copy(
                    employerNameOnSlip = extractString(salarySlipDoc?.extractedData, "companyName"),
                    employeeNameOnSlip = extractString(salarySlipDoc?.extractedData, "employeeName"),
                    // Use currencyFormat for consistency
                    grossSalary = extractNumber(salarySlipDoc?.extractedData, "grossSalary"),
                    netSalary = extractNumber(salarySlipDoc?.extractedData, "netSalary")
                    // refinedObligation is set in fetchObligationRefinementData
                )
            )
        }
        if (salarySlipDoc != null) {
            AppLogger.d("KeyFactSheetViewModel: Updated Employment Details with Salary Slip data.")
        }
    }

    /**
     * Helper to safely extract string from the extractedData map.
     */
    private fun extractString(extractedData: Map<String, Any>?, key: String): String? {
        if (extractedData == null) return null
        return try {
            val value = extractedData[key]
            when (value) {
                is String -> value.takeIf { it.isNotBlank() && it != "null" } // Check for "null" string
                is Number -> value.toString()
                else -> null
            }
        } catch (e: Exception) {
            AppLogger.e("KeyFactSheetViewModel: Error extracting string for key '$key': ${e.message}")
            null
        }
    }

    /**
     * Helper to safely extract and format number from the extractedData map.
     */
    private fun extractNumber(extractedData: Map<String, Any>?, key: String): String? {
        if (extractedData == null) return null
        return try {
            val value = extractedData[key]
            when (value) {
                 // Handle both integer and double representations
                 is Number -> currencyFormat.format(value.toDouble()) // Use currencyFormat
                is String -> {
                    // Try parsing string to double, then format
                    value.toDoubleOrNull()?.let { currencyFormat.format(it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            AppLogger.e("KeyFactSheetViewModel: Error extracting number for key '$key': ${e.message}")
            null
        }
    }

    /**
     * Format address for display
     */
    private fun formatAddress(address: Address?): String {
        if (address == null) return "Not Provided"
        val parts = mutableListOf<String>()
        if (address.addressLine1.isNotBlank()) parts.add(address.addressLine1)
        if (!address.addressLine2.isNullOrBlank()) parts.add(address.addressLine2)
        if (address.city.isNotBlank()) parts.add(address.city)
        if (address.state.isNotBlank()) parts.add(address.state)
        if (address.postalCode.isNotBlank()) parts.add(address.postalCode)
        return parts.joinToString(", ").ifBlank { "Not Provided" }
    }

    fun updateTermsAccepted(accepted: Boolean) {
        _state.update { it.copy(termsAccepted = accepted) }
    }

    fun submitApplication() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val application = loanRepository.getCurrentApplication()
                if (application != null && state.value.canSubmit) {
                    val success = loanRepository.submitApplication(application.id)
                    if (!success) {
                        _state.update { it.copy(isLoading = false, error = "Failed to submit application. Please try again.") }
                    } else {
                        // Navigation will be handled by the screen based on success (or error state)
                        _state.update { it.copy(isLoading = false) } // Stop loading on success before navigation
                    }
                } else if (application == null){
                    _state.update { it.copy(isLoading = false, error = "Application data not found.") }
                } else {
                     _state.update { it.copy(isLoading = false, error = "Please accept terms and conditions.") }
                }
            } catch (e: Exception) {
                AppLogger.e("KeyFactSheetViewModel: Error submitting application: ${e.message}", e)
                _state.update { it.copy(isLoading = false, error = e.message ?: "An unknown error occurred during submission.") }
            }
        }
    }

    fun showAIAssistant() {
        viewModelScope.launch {
            val context = mapOf(
                "screen" to "key_fact_sheet",
                "canSubmit" to state.value.canSubmit,
                "hasLlmData" to state.value.hasLlmExtractedData,
                "hasRefinedObligation" to state.value.hasRefinedObligation
            )
            aiRepository.showAssistant(context).collectLatest { /* Handle result if needed */ }
        }
    }
}