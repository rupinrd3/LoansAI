package com.loansai.unassisted.ui.screens.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentStatus
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.EmploymentType
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.domain.model.ProcessingMethod
import com.loansai.unassisted.domain.model.DocumentProcessingResult
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.usecase.verification.VerifyEmploymentUseCase
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.tasks.await


data class EmploymentVerificationState(
    val isMethodsExpanded: Boolean = true,
    val selectedMethod: VerificationMethod = VerificationMethod.EMAIL,
    val workEmail: String = "",
    val workEmailError: String? = null,
    val otp: String = "",
    val otpError: String? = null,
    val otpSent: Boolean = false,
    val idCardScanned: Boolean = false,
    val idCardDocument: Document? = null,
    val isVerified: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    // New field to store employment type
    val employmentType: EmploymentType? = null
) {
    val canSendOTP: Boolean
        get() = workEmail.isNotEmpty() && workEmailError == null && !isLoading
    
    val canVerifyOTP: Boolean
        get() = otp.isNotEmpty() && otpError == null && !isLoading
}

@HiltViewModel
class EmploymentVerificationViewModel @Inject constructor(
    private val verifyEmploymentUseCase: VerifyEmploymentUseCase,
    private val aiRepository: AIRepository,
    private val loanRepository: LoanRepository,
    private val firestore: FirebaseFirestore
) : ViewModel() {
    
    private val _state = MutableStateFlow(EmploymentVerificationState())
    val state: StateFlow<EmploymentVerificationState> = _state
    private var currentApplicationId: String? = null
    
    init {
        // Load current application to get employment details
        loadEmploymentDetails()
    }

    /**
     * Load employment details from current application to pre-populate fields
     */
    private fun loadEmploymentDetails() {
        viewModelScope.launch {
            try {
                val currentApp = loanRepository.getCurrentApplication()
                if (currentApp != null && currentApp.employmentDetails != null) {
                    // Get the employment type and work email
                    val employmentType = currentApp.employmentDetails.employmentType
                    val workEmail = currentApp.employmentDetails.workEmail ?: ""
                    
                    // Set current application ID for later use
                    currentApplicationId = currentApp.id
                    
                    // Update state with these values
                    _state.update { 
                        it.copy(
                            employmentType = employmentType,
                            workEmail = workEmail,
                            // Check if already verified
                            isVerified = currentApp.employmentDetails.isVerified
                        ) 
                    }
                    
                    AppLogger.d("Loaded employment details: type=$employmentType, email=$workEmail, verified=${currentApp.employmentDetails.isVerified}")
                    
                    // Check if verification is already done in Firestore
                    if (!currentApp.employmentDetails.isVerified) {
                        checkVerificationStatusInFirestore(currentApp.id)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error loading employment details: ${e.message}", e)
            }
        }
    }
    
    /**
     * Check if verification is already done in Firestore
     */
    private suspend fun checkVerificationStatusInFirestore(applicationId: String) {
        try {
            val verificationDoc = firestore.collection("employment_verifications")
                .document(applicationId)
                .get()
                .await()
                
            if (verificationDoc.exists() && verificationDoc.getBoolean("isVerified") == true) {
                AppLogger.d("Found verified status in Firestore for application $applicationId")
                _state.update { it.copy(isVerified = true) }
            }
        } catch (e: Exception) {
            AppLogger.e("Error checking verification status: ${e.message}", e)
        }
    }
    
    /**
     * Check if the user selected Private Sector in Employment Details
     */
    fun isPrivateSector(): Boolean {
        return _state.value.employmentType == EmploymentType.PRIVATE_SECTOR
    }
    
    fun toggleMethodsExpanded() {
        _state.update { it.copy(isMethodsExpanded = !it.isMethodsExpanded) }
    }
    
    fun selectMethod(method: VerificationMethod) {
        _state.update { 
            it.copy(
                selectedMethod = method,
                isVerified = false,
                error = null
            ) 
        }
    }
    
    fun updateWorkEmail(email: String) {
        // Only update email if not private sector
        if (_state.value.employmentType != EmploymentType.PRIVATE_SECTOR) {
            _state.update {
                val error = validateEmail(email)
                it.copy(
                    workEmail = email,
                    workEmailError = error,
                    isVerified = false
                )
            }
        }
    }
    
    fun updateOTP(otp: String) {
        _state.update {
            val error = validateOTP(otp)
            it.copy(
                otp = otp,
                otpError = error
            )
        }
    }
    
    fun sendOTP() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Check if application ID is available
                val appId = currentApplicationId
                if (appId == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Application ID not found. Please restart the application."
                        )
                    }
                    return@launch
                }
                
                AppLogger.d("Sending OTP to ${state.value.workEmail} for app $appId")
                
                // Call the use case to send OTP to the work email with application ID
                val result = verifyEmploymentUseCase.sendEmailOTP(appId, state.value.workEmail)
                
                _state.update {
                    it.copy(
                        otpSent = result,
                        error = if (!result) "Failed to send OTP. Please try again." else null,
                        isLoading = false
                    )
                }
                
                if (result) {
                    AppLogger.i("OTP sent successfully to ${state.value.workEmail}")
                } else {
                    AppLogger.e("Failed to send OTP to ${state.value.workEmail}")
                }
            } catch (e: Exception) {
                AppLogger.e("Error sending OTP: ${e.message}", e)
                _state.update {
                    it.copy(
                        error = "Failed to send OTP: ${e.message}",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun resendOTP() {
        sendOTP()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
    
    fun verifyOTP() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Check if application ID is available
                val appId = currentApplicationId
                if (appId == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = "Application ID not found. Please restart the application."
                        )
                    }
                    return@launch
                }
                
                AppLogger.d("Verifying OTP for ${state.value.workEmail} on app $appId")
                
                // Call the use case to verify the OTP with application ID
                val result = verifyEmploymentUseCase.verifyEmailOTP(
                    appId,
                    state.value.workEmail, 
                    state.value.otp
                )
                
                _state.update {
                    it.copy(
                        isVerified = result,
                        error = if (!result) "Invalid OTP. Please try again." else null,
                        isLoading = false
                    )
                }
                
                if (result) {
                    AppLogger.i("OTP verified successfully for ${state.value.workEmail}")
                } else {
                    AppLogger.e("OTP verification failed for ${state.value.workEmail}")
                }
            } catch (e: Exception) {
                AppLogger.e("Error verifying OTP: ${e.message}", e)
                _state.update {
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun scanIDCard() {
        // Trigger camera to scan ID card
        // This would typically integrate with the OCR service
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Mock implementation - in a real app, this would capture and process the image
                val document = Document(
                    id = "id_card_${System.currentTimeMillis()}",
                    applicationId = currentApplicationId ?: "unknown_app_id", // Use current app ID
                    documentType = DocumentType.ID_CARD,
                    fileType = FileType.JPG,
                    fileName = "Employee ID Card",
                    fileSize = 1024 * 1024, // 1MB mock size
                    uploadedAt = LocalDateTime.now(),
                    documentStatus = DocumentStatus.PROCESSED,
                    storageUrl = null,
                    localUri = "file://mock_id_card.jpg",
                    processingResult = DocumentProcessingResult(
                        isProcessed = true,
                        processedAt = LocalDateTime.now(),
                        processingMethod = ProcessingMethod.ML_KIT_OCR,
                        extractedFields = mapOf("company_name" to "Mock Company Name"),
                        ocrConfidence = 0.95f
                    )
                )
                
                // Verify the ID card contains the company name
                val result = verifyEmploymentUseCase.verifyIDCard(document)
                
                _state.update {
                    it.copy(
                        idCardScanned = true,
                        idCardDocument = document,
                        isVerified = result,
                        error = if (!result) "Company name not found on ID card." else null,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = e.message,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun selectIDCard() {
        // Similar to scanIDCard but would open gallery instead of camera
        // Implementation would be similar to the above
    }
    
    fun removeIDCard() {
        _state.update {
            it.copy(
                idCardScanned = false,
                idCardDocument = null,
                isVerified = false
            )
        }
    }
    
    /**
     * Save progress to update application step in Firestore
     */
    fun saveProgress() {
        viewModelScope.launch {
            try {
                // Get current application
                val currentApp = loanRepository.getCurrentApplication() ?: return@launch
                val applicationId = currentApp.id
                
                AppLogger.d("Saving employment verification progress for app $applicationId")

                // 1. Update application step progress
                val completedSteps = currentApp.completedSteps.toMutableList()
                if (!completedSteps.contains(ApplicationStep.EMPLOYMENT_VERIFICATION)) {
                    completedSteps.add(ApplicationStep.EMPLOYMENT_VERIFICATION)
                }
                val nextStep = ApplicationStep.KEY_FACT_SHEET // Next step is KFS

                loanRepository.saveApplicationProgressWithFlow(
                    applicationId = applicationId,
                    completedSteps = completedSteps,
                    currentStep = nextStep
                ).collect { /* Ignore the result */ }
                AppLogger.d("Saved progress: currentStep=${nextStep.name}, completed=${completedSteps.size} steps")

                // 2. Save detailed verification status in employment_verifications collection
                val verificationData = hashMapOf(
                    "applicationId" to applicationId,
                    "verificationMethod" to state.value.selectedMethod.toString(),
                    "isVerified" to state.value.isVerified,
                    "verifiedAt" to com.google.firebase.Timestamp.now(),
                    "verifiedEmail" to if (state.value.selectedMethod == VerificationMethod.EMAIL)
                        state.value.workEmail else null
                )
                
                firestore.collection("employment_verifications")
                    .document(applicationId) // Using appId as doc ID here
                    .set(verificationData) // Use set instead of merge to ensure clean data
                    .await()
                AppLogger.d("Employment verification status saved to employment_verifications collection")

                // 3. Update isVerified and verificationMethod within employmentDetails map in main application
                val employmentUpdateData = mapOf(
                    "employmentDetails.isVerified" to state.value.isVerified,
                    "employmentDetails.verificationMethod" to state.value.selectedMethod.name, // Store enum name
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )
                
                firestore.collection("applications")
                    .document(applicationId)
                    .update(employmentUpdateData)
                    .await()
                AppLogger.d("Updated isVerified status in main application document.")

            } catch (e: Exception) {
                AppLogger.e("Error saving progress or verification status: ${e.message}", e)
                // Optionally update UI state with error
            }
        }
    }

    /**
     * Show AI assistant with current context
     */
    fun showAIAssistant() {
        viewModelScope.launch {
            val context = mapOf(
                "screen" to "employment_verification",
                "method" to state.value.selectedMethod.toString(),
                "isVerified" to state.value.isVerified,
                "error" to (state.value.error ?: "")
            )
            
            aiRepository.showAssistant(context)
        }
    }
}

private fun validateEmail(email: String): String? {
    if (email.isEmpty()) {
        return "Email is required"
    }
    
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        return "Please enter a valid email address"
    }
    
    // Check if the domain matches the employer's domain (in a real app)
    // This is simplified here
    
    return null
}

private fun validateOTP(otp: String): String? {
    if (otp.isEmpty()) {
        return "OTP is required"
    }
    
    if (otp.length < 6) {
        return "Please enter the complete 6-digit OTP"
    }
    
    return null
}