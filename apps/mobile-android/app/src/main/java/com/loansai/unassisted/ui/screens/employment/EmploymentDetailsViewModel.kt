package com.loansai.unassisted.ui.screens.employment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.model.Address
import com.loansai.unassisted.domain.model.AddressType
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.Employer
import com.loansai.unassisted.domain.model.EmploymentDetails
import com.loansai.unassisted.domain.model.EmploymentType
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.model.ScreenVisit
import com.loansai.unassisted.domain.model.SectionTiming
import com.loansai.unassisted.domain.repository.EmployerRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.extensions.isValidEmail
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * UI state for Employment Details screen
 */
data class EmploymentDetailsUIState(
    val isLoading: Boolean = false,
    
    // Employment type selection
    val employmentType: EmploymentType? = null,
    
    // Employer search and selection
    val employerSearchQuery: String = "",
    val employerSearchResults: List<Employer> = emptyList(),
    val isSearching: Boolean = false,
    val selectedEmployer: Employer? = null,
    
    // Government entity details (for government employees)
    val governmentEntityName: String = "",
    val governmentEntityNameError: String? = null,
    
    // Common employment details
    val designation: String = "",
    val designationError: String? = null,
    val department: String = "",
    val workEmail: String = "",
    val workEmailError: String? = null,
    
    // Office address
    val addressLine1: String = "",
    val addressLine1Error: String? = null,
    
    // Note: Address Line 2 is now merged into Address Line 1
    
    val city: String = "",
    val cityError: String? = null,
    val state: String = "",
    val stateError: String? = null,
    val postalCode: String = "",
    val postalCodeError: String? = null,
    
    // Income and loan obligations
    val monthlySalary: String = "",
    val monthlySalaryError: String? = null,
    val monthlyEmi: String = "",
    val monthlyEmiError: String? = null,
    
    // Error and navigation states
    val error: String? = null,
    val fatalError: String? = null,
    val navigateToDocumentUpload: Boolean = false
)

/**
 * ViewModel for the Employment Details screen
 */
@HiltViewModel
class EmploymentDetailsViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val userRepository: UserRepository,
    private val employerRepository: EmployerRepository,
    private val metadataRepository: MetadataRepository

) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EmploymentDetailsUIState())
    val uiState: StateFlow<EmploymentDetailsUIState> = _uiState.asStateFlow()
    
    private var currentApplicationId: String? = null
    private var screenVisitId: String? = null
    private var sectionEmploymentTypeId: String? = null
    private var sectionEmployerDetailsId: String? = null
    private var sectionOfficeAddressId: String? = null
    private var sectionIncomeObligationsId: String? = null
    
    // Trigger employer search when query changes
    private val _searchQueryFlow = MutableStateFlow("")
    
    init {
        // Get current application ID
        viewModelScope.launch {
            userRepository.getCurrentUser().collectLatest { userResource ->
                if (userResource is Resource.Success) {
                    currentApplicationId = userResource.data.currentApplicationId
                    
                    // Record screen visit
                    currentApplicationId?.let { appId ->
                        recordScreenVisit(appId)
                        loadEmploymentDetails(appId)
                    }
                }
            }
        }
        
        // Set up debounced employer search
        setupEmployerSearch()
    }
    
    /**
     * Record screen visit metadata
     */
    private fun recordScreenVisit(applicationId: String) {
        viewModelScope.launch {
            try {
                val screenVisit = ScreenVisit(
                    screenName = "EmploymentDetailsScreen",
                    startTime = LocalDateTime.now()
                )
                
                metadataRepository.recordScreenVisit(applicationId, screenName = screenVisit.screenName)
                    .first() // Collect just the first emission
            } catch (e: Exception) {
                AppLogger.e("Failed to record screen visit: ${e.message}", e)
            }
        }
    }
    
    /**
     * End screen visit when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        
        // End the screen visit
        currentApplicationId?.let { appId ->
            viewModelScope.launch {
                try {
                    metadataRepository.endScreenVisit(appId, "EmploymentDetailsScreen")
                        .first()
                } catch (e: Exception) {
                    AppLogger.e("Failed to end screen visit: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Start section timing
     */
    private fun startSectionTiming(sectionName: String) {
        currentApplicationId?.let { appId ->
            viewModelScope.launch {
                try {
                    metadataRepository.startSectionTiming(
                        applicationId = appId,
                        screenName = "EmploymentDetailsScreen",
                        sectionName = sectionName
                    ).first()
                } catch (e: Exception) {
                    AppLogger.e("Failed to start section timing: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Complete section timing
     */
    private fun completeSectionTiming(sectionName: String) {
        currentApplicationId?.let { appId ->
            viewModelScope.launch {
                try {
                    metadataRepository.completeSectionTiming(
                        applicationId = appId,
                        screenName = "EmploymentDetailsScreen",
                        sectionName = sectionName
                    ).first()
                } catch (e: Exception) {
                    AppLogger.e("Failed to complete section timing: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Set up debounced employer search when query changes
     */
    @OptIn(FlowPreview::class)
    private fun setupEmployerSearch() {
        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300) // Wait for 300ms of inactivity
                .filter { it.length >= 3 } // Only search with 3+ characters
                .distinctUntilChanged() // Avoid duplicate searches
                .collectLatest { query ->
                    if (query.isNotBlank()) {
                        searchEmployersInternal(query)
                    }
                }
        }
    }
    
    /**
     * Load employment details from existing application
     */
    private fun loadEmploymentDetails(applicationId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            loanRepository.getApplication(applicationId).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        val application = result.data
                        
                        // If employment details exist, populate the form
                        application.employmentDetails?.let { details ->
                            _uiState.update { currentState ->
                                currentState.copy(
                                    isLoading = false,
                                    employmentType = details.employmentType,
                                    // If employer ID exists, fetch the employer details
                                    governmentEntityName = if (details.employmentType == EmploymentType.GOVERNMENT) 
                                        details.employerName else "",
                                    designation = details.designation ?: "",
                                    department = details.department ?: "",
                                    workEmail = details.workEmail ?: "",
                                    // Merge Address Line 1 and Line 2 into Address Line 1
                                    addressLine1 = details.officeAddress?.let { 
                                        val line1 = it.addressLine1
                                        val line2 = it.addressLine2 ?: ""
                                        if (line2.isNotEmpty()) "$line1, $line2" else line1
                                    } ?: "",
                                    city = details.officeAddress?.city ?: "",
                                    state = details.officeAddress?.state ?: "",
                                    postalCode = details.officeAddress?.postalCode ?: "",
                                    monthlySalary = details.monthlySalary.toString(),
                                    // Initialize monthlyEmi with 0 if not available
                                    monthlyEmi = details.monthlyEmi?.toString() ?: "0"
                                )
                            }
                            
                            // If private sector, fetch employer details
                            if (details.employmentType == EmploymentType.PRIVATE_SECTOR && details.employerId != null) {
                                fetchEmployerDetails(details.employerId)
                            }
                        } ?: run {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to load employment details: ${result.message}"
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
     * Fetch employer details by ID
     */
    private fun fetchEmployerDetails(employerId: String) {
        viewModelScope.launch {
            employerRepository.getEmployerDetails(employerId).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        _uiState.update { 
                            it.copy(selectedEmployer = result.data) 
                        }
                    }
                    is Resource.Error -> {
                        AppLogger.e("Failed to fetch employer details: ${result.message}")
                        // Non-critical error, don't update UI state
                    }
                    is Resource.Loading -> {
                        // Not handling loading state for this operation
                    }
                }
            }
        }
    }
    
    /**
    * Update employment type
    */
    fun updateEmploymentType(type: EmploymentType) {
        // Start section timing if first selection or changing type
        if (_uiState.value.employmentType == null) {
            startSectionTiming("EmploymentType")
        }
        
        _uiState.update { 
            it.copy(
                employmentType = type,
                // Clear employer-specific fields when switching types
                selectedEmployer = if (type == EmploymentType.GOVERNMENT) null else it.selectedEmployer,
                governmentEntityName = if (type == EmploymentType.PRIVATE_SECTOR) "" else it.governmentEntityName,
                governmentEntityNameError = null,
                employerSearchQuery = if (type == EmploymentType.GOVERNMENT) "" else it.employerSearchQuery,
                employerSearchResults = if (type == EmploymentType.GOVERNMENT) emptyList() else it.employerSearchResults,
                // Clear designation for private sector as it's not required
                designation = if (type == EmploymentType.PRIVATE_SECTOR) "" else it.designation,
                designationError = null,
                // Clear work email error when changing type (validation rules are different)
                workEmailError = null
            ) 
        }
        
        // Complete section timing if changing or confirming type
        completeSectionTiming("EmploymentType")
    }
    
    /**
     * Search employers by name
     */
    fun searchEmployers(query: String) {
        _uiState.update { 
            it.copy(employerSearchQuery = query) 
        }
        _searchQueryFlow.value = query
    }
    
    /**
     * Internal implementation of employer search
     */
    private fun searchEmployersInternal(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            
            employerRepository.searchEmployers(query).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        _uiState.update { 
                            it.copy(
                                isSearching = false,
                                employerSearchResults = result.data
                            ) 
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isSearching = false,
                                employerSearchResults = emptyList(),
                                error = "Failed to search employers: ${result.message}"
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
     * Select an employer from search results
     */
    fun selectEmployer(employer: Employer) {
        // Start Employer Details section timing if first selection
        if (_uiState.value.selectedEmployer == null) {
            startSectionTiming("EmployerDetails")
        }
        
        _uiState.update { 
            it.copy(
                selectedEmployer = employer,
                employerSearchQuery = employer.name,
                employerSearchResults = emptyList()
            ) 
        }
        
        // Complete Employer Details section
        completeSectionTiming("EmployerDetails")
    }
    
    /**
     * Update government entity name
     */
    fun updateGovernmentEntityName(name: String) {
        // Start Employer Details section timing if first input
        if (_uiState.value.governmentEntityName.isEmpty()) {
            startSectionTiming("EmployerDetails")
        }
        
        _uiState.update { 
            it.copy(
                governmentEntityName = name,
                governmentEntityNameError = null
            ) 
        }
    }
    
    /**
     * Update designation
     */
    fun updateDesignation(designation: String) {
        _uiState.update { 
            it.copy(
                designation = designation,
                designationError = null
            ) 
        }
    }
    
    /**
     * Update department
     */
    fun updateDepartment(department: String) {
        _uiState.update { it.copy(department = department) }
    }
    
    /**
     * Update work email
     */
    fun updateWorkEmail(email: String) {
        _uiState.update { 
            it.copy(
                workEmail = email,
                workEmailError = null
            ) 
        }
    }
    
    /**
     * Update address line 1 (merged with line 2)
     */
    fun updateAddressLine1(address: String) {
        // Start Office Address section timing if first input
        if (_uiState.value.addressLine1.isEmpty()) {
            startSectionTiming("OfficeAddress")
        }
        
        _uiState.update { 
            it.copy(
                addressLine1 = address,
                addressLine1Error = null
            ) 
        }
    }
    
    /**
     * Update city
     */
    fun updateCity(city: String) {
        _uiState.update { 
            it.copy(
                city = city,
                cityError = null
            ) 
        }
    }
    
    /**
     * Update state
     */
    fun updateState(state: String) {
        _uiState.update { 
            it.copy(
                state = state,
                stateError = null
            ) 
        }
    }
    
    /**
     * Update postal code
     */
    fun updatePostalCode(postalCode: String) {
        _uiState.update { 
            it.copy(
                postalCode = postalCode,
                postalCodeError = null
            ) 
        }
    }
    
    /**
     * Update monthly salary
     */
    fun updateMonthlySalary(salary: String) {
        // Start Income & Obligations section timing if first input
        if (_uiState.value.monthlySalary.isEmpty()) {
            startSectionTiming("IncomeObligations")
        }
        
        _uiState.update { 
            it.copy(
                monthlySalary = salary,
                monthlySalaryError = null
            ) 
        }
    }
    
    /**
     * Update monthly EMI
     */
    fun updateMonthlyEmi(emi: String) {
        _uiState.update { 
            it.copy(
                monthlyEmi = emi,
                monthlyEmiError = null
            ) 
        }
    }
    
    /**
     * Validate all fields and save employment details
     */
    fun validateAndSaveEmploymentDetails() {
        val currentState = _uiState.value
        
        // Basic validation checks
        val employmentTypeError = if (currentState.employmentType == null) {
            "Please select employment type"
        } else null
        
        // Employer validation based on employment type
        val governmentEntityNameError = if (currentState.employmentType == EmploymentType.GOVERNMENT && 
                                           currentState.governmentEntityName.isBlank()) {
            "Government entity name is required"
        } else null
        
        val employerError = if (currentState.employmentType == EmploymentType.PRIVATE_SECTOR && 
                               currentState.selectedEmployer == null) {
            "Please select an employer"
        } else null
        
        // Conditional validation for government employees only
        val designationError = if (currentState.employmentType == EmploymentType.GOVERNMENT && 
                                  currentState.designation.isBlank()) {
            "Designation is required for government employees"
        } else null
        
        // Email validation - ONLY required for Private Sector now
        val workEmailError = if (currentState.employmentType == EmploymentType.PRIVATE_SECTOR) {
            if (currentState.workEmail.isBlank()) {
                "Work email is required for private sector employees"
            } else if (!currentState.workEmail.isValidEmail()) {
                "Please enter a valid email address"
            } else null
        } else if (currentState.workEmail.isNotBlank() && !currentState.workEmail.isValidEmail()) {
            "Please enter a valid email address"
        } else null
        
        // Address validation
        val addressLine1Error = if (currentState.addressLine1.isBlank()) {
            "Address is required"
        } else null
        
        val cityError = if (currentState.city.isBlank()) {
            "City is required"
        } else null
        
        val stateError = if (currentState.state.isBlank()) {
            "State is required"
        } else null
        
        val postalCodeError = if (currentState.postalCode.isBlank()) {
            "Postal code is required"
        } else if (currentState.postalCode.length != 6 || !currentState.postalCode.all { it.isDigit() }) {
            "Please enter a valid 6-digit postal code"
        } else null
        
        // Income validation
        val monthlySalaryError = if (currentState.monthlySalary.isBlank()) {
            "Monthly salary is required"
        } else if (currentState.monthlySalary.toDoubleOrNull() == null) {
            "Please enter a valid amount"
        } else if (currentState.monthlySalary.toDoubleOrNull() ?: 0.0 <= 0) {
            "Monthly salary must be greater than zero"
        } else null
        
        // Monthly EMI validation (can be zero but must be a valid number)
        val monthlyEmiError = if (currentState.monthlyEmi.isBlank()) {
            "Monthly EMI is required (enter 0 if none)"
        } else if (currentState.monthlyEmi.toDoubleOrNull() == null) {
            "Please enter a valid amount"
        } else if ((currentState.monthlyEmi.toDoubleOrNull() ?: 0.0) < 0) {
            "Monthly EMI cannot be negative"
        } else null
        
        // Update UI state with validation errors
        _uiState.update { 
            it.copy(
                designationError = designationError,
                governmentEntityNameError = governmentEntityNameError,
                workEmailError = workEmailError,
                addressLine1Error = addressLine1Error,
                cityError = cityError,
                stateError = stateError,
                postalCodeError = postalCodeError,
                monthlySalaryError = monthlySalaryError,
                monthlyEmiError = monthlyEmiError
            ) 
        }
        
        // Check if there are any validation errors
        val hasErrors = employmentTypeError != null || 
                       governmentEntityNameError != null || 
                       employerError != null ||
                       (currentState.employmentType == EmploymentType.GOVERNMENT && designationError != null) || 
                       workEmailError != null ||
                       addressLine1Error != null || 
                       cityError != null || 
                       stateError != null ||
                       postalCodeError != null || 
                       monthlySalaryError != null || 
                       monthlyEmiError != null
        
        // If there are no errors, save the employment details
        if (!hasErrors) {
            saveEmploymentDetails()
        } else if (employmentTypeError != null) {
            // If employment type is not selected, show error
            _uiState.update { it.copy(error = employmentTypeError) }
        } else if (employerError != null) {
            // If employer is not selected for private sector, show error
            _uiState.update { it.copy(error = employerError) }
        }
    }
    
    /**
     * Save employment details to application
     */
    private fun saveEmploymentDetails() {
        val currentState = _uiState.value
        val applicationId = currentApplicationId ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Create office address - Parse address line 1 to separate parts if it contains a comma
            val addressParts = currentState.addressLine1.split(",", limit = 2)
            val line1 = addressParts[0].trim()
            val line2 = if (addressParts.size > 1) addressParts[1].trim() else null
            
            val officeAddress = Address(
                addressLine1 = line1,
                addressLine2 = line2,
                city = currentState.city,
                state = currentState.state,
                postalCode = currentState.postalCode,
                country = "India",
                addressType = AddressType.OFFICE
            )
            
            // Get employer name and ID based on employment type
            val (employerName, employerId) = when (currentState.employmentType) {
                EmploymentType.PRIVATE_SECTOR -> {
                    val employer = currentState.selectedEmployer
                    Pair(employer?.name ?: "", employer?.id)
                }
                EmploymentType.GOVERNMENT -> {
                    Pair(currentState.governmentEntityName, null)
                }
                else -> Pair("", null)
            }
            
            // Set designation, department for government employees
            val designation = if (currentState.employmentType == EmploymentType.GOVERNMENT) {
                currentState.designation
            } else null
            
            val department = if (currentState.employmentType == EmploymentType.GOVERNMENT && 
                                currentState.department.isNotEmpty()) {
                currentState.department
            } else null
            
            // Create employment details
            val employmentDetails = EmploymentDetails(
                employmentType = currentState.employmentType ?: EmploymentType.OTHER,
                employerName = employerName,
                employerId = employerId,
                designation = designation,
                department = department,
                employeeId = null, // Removed as per requirements
                workEmail = currentState.workEmail,
                officeAddress = officeAddress,
                monthlySalary = currentState.monthlySalary.toDoubleOrNull() ?: 0.0,
                monthlyEmi = currentState.monthlyEmi.toDoubleOrNull() ?: 0.0
            )
            
            // Complete all section timing
            completeSectionTiming("OfficeAddress")
            completeSectionTiming("IncomeObligations")
            
            // Save to repository
            loanRepository.updateEmploymentDetails(applicationId, employmentDetails).collectLatest { result ->
                when (result) {
                    is Resource.Success -> {
                        // Update application progress to next step - Document Upload
                        // Create list of completed steps
                        val application = result.data
                        val completedSteps = application.completedSteps.toMutableList()
                        
                        // Add EMPLOYMENT_DETAILS to completed steps if not already there
                        if (!completedSteps.contains(ApplicationStep.EMPLOYMENT_DETAILS)) {
                            completedSteps.add(ApplicationStep.EMPLOYMENT_DETAILS)
                        }
                        
                        // Set next step as DOCUMENT_UPLOAD
                        val nextStep = ApplicationStep.DOCUMENT_UPLOAD
                        
                        // Update application progress
                        loanRepository.saveApplicationProgressWithFlow(
                            applicationId = applicationId,
                            completedSteps = completedSteps,
                            currentStep = nextStep
                        ).collect { /* Ignore the result */ }
                        
                        AppLogger.d("Employment details saved and progress updated: currentStep=${nextStep.name}")
                        
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                navigateToDocumentUpload = true
                            ) 
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to save employment details: ${result.message}"
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
        _uiState.update { it.copy(navigateToDocumentUpload = false) }
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
        
        currentApplicationId?.let { loadEmploymentDetails(it) }
    }
}