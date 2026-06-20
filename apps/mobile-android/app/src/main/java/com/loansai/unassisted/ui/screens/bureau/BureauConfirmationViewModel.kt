package com.loansai.unassisted.ui.screens.bureau

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.loansai.unassisted.BuildConfig
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.model.ObligationRefinement
import com.loansai.unassisted.domain.model.TradelineItem
import com.loansai.unassisted.domain.repository.AppwriteRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.repository.ObligationRefinementRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.domain.model.LlmProcessingStatus
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext


/**
 * Enum representing the current processing state
 */
enum class ProcessingState {
    IDLE,
    SAVING_DATA,
    GEMINI_PROCESSING,
    PROCESSING_COMPLETE,
    PROCESSING_ERROR
}

/**
 * UI State for Bureau Confirmation Screen
 */
data class BureauConfirmationUiState(
    val tradelines: List<TradelineItem> = emptyList(),
    val emiValues: Map<String, String> = emptyMap(),
    val emiErrors: Set<String> = emptySet(),
    val comments: String = "",
    val isLoading: Boolean = false,
    // New fields for processing state:
    val processingState: ProcessingState = ProcessingState.IDLE,
    val processingMessage: String = "",
    val errorMessage: String? = null,
    val hasValidationErrors: Boolean = false,
    val isDevMode: Boolean = BuildConfig.DEBUG,
    val noTradelinesFound: Boolean = false,
    val autoNavigate: Boolean = false // Triggers navigation when true
)

/**
 * ViewModel for Bureau Confirmation Screen
 */

@HiltViewModel
class BureauConfirmationViewModel @Inject constructor(
    private val appwriteRepository: AppwriteRepository,
    private val loanRepository: LoanRepository,
    private val userRepository: UserRepository,
    private val metadataRepository: MetadataRepository,
    private val obligationRefinementRepository: ObligationRefinementRepository,
    private val preferencesDataSource: PreferencesDataSource // Inject PreferencesDataSource
) : ViewModel() {

    private val _uiState = MutableStateFlow(BureauConfirmationUiState())
    val uiState: StateFlow<BureauConfirmationUiState> = _uiState.asStateFlow()

    private var currentApplicationId: String? = null
    private var watchdogJob: Job? = null // Keep track of the watchdog job

    init {
        loadTradelines()
    }

    /**
    * Load tradelines from the bureau report
    */
    private fun loadTradelines() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            AppLogger.d("[BureauViewModel] Starting loadTradelines...")

            try {
                // Step 1: Get current application ID - try multiple approaches
                currentApplicationId = getCurrentApplicationId()

                if (currentApplicationId != null) {
                    AppLogger.d("[BureauViewModel] Current Application ID: $currentApplicationId")

                    // Step 2: Get PAN number - try multiple approaches
                    val panNumber = getPanNumber(currentApplicationId!!)

                    if (panNumber != null) {
                        AppLogger.d("[BureauViewModel] PAN Number found: $panNumber")

                        // Step 3: Get tradelines with the PAN number - use collect() for better error handling
                        AppLogger.d("[BureauViewModel] Calling appwriteRepository.getTradelines for PAN: $panNumber")

                        try {
                            appwriteRepository.getTradelines(panNumber).collect { tradelinesResource ->
                                when (tradelinesResource) {
                                    is Resource.Success -> {
                                        val allTradelines = tradelinesResource.data
                                        AppLogger.i("[BureauViewModel] Fetched ${allTradelines.size} tradelines from repository for PAN $panNumber.")

                                        // Log details of all tradelines for debugging
                                        allTradelines.forEachIndexed { index, tradeline ->
                                            AppLogger.d("[BureauViewModel] Tradeline ${index + 1}: ID=${tradeline.id}, " +
                                                    "Member=${tradeline.memberName}, Type=${tradeline.accountType}, " +
                                                    "Status='${tradeline.facilityStatus}', DateClosed='${tradeline.dateClosed}'")
                                        }

                                        // Filter for OPEN/ACTIVE accounts with IMPROVED LOGIC
                                        val openTradelines = allTradelines
                                            .filter { tradeline ->
                                                // Check if the facilityStatus indicates active - might be null or empty
                                                val isActiveStatus = tradeline.facilityStatus?.equals("Active", ignoreCase = true) ?: false

                                                // Check if the account is NOT closed - handle "Enter string" placeholders
                                                val isNotClosed = tradeline.dateClosed == null ||
                                                                tradeline.dateClosed.isBlank() ||
                                                                tradeline.dateClosed == "Enter string"

                                                // Check if there is a current balance
                                                val hasCurrentBalance = (tradeline.currentBalance ?: 0.0) > 0.0

                                                // For credit cards, they might be active even with zero balance
                                                val isCreditCard = tradeline.accountType?.contains("Credit Card", ignoreCase = true) ?: false
                                                val isActiveCreditCard = isCreditCard && isNotClosed

                                                // Loan is considered active if ANY of these conditions are true
                                                val isActive = isActiveStatus || (isNotClosed && hasCurrentBalance) || isActiveCreditCard

                                                AppLogger.d("[BureauViewModel] Filtering Tradeline: ${tradeline.memberName} - " +
                                                        "AccountType: ${tradeline.accountType}, " +
                                                        "CurrentBalance: ${tradeline.currentBalance}, " +
                                                        "DateClosed: ${tradeline.dateClosed}, " +
                                                        "FacilityStatus: ${tradeline.facilityStatus}, " +
                                                        "IsActive: $isActive")

                                                isActive
                                            }
                                            .map { tradeline ->
                                                // Map to TradelineItem with sensible defaults for missing values
                                                TradelineItem(
                                                    id = tradeline.id ?: UUID.randomUUID().toString(),
                                                    memberName = tradeline.memberName ?: "Unknown Lender",
                                                    accountType = tradeline.accountType ?: "Loan Account",
                                                    accountNumber = tradeline.accountNumber ?: "XXXXXXXX",
                                                    currentBalance = tradeline.currentBalance?.toInt() ?: 0,
                                                    // Use a safe default for EMI if not available
                                                    emiAmount = tradeline.emiAmount?.toInt() ?: 0,
                                                    dateOpened = tradeline.dateOpened,
                                                    dateClosed = if (tradeline.dateClosed == "Enter string") null else tradeline.dateClosed
                                                )
                                            }

                                        AppLogger.i("[BureauViewModel] Filtered to ${openTradelines.size} open tradelines.")

                                        // Check if no tradelines found
                                        if (openTradelines.isEmpty()) {
                                            AppLogger.w("[BureauViewModel] No open tradelines found after filtering. Setting noTradelinesFound = true.")
                                            _uiState.update { it.copy(
                                                isLoading = false,
                                                noTradelinesFound = true,
                                                errorMessage = "No active loans found in your bureau report."
                                            )}

                                            // Record screen visit
                                            recordScreenVisit("BUREAU_CONFIRMATION_EMPTY")
                                        } else {
                                            AppLogger.i("[BureauViewModel] Found ${openTradelines.size} open tradelines. Updating UI state.")
                                            // Initialize EMI values with the current EMI amounts
                                            val initialEmiValues = openTradelines.associate { tradeline ->
                                                tradeline.id to (tradeline.emiAmount.toString())
                                            }

                                            _uiState.update { it.copy(
                                                tradelines = openTradelines,
                                                emiValues = initialEmiValues,
                                                isLoading = false,
                                                noTradelinesFound = false
                                            )}

                                            // Record screen visit
                                            recordScreenVisit("BUREAU_CONFIRMATION")
                                        }
                                    }
                                    is Resource.Error -> {
                                        val errorMessage = tradelinesResource.message ?: "Unknown repository error"
                                        AppLogger.e("[BureauViewModel] Error in tradelines flow: $errorMessage")
                                        handleTradelinesError("Could not load your loan details: $errorMessage")
                                    }
                                    is Resource.Loading -> {
                                        // Already handled at the start of this method
                                        AppLogger.d("[BureauViewModel] Loading tradelines...")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("[BureauViewModel] Exception collecting tradelines flow: ${e.message}", e)
                            handleTradelinesError("Error loading loan details: ${e.message}")
                        }
                    } else {
                        AppLogger.e("[BureauViewModel] PAN details not found in the application object.")
                        handleTradelinesError("PAN details not found in application")
                    }
                } else {
                    AppLogger.e("[BureauViewModel] Current Application ID is null.")
                    handleTradelinesError("No active application found")
                }
            } catch (e: Exception) {
                AppLogger.e("[BureauViewModel] Exception loading tradelines: ${e.message}", e)
                handleTradelinesError("An error occurred while loading your loans: ${e.message}")
            }
        }
    }

    /**
     * Get current application ID - tries multiple approaches
     */
    private suspend fun getCurrentApplicationId(): String? {
        // Approach 1: Try userRepository.getCurrentUser()
        try {
            val userResource = userRepository.getCurrentUser().first()
            if (userResource is Resource.Success) {
                val appId = userResource.data.currentApplicationId
                if (!appId.isNullOrEmpty()) {
                    AppLogger.d("[BureauViewModel] Got application ID from userRepository: $appId")
                    return appId
                }
            }
        } catch (e: Exception) {
            AppLogger.e("[BureauViewModel] Error getting user from userRepository: ${e.message}", e)
        }

        // Approach 2: Try loanRepository.getCurrentApplication()
        try {
            val currentApp = loanRepository.getCurrentApplication()
            if (currentApp != null) {
                AppLogger.d("[BureauViewModel] Got application ID from loanRepository: ${currentApp.id}")
                return currentApp.id
            }
        } catch (e: Exception) {
            AppLogger.e("[BureauViewModel] Error getting application from loanRepository: ${e.message}", e)
        }

        // Approach 3: Try preferencesDataSource directly
        try {
            val appId = preferencesDataSource.getCurrentApplicationId()
            if (!appId.isNullOrEmpty()) {
                AppLogger.d("[BureauViewModel] Got application ID from preferences: $appId")
                return appId
            }
        } catch (e: Exception) {
            AppLogger.e("[BureauViewModel] Error getting application ID from preferences: ${e.message}", e)
        }

        AppLogger.e("[BureauViewModel] Failed to get application ID from all sources")
        return null
    }

    
    /**
    * Get PAN number for an application ID - tries multiple approaches (Cache-First)
    */
    private suspend fun getPanNumber(applicationId: String): String? {
        AppLogger.d("[BureauViewModel.getPanNumber] Attempting to find PAN for app: $applicationId")
        try {
            // --- Step 1: Check PreferencesDataSource SPECIFIC PAN Cache FIRST ---
            AppLogger.d("[BureauViewModel.getPanNumber] Checking PreferencesDataSource cache...")
            // Assuming preferencesDataSource is injected via Hilt
            val cachedPan = preferencesDataSource.getPanNumberForApplication(applicationId)
            if (!cachedPan.isNullOrBlank()) {
                AppLogger.i("[BureauViewModel.getPanNumber] Found PAN in Preferences Cache: $cachedPan")
                return cachedPan // Return cached PAN if found
            }
            AppLogger.d("[BureauViewModel.getPanNumber] PAN not found in Preferences cache.")

            // --- Step 2: Fetch LoanApplication object (uses its own internal caching/Firestore via repository) ---
            AppLogger.d("[BureauViewModel.getPanNumber] Fetching full LoanApplication object via repository...")
            // Use .first() to get the first emitted value (success or error)
            val appResource = loanRepository.getApplication(applicationId).first()

            // Check if the resource is Success before accessing data
            if (appResource is Resource.Success) {
                AppLogger.d("[BureauViewModel.getPanNumber] Successfully fetched LoanApplication object.")
                val applicationData = appResource.data // Get the LoanApplication data

                // --- Step 3: Check Direct `panNumber` Field in the Fetched Object ---
                val directPan = applicationData.panNumber // Check the top-level field added to LoanApplication model
                if (!directPan.isNullOrBlank()) {
                    AppLogger.d("[BureauViewModel.getPanNumber] Found direct PAN in fetched application object: $directPan")
                    // Cache the found PAN back to preferences for next time
                    preferencesDataSource.savePanNumberForApplication(applicationId, directPan)
                    return directPan // Return if found directly
                }
                AppLogger.d("[BureauViewModel.getPanNumber] Direct panNumber field in fetched object is null or blank.")

                // --- Step 4: Fallback to Nested `panDetails.panNumber` Field ---
                val nestedPan = applicationData.panDetails?.panNumber?.takeIf { it.isNotBlank() }
                if (!nestedPan.isNullOrBlank()) {
                    AppLogger.d("[BureauViewModel.getPanNumber] Found PAN in nested panDetails: $nestedPan")
                    // Cache the found PAN back to preferences for next time
                    preferencesDataSource.savePanNumberForApplication(applicationId, nestedPan)
                    return nestedPan // Return if found in nested details
                }
                AppLogger.d("[BureauViewModel.getPanNumber] Nested panDetails.panNumber field is null or blank.")

                // If neither is found in the fetched application object
                AppLogger.w("[BureauViewModel.getPanNumber] PAN not found in direct field or nested panDetails within fetched application object for app $applicationId")

            } else if (appResource is Resource.Error) {
                // Log the error from fetching the application
                AppLogger.w("[BureauViewModel.getPanNumber] Failed to get application object $applicationId to extract PAN: ${appResource.message}")
            }
        } catch (e: Exception) {
            // Catch any unexpected errors during the process
            AppLogger.e("[BureauViewModel.getPanNumber] Error getting PAN for application $applicationId: ${e.message}", e)
        }
        // Return null if not found or error occurred in any step
        AppLogger.e("[BureauViewModel.getPanNumber] Could not find PAN for application $applicationId from any source (Cache, Direct Field, Nested Field).")
        return null
    }




    /**
     * Helper method to handle tradelines load errors
     */
    private fun handleTradelinesError(message: String) {
        _uiState.update { it.copy(
            isLoading = false,
            noTradelinesFound = true, // Assume no tradelines if loading fails
            errorMessage = message
        )}

        // Record error event if we have an application ID
        currentApplicationId?.let { appId ->
            recordErrorEvent(appId, message)
        }
    }

    /**
     * Record a screen visit event
     */
    private fun recordScreenVisit(screenName: String) {
        currentApplicationId?.let { appId ->
            viewModelScope.launch {
                metadataRepository.recordScreenVisit(
                    applicationId = appId,
                    screenName = screenName
                ).collect { /* consume the flow, result ignored */ } // Use collect to execute the flow
            }
        }
    }

    /**
     * Record an error event
     */
    private fun recordErrorEvent(applicationId: String, errorMessage: String) {
        viewModelScope.launch {
            metadataRepository.sendEventToOrchestrator(
                applicationId = applicationId,
                eventType = MetadataEventType.ERROR_OCCURRED, // Use correct event type
                metadata = mapOf(
                    "errorEvent" to mapOf( // Nest under errorEvent as per MetadataModels
                        "screenName" to "BUREAU_CONFIRMATION",
                        "errorMessage" to errorMessage,
                        "timestamp" to System.currentTimeMillis().toString()
                    )
                )
            ).collect { /* consume the flow, result ignored */ } // Use collect to execute the flow
        }
    }


    /**
    * Retry processing after an error
    */
    fun retryProcessing() {
        _uiState.update { it.copy(
            processingState = ProcessingState.IDLE,
            processingMessage = "",
            errorMessage = null
        )}
        submitObligationRefinement()
    }

    /**
    * Handle case where user has no tradelines
    */
    fun continueWithoutTradelines() {
        viewModelScope.launch {
            try {
                // Save a "no tradelines" record in Firestore if we have an application ID
                if (currentApplicationId != null) {
                    AppLogger.d("Creating empty obligation refinement for application: $currentApplicationId")

                    // Create an empty obligation refinement to indicate user skipped
                    val refinementId = UUID.randomUUID().toString()
                    val emptyRefinement = ObligationRefinement(
                        recordId = refinementId,
                        applicationId = currentApplicationId!!,
                        userProvidedEmis = emptyMap(),
                        userComments = "User had no active loans in bureau",
                        llmProcessingStatus = LlmProcessingStatus.SUCCESS, // Treat as success (no processing needed)
                        llmRecalculatedObligation = 0  // Explicitly set to 0 for no obligations
                    )

                    try {
                        // Use .collect() to ensure the flow completes
                        obligationRefinementRepository.saveObligationRefinement(emptyRefinement).collect { saveResult ->
                             if (saveResult is Resource.Success) {
                                AppLogger.d("Successfully saved empty refinement record")
                             } else if (saveResult is Resource.Error) {
                                AppLogger.w("Failed to save empty refinement: ${saveResult.message}")
                             }
                        }
                    } catch (e: Exception) {
                        AppLogger.w("Failed to save empty refinement: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error in continueWithoutTradelines: ${e.message}", e)
            } finally {
                // Always set autoNavigate to true
                AppLogger.d("Setting autoNavigate = true (continueWithoutTradelines)")
                _uiState.update { it.copy(autoNavigate = true) }
            }
        }
    }

     /**
     * Update EMI value for a tradeline
     */
    fun updateEmiValue(tradelineId: String, value: String) {
        val currentEmiValues = _uiState.value.emiValues.toMutableMap()
        currentEmiValues[tradelineId] = value

        // Validate the new input
        val errors = validateEmiValues(currentEmiValues)

        _uiState.update { it.copy(
            emiValues = currentEmiValues,
            emiErrors = errors,
            hasValidationErrors = errors.isNotEmpty(),
            // Update error message based on validation result
            errorMessage = if (errors.isNotEmpty()) "Please enter valid EMI amounts (numbers only) for all loans" else null
        )}
    }

    /**
     * Update comments field
     */
    fun updateComments(value: String) {
        _uiState.update { it.copy(comments = value) }
    }

    /**
     * Validate EMI values to ensure they're all filled and numeric
     * @return Set of tradeline IDs with validation errors
     */
    private fun validateEmiValues(emiValues: Map<String, String>): Set<String> {
        val errors = mutableSetOf<String>()

        for (tradeline in _uiState.value.tradelines) {
            val value = emiValues[tradeline.id] ?: ""
             // Check if blank OR contains non-digit characters
             if (value.isBlank() || !value.all { it.isDigit() }) {
                errors.add(tradeline.id)
            }
        }

        return errors
    }


    /**
     * Submit the obligation refinement to backend (Corrected Logic)
     */
    fun submitObligationRefinement() {
        watchdogJob?.cancel() // Cancel previous watchdog if any
        watchdogJob = null

        viewModelScope.launch {
            val emiValues = _uiState.value.emiValues
            val errors = validateEmiValues(emiValues)

            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(
                    emiErrors = errors,
                    hasValidationErrors = true,
                    errorMessage = "Please enter valid EMI amounts (numbers only) for all loans"
                )}
                return@launch
            }

            _uiState.update { it.copy(
                processingState = ProcessingState.SAVING_DATA,
                processingMessage = "Saving your EMI information...",
                errorMessage = null,
                isLoading = true // Use isLoading flag for initial save
            )}

            var refinementId: String? = null
            var finalErrorMessageForState: String? = null // Use separate var for final state update
            val currentAppIdForErrorHandling = currentApplicationId // Capture for use in catch blocks

            try {
                AppLogger.d("Starting obligation refinement submission process")
                val currentAppId = currentApplicationId ?: throw Exception("Application ID not found")

                val numericEmiValues = emiValues.mapValues { (_, value) -> value.toIntOrNull() ?: 0 }

                refinementId = UUID.randomUUID().toString()
                AppLogger.d("Creating obligation refinement with ID: $refinementId")
                val obligationRefinement = ObligationRefinement(
                    recordId = refinementId,
                    applicationId = currentAppId,
                    userProvidedEmis = numericEmiValues,
                    userComments = _uiState.value.comments
                )

                AppLogger.d("Saving obligation refinement: $refinementId")
                // Use timeout for saving data
                val saveResult = withTimeoutOrNull(10000L) { // 10 seconds timeout for save
                    obligationRefinementRepository.saveObligationRefinement(obligationRefinement).first()
                }

                if (saveResult == null || saveResult is Resource.Error) {
                     throw Exception("Failed to save data" + (if(saveResult == null) " (timeout)" else ": ${(saveResult as Resource.Error).message}"))
                }
                AppLogger.d("Successfully saved refinement record")

                _uiState.update { it.copy(
                    processingState = ProcessingState.GEMINI_PROCESSING,
                    processingMessage = "Recalculating your EMI obligations...",
                    isLoading = false // Stop initial loading after save
                )}

                 metadataRepository.sendEventToOrchestrator(
                     applicationId = currentAppId,
                     eventType = MetadataEventType.OBLIGATION_REFINEMENT_SUBMITTED,
                      metadata = mapOf(
                         "obligationEvent" to mapOf(
                              "refinementRecordId" to refinementId,
                              "status" to "SUBMITTED",
                              "timestamp" to System.currentTimeMillis()
                          )
                      )
                 ).collect { /* ignore result */ }

                // Start watchdog timer - 60 seconds
                watchdogJob = CoroutineScope(Dispatchers.Default).launch {
                    delay(60000L)
                    if (isActive) { // Check if job wasn't cancelled
                        AppLogger.w("Watchdog timer expired ($refinementId) - auto-continuing after timeout")
                        withContext(Dispatchers.Main) {
                            // Check current state before overriding
                            if (_uiState.value.processingState != ProcessingState.PROCESSING_COMPLETE &&
                                _uiState.value.processingState != ProcessingState.PROCESSING_ERROR) {
                                val timeoutMsg = "Recalculation took longer than expected."
                                finalErrorMessageForState = timeoutMsg // Store for final state update
                                _uiState.update { it.copy(
                                    processingState = ProcessingState.PROCESSING_ERROR,
                                    processingMessage = timeoutMsg,
                                    errorMessage = timeoutMsg,
                                    isLoading = false,
                                    autoNavigate = true // Trigger navigation on timeout
                                )}
                            }
                        }
                    }
                }

                AppLogger.d("Triggering Gemini recalculation for refinement: $refinementId")
                 var recalcSuccess = false
                 var collectedErrorMessage: String? = null // Error message collected from flow

                 var finalRecalculatedObligation: Int? = null // Variable to hold the result

                try {
                    // Timeout for the recalculation flow itself (slightly less than watchdog)
                    withTimeout(55000L) {
                        obligationRefinementRepository.triggerObligationRecalculation(
                            obligationRefinement = obligationRefinement,
                            tradelineItems = uiState.value.tradelines
                        )
                            .catch { e -> 
                                AppLogger.e("Error collecting recalculation result: ${e.message}", e)
                                collectedErrorMessage = "Recalculation failed: ${e.message}"
                                if (watchdogJob?.isActive == true) {
                                    val errorMsg = collectedErrorMessage!!
                                    _uiState.update { it.copy(
                                        processingState = ProcessingState.PROCESSING_ERROR,
                                        processingMessage = errorMsg,
                                        errorMessage = errorMsg
                                    )}
                                }
                            }
                            .collect { recalcResult ->
                                when (recalcResult) {
                                    is Resource.Success -> {
                                        AppLogger.d("Gemini recalculation successful")
                                        recalcSuccess = true
                                        finalRecalculatedObligation = recalcResult.data.llmRecalculatedObligation // STORE the value
                                        AppLogger.d("Stored recalculated obligation: $finalRecalculatedObligation")
                                        
                                        if (watchdogJob?.isActive == true) {
                                            _uiState.update { it.copy(
                                                processingState = ProcessingState.PROCESSING_COMPLETE,
                                                processingMessage = "EMI obligation recalculation complete."
                                            )}
                                        } else {
                                            AppLogger.w("Watchdog already cancelled/finished, skipping SUCCESS state update for $refinementId")
                                        }
                                        return@collect
                                    }
                                    is Resource.Error -> {
                                        AppLogger.e("Gemini recalculation flow emitted error: ${recalcResult.message}")
                                        collectedErrorMessage = "Recalculation failed: ${recalcResult.message}"
                                        if (watchdogJob?.isActive == true) {
                                            val errorMsg = collectedErrorMessage!!
                                            _uiState.update { it.copy(
                                                processingState = ProcessingState.PROCESSING_ERROR,
                                                processingMessage = errorMsg,
                                                errorMessage = errorMsg
                                            )}
                                        } else {
                                            AppLogger.w("Watchdog already cancelled/finished, skipping ERROR state update for $refinementId")
                                        }
                                        return@collect
                                    }
                                    is Resource.Loading -> {
                                        AppLogger.d("Recalculation flow is loading...")
                                    }
                                }
                            }
                    }
                
                 } catch (timeoutError: TimeoutCancellationException) {
                     // Catch timeout specific to the recalculation flow
                     AppLogger.e("Gemini recalculation trigger timed out after 55 seconds for $refinementId")
                     collectedErrorMessage = "Recalculation timed out." // Capture timeout message
                     if (watchdogJob?.isActive == true) {
                         // Only update state if watchdog hasn't fired
                         val timeoutMsg = collectedErrorMessage!! // Use captured message
                         _uiState.update { it.copy(
                             processingState = ProcessingState.PROCESSING_ERROR,
                             processingMessage = timeoutMsg,
                             errorMessage = timeoutMsg
                         )}
                     } else {
                         AppLogger.w("Watchdog already cancelled/finished, skipping TIMEOUT state update for $refinementId")
                     }
                 }

                 // --- Final State Update and Navigation Trigger ---
                 watchdogJob?.cancel() // Cancel watchdog regardless of outcome now
                 watchdogJob = null

                 // Use the error message collected during the flow or outer catch for final state
                 finalErrorMessageForState = collectedErrorMessage ?: finalErrorMessageForState

                 // Only proceed to final navigation/state update if watchdog didn't already handle it
                 if (_uiState.value.processingState != ProcessingState.PROCESSING_ERROR || _uiState.value.errorMessage?.contains("longer than expected") != true) {
                     if (recalcSuccess) {
                         _uiState.update { it.copy(isLoading = false, processingState = ProcessingState.PROCESSING_COMPLETE, processingMessage = "Success!") }
                         delay(1000) // Brief pause to show success
                         AppLogger.d("Setting autoNavigate = true (Success)")
                         _uiState.update { it.copy(autoNavigate = true) }
                     } else {
                         // Ensure error state reflects the collected/timeout error
                         val errorMsg = finalErrorMessageForState ?: "An unknown error occurred during recalculation."
                         _uiState.update { it.copy(
                             isLoading = false,
                             processingState = ProcessingState.PROCESSING_ERROR,
                             processingMessage = errorMsg,
                             errorMessage = errorMsg
                         )}
                         // Optionally add a delay before auto-navigation on error
                         // delay(3000)
                         // AppLogger.d("Setting autoNavigate = true (Error)")
                         // _uiState.update { it.copy(autoNavigate = true) } // Or let user press retry
                     }
                 } else {
                      // If watchdog already triggered navigation due to timeout
                      AppLogger.d("Navigation already triggered by watchdog timeout.")
                      _uiState.update { it.copy(isLoading = false) } // Ensure loading spinner stops
                  }

            } catch (e: Exception) {
                // Catch errors during initial save or setup
                AppLogger.e("Error processing obligation refinement: ${e.message}", e)
                e.printStackTrace()

                watchdogJob?.cancel() // Cancel watchdog if it was started
                watchdogJob = null

                finalErrorMessageForState = e.message ?: "Unknown error during setup"

                _uiState.update { it.copy(
                    processingState = ProcessingState.PROCESSING_ERROR,
                    processingMessage = "Error: ${finalErrorMessageForState}",
                    errorMessage = finalErrorMessageForState,
                    isLoading = false
                )}

                // Optionally trigger navigation even on setup error after a delay
                // delay(3000)
                // AppLogger.d("Setting autoNavigate = true (Outer Catch Error)")
                // _uiState.update { it.copy(autoNavigate = true) }
            }
        }
    }

}