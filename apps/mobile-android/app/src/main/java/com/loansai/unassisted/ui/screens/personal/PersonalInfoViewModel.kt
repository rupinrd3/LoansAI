package com.loansai.unassisted.ui.screens.personal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.domain.model.Address
import com.loansai.unassisted.domain.model.AddressType
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.domain.model.Gender
import com.loansai.unassisted.domain.model.MaritalStatus
import com.loansai.unassisted.domain.model.PersonalInfo
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.PANRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.ui.screens.pan.PersonalInfoExtractedData
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.extensions.isValidMobileNumber
import com.loansai.unassisted.util.logger.AppLogger
import com.loansai.unassisted.util.validation.InputValidator
import com.loansai.unassisted.util.validation.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import java.time.format.DateTimeParseException 


/**
 * UI state for the Personal Information screen
 */
data class PersonalInfoUIState(
    val isLoading: Boolean = false,
    
    // Personal details
    val fullName: String = "",
    val fullNameError: String? = null,
    val dateOfBirth: LocalDate? = null,
    val dateOfBirthError: String? = null,
    val email: String = "",
    val emailError: String? = null,
    val gender: Gender? = null,
    val genderError: String? = null,
    val maritalStatus: MaritalStatus? = null,
    
    // Address
    val addressLine1: String = "",
    val addressLine1Error: String? = null,
    val addressLine2: String = "",
    val city: String = "",
    val cityError: String? = null,
    val state: String = "",
    val stateError: String? = null,
    val pinCode: String = "",
    val pinCodeError: String? = null,
    val addressType: AddressType? = AddressType.CURRENT,
    val residingSince: Int? = null,
    val residingSinceError: String? = null,
    
    // Additional contact
    val alternatePhone: String = "",
    val alternatePhoneError: String? = null,
    
    // Bureau data
    val bureauReport: BureauReport? = null,
    
    // Navigation and error states
    val error: String? = null,
    val fatalError: String? = null,
    val navigateToEmploymentDetails: Boolean = false,
    
    // Success state for animation
    val saveSuccess: Boolean = false
)

/**
 * ViewModel for the Personal Information screen
 */
@HiltViewModel
class PersonalInfoViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val userRepository: UserRepository,
    private val panRepository: PANRepository,
    private val preferencesDataSource: PreferencesDataSource
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PersonalInfoUIState())
    val uiState: StateFlow<PersonalInfoUIState> = _uiState.asStateFlow()
    
    private var currentApplicationId: String? = null
    
    init {
        // Get current application ID
        viewModelScope.launch {
            userRepository.getCurrentUser().collectLatest { userResource ->
                if (userResource is Resource.Success) {
                    currentApplicationId = userResource.data.currentApplicationId
                    
                    // Load personal info from application if available
                    currentApplicationId?.let { appId ->
                        loadPersonalInfo(appId)
                    }
                    
                    // Also load extracted data from PAN verification
                    loadExtractedPANData()
                }
            }
        }
    }
    
    /**
     * Load personal information from existing application
     */
    private fun loadPersonalInfo(applicationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            loanRepository.getApplication(applicationId).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        val application = result.data
                        
                        // If personal info exists, populate the form
                        application.personalInfo?.let { personalInfo ->
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    fullName = personalInfo.name,
                                    dateOfBirth = personalInfo.dateOfBirth,
                                    email = personalInfo.email,
                                    gender = personalInfo.gender,
                                    maritalStatus = personalInfo.maritalStatus,
                                    addressLine1 = personalInfo.address.addressLine1,
                                    addressLine2 = personalInfo.address.addressLine2 ?: "",
                                    city = personalInfo.address.city,
                                    state = personalInfo.address.state,
                                    pinCode = personalInfo.address.postalCode,
                                    alternatePhone = personalInfo.alternatePhoneNumber ?: ""
                                )
                            }
                        } ?: run {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to load personal information: ${result.message}"
                            ) 
                        }
                    }
                    is Resource.Loading -> {
                        // Already handling loading state
                    }
                }
            }
        }
    }
    
    /**
     * Load data extracted from PAN verification
     */
    fun loadExtractedPANData() {
        viewModelScope.launch {
            currentApplicationId?.let { appId ->
                try {
                    val json = preferencesDataSource.getString("extracted_data_$appId")
                    if (json != null) {
                        AppLogger.d("Found extracted data for $appId: $json")
                        val extractedData = Gson().fromJson(json, PersonalInfoExtractedData::class.java)
                        
                        // Try to parse the stored DOB string back to LocalDate
                        var extractedDate: LocalDate? = null
                        if (!extractedData.dateOfBirthString.isNullOrBlank()) {
                            try {
                                extractedDate = LocalDate.parse(extractedData.dateOfBirthString) // Parse YYYY-MM-DD string
                                AppLogger.d("Successfully parsed extracted DOB string to LocalDate: $extractedDate")
                            } catch (e: DateTimeParseException) {
                                AppLogger.e("Error parsing stored DOB string '${extractedData.dateOfBirthString}': ${e.message}", e)
                            }
                        }
                        
                        // Update UI state with extracted data if fields are empty
                        _uiState.update { currentState ->
                            var updatedState = currentState
                            
                            // Only update if current fields are empty
                            if (currentState.fullName.isEmpty() && extractedData.name.isNotEmpty()) {
                                AppLogger.d("Setting extracted name: ${extractedData.name}")
                                updatedState = updatedState.copy(fullName = extractedData.name)
                            }
                            
                            // Update DOB only if current is null AND we successfully parsed the extracted date
                            if (currentState.dateOfBirth == null && extractedDate != null) {
                                AppLogger.d("Setting extracted DOB: $extractedDate")
                                updatedState = updatedState.copy(
                                    dateOfBirth = extractedDate,
                                    dateOfBirthError = null // Clear any previous error if pre-filling
                                )
                            }
                            
                            updatedState
                        }
                    } else {
                        AppLogger.d("No extracted data found for application ID: $appId")
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error loading extracted PAN data: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Load bureau data to pre-populate fields
     */
    fun loadBureauData() {
        viewModelScope.launch {
            currentApplicationId?.let { appId ->
                _uiState.update { it.copy(isLoading = true) }
                
                panRepository.getBureauReport(appId).collectLatest { result ->
                    when (result) {
                        is Resource.Success -> {
                            val bureauReport = result.data
                            
                            // Pre-populate fields from bureau data if they're not already set
                            _uiState.update { currentState ->
                                var updatedState = currentState.copy(
                                    isLoading = false,
                                    bureauReport = bureauReport
                                )
                                
                                // Only update fields if they're empty and bureau data is available
                                if (currentState.fullName.isEmpty() && !bureauReport.customerName.isNullOrEmpty()) {
                                    updatedState = updatedState.copy(fullName = bureauReport.customerName)
                                }
                                
                                if (currentState.dateOfBirth == null && bureauReport.dateOfBirth != null) {
                                    updatedState = updatedState.copy(dateOfBirth = bureauReport.dateOfBirth)
                                }
                                
                                if (currentState.email.isEmpty() && !bureauReport.email.isNullOrEmpty()) {
                                    updatedState = updatedState.copy(email = bureauReport.email)
                                }
                                
                                if (currentState.gender == null && bureauReport.gender != null) {
                                    // Convert string gender to Gender enum
                                    val genderEnum = when (bureauReport.gender?.uppercase()) {
                                        "MALE" -> Gender.MALE
                                        "FEMALE" -> Gender.FEMALE
                                        else -> Gender.OTHER
                                    }
                                    updatedState = updatedState.copy(gender = genderEnum)
                                }
                                
                                if (currentState.addressLine1.isEmpty() && bureauReport.addresses.isNotEmpty()) {
                                    bureauReport.addresses.firstOrNull()?.let { addr ->
                                        // Use address fields correctly based on our BureauAddress model
                                        updatedState = updatedState.copy(
                                            addressLine1 = addr.addressLine1,
                                            addressLine2 = addr.addressLine2 ?: "",
                                            city = addr.city,
                                            state = addr.state,
                                            pinCode = addr.pincode
                                        )
                                    }
                                }
                                
                                updatedState
                            }
                        }
                        is Resource.Error -> {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    // Not setting error as this is optional pre-population
                                ) 
                            }
                        }
                        is Resource.Loading -> {
                            // Already handling loading state
                        }
                    }
                }
            }
        }
    }
    
    // Field update functions
    
    fun updateFullName(name: String) {
        _uiState.update { 
            it.copy(
                fullName = name,
                fullNameError = null
            ) 
        }
    }
    
    fun updateDateOfBirth(dateOfBirth: LocalDate) {
        val validationResult = InputValidator.validateDateOfBirth(dateOfBirth)
        
        _uiState.update { 
            it.copy(
                dateOfBirth = dateOfBirth,
                dateOfBirthError = if (validationResult is ValidationResult.Error) {
                    validationResult.message
                } else {
                    null
                }
            ) 
        }
    }
    
    fun updateEmail(email: String) {
        _uiState.update { 
            it.copy(
                email = email,
                emailError = null
            ) 
        }
    }
    
    fun updateGender(gender: Gender?) {
        _uiState.update { it.copy(gender = gender, genderError = null) }
    }
    
    fun updateMaritalStatus(maritalStatus: MaritalStatus) {
        _uiState.update { it.copy(maritalStatus = maritalStatus) }
    }
    
    fun updateAddressLine1(addressLine1: String) {
        _uiState.update { 
            it.copy(
                addressLine1 = addressLine1,
                addressLine1Error = null
            ) 
        }
    }
    
    fun updateAddressLine2(addressLine2: String) {
        _uiState.update { it.copy(addressLine2 = addressLine2) }
    }
    
    fun updateCity(city: String) {
        _uiState.update { 
            it.copy(
                city = city,
                cityError = null
            ) 
        }
    }
    
    fun updateState(state: String) {
        _uiState.update { 
            it.copy(
                state = state,
                stateError = null
            ) 
        }
    }
    
    fun updatePinCode(pinCode: String) {
        _uiState.update { 
            it.copy(
                pinCode = pinCode,
                pinCodeError = null
            ) 
        }
    }
    
    fun updateAlternatePhone(phoneNumber: String) {
        _uiState.update { 
            it.copy(
                alternatePhone = phoneNumber,
                alternatePhoneError = null
            ) 
        }
    }
    
    fun updateAddressType(type: AddressType) {
        _uiState.update { it.copy(addressType = type) }
    }
    
    fun updateResidingSince(year: Int) {
        _uiState.update { it.copy(residingSince = year, residingSinceError = null) }
    }
    
    /**
     * Validate all fields and save personal information
     */
    fun validateAndSavePersonalInfo() {
        val currentState = _uiState.value
        
        // Validate name
        val nameValidation = InputValidator.validateName(currentState.fullName)
        val nameError = if (nameValidation is ValidationResult.Error) nameValidation.message else null
        
        // Validate date of birth
        val dateOfBirthValidation = currentState.dateOfBirth?.let { 
            InputValidator.validateDateOfBirth(it)
        } ?: ValidationResult.Error("Date of birth is required")
        val dateOfBirthError = if (dateOfBirthValidation is ValidationResult.Error) dateOfBirthValidation.message else null
        
        // Validate email
        val emailValidation = InputValidator.validateEmail(currentState.email)
        val emailError = if (emailValidation is ValidationResult.Error) emailValidation.message else null
        
        // Validate address
        val addressLine1Error = if (currentState.addressLine1.isBlank()) "Address line 1 is required" else null
        val cityError = if (currentState.city.isBlank()) "City is required" else null
        val stateError = if (currentState.state.isBlank()) "State is required" else null
        val pinCodeError = if (currentState.pinCode.isBlank()) {
            "Postal code is required"
        } else if (currentState.pinCode.length != 6 || !currentState.pinCode.all { it.isDigit() }) {
            "Please enter a valid 6-digit postal code"
        } else {
            null
        }
        
        // Validate alternate phone number (if provided)
        val alternatePhoneError = if (currentState.alternatePhone.isNotEmpty() &&
                                          !currentState.alternatePhone.isValidMobileNumber()) {
            "Please enter a valid 10-digit mobile number"
        } else {
            null
        }
        
        // Update UI state with validation errors
        _uiState.update { 
            it.copy(
                fullNameError = nameError,
                dateOfBirthError = dateOfBirthError,
                emailError = emailError,
                addressLine1Error = addressLine1Error,
                cityError = cityError,
                stateError = stateError,
                pinCodeError = pinCodeError,
                alternatePhoneError = alternatePhoneError
            ) 
        }
        
        // Check if there are any validation errors
        val hasErrors = nameError != null || dateOfBirthError != null || emailError != null ||
                addressLine1Error != null || cityError != null || stateError != null ||
                pinCodeError != null || alternatePhoneError != null
        
        // If there are no errors, save the personal information
        if (!hasErrors) {
            savePersonalInfo()
        }
    }
    
    /**
     * Save personal information to application
     */
    private fun savePersonalInfo() {
        val currentState = _uiState.value
        val applicationId = currentApplicationId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Create address
            val address = Address(
                addressLine1 = currentState.addressLine1,
                addressLine2 = if (currentState.addressLine2.isNotEmpty()) currentState.addressLine2 else null,
                city = currentState.city,
                state = currentState.state,
                postalCode = currentState.pinCode,
                country = "India",
                addressType = currentState.addressType ?: AddressType.CURRENT
            )
            
            // Create personal info
            val personalInfo = PersonalInfo(
                name = currentState.fullName,
                dateOfBirth = currentState.dateOfBirth,
                email = currentState.email,
                gender = currentState.gender,
                maritalStatus = currentState.maritalStatus,
                address = address,
                alternatePhoneNumber = if (currentState.alternatePhone.isNotEmpty()) 
                                        currentState.alternatePhone else null
            )
            
            // Save to repository
            loanRepository.updatePersonalInfo(applicationId, personalInfo).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        // THIS IS THE BUGFIX - Ensure currentStep is updated in Firestore
                        AppLogger.d("Updating application step progress in Firestore")
                        loanRepository.updateApplicationStepProgress(
                            applicationId = applicationId,
                            completedSteps = listOf(ApplicationStep.PAN_VERIFICATION, ApplicationStep.PERSONAL_INFO),
                            currentStep = ApplicationStep.EMPLOYMENT_DETAILS
                        )
                        
                        // Log metadata event for form completion
                        try {
                            val metadataEvent = mapOf(
                                "eventType" to "SECTION_COMPLETED",
                                "screenName" to "PersonalInfo",
                                "sectionName" to "PersonalInfoForm",
                                "completedAt" to LocalDateTime.now().toString()
                            )
                            
                            loanRepository.logMetadataEvent(
                                applicationId = applicationId,
                                eventType = "SECTION_COMPLETED",
                                eventData = metadataEvent
                            ).collect { /* No processing needed */ }
                        } catch (e: Exception) {
                            AppLogger.e("Error logging metadata: ${e.message}", e)
                            // Non-critical, continue with navigation
                        }
                        
                        // Show success animation
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                saveSuccess = true
                            ) 
                        }
                        
                        // After a short delay, navigate to next screen
                        delay(1500)
                        _uiState.update { 
                            it.copy(navigateToEmploymentDetails = true) 
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to save personal information: ${result.message}"
                            ) 
                        }
                    }
                    is Resource.Loading -> {
                        // Already handling loading state
                    }
                }
            }
        }
    }



    /**
     * Reset navigation flags
     */
    fun resetNavigation() {
        _uiState.update { 
            it.copy(
                navigateToEmploymentDetails = false,
                saveSuccess = false
            ) 
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Retry after a fatal error
     */
    fun retry() {
        _uiState.update { 
            it.copy(
                isLoading = false,
                fatalError = null,
                error = null
            ) 
        }
        
        // Reload personal info
        currentApplicationId?.let { appId ->
            loadPersonalInfo(appId)
        }
    }
}