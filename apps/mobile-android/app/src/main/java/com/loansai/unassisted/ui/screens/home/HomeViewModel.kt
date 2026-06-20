package com.loansai.unassisted.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.loansai.unassisted.ui.navigation.Screen

/**
 * UI state for the Home screen
 */
data class HomeUIState(
    val isLoading: Boolean = false,
    val userName: String = "",
    val currentApplication: LoanApplication? = null,
    val error: String? = null,
    val fatalError: String? = null,
    val showVoidConfirmation: Boolean = false
)

/**
 * ViewModel for the Home screen
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val loanRepository: LoanRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUIState(isLoading = true))
    val uiState: StateFlow<HomeUIState> = _uiState.asStateFlow()
    
    init {
        loadUserData()
        loadUserApplications()
    }
    
    /**
     * Load user data (name, etc.)
     */
    private fun loadUserData() {
        viewModelScope.launch {
            userRepository.getCurrentUser().collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val user = result.data
                        _uiState.update {
                            it.copy(
                                userName = user.phoneNumber, // Use phone number for now; can be updated with real name later
                                isLoading = false
                            )
                        }
                        
                        // If there's a current application ID, load it
                        user.currentApplicationId?.let { appId ->
                            loadCurrentApplication(appId)
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                fatalError = "Failed to load user data: ${result.message}"
                            )
                        }
                    }
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }
    
    /**
     * Load user's loan applications
     */
    fun loadUserApplications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            userRepository.getCurrentUser().collect { userResult ->
                when (userResult) {
                    is Resource.Success -> {
                        val user = userResult.data
                        
                        // Load applications for this user
                        loanRepository.getUserApplications(user.id).collect { result ->
                            when (result) {
                                is Resource.Success -> {
                                    val applications = result.data
                                    
                                    // If there's a current application ID, use that
                                    if (user.currentApplicationId != null) {
                                        val currentApp = applications.find { it.id == user.currentApplicationId }
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                currentApplication = currentApp
                                            )
                                        }
                                    } 
                                    // Otherwise use the most recent application if any
                                    else if (applications.isNotEmpty()) {
                                        val mostRecent = applications.maxByOrNull { it.lastUpdatedAt }
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                currentApplication = mostRecent
                                            )
                                        }
                                        
                                        // Update user's current application ID
                                        mostRecent?.let { app ->
                                            userRepository.updateCurrentApplicationId(app.id)
                                        }
                                    } else {
                                        _uiState.update {
                                            it.copy(
                                                isLoading = false,
                                                currentApplication = null
                                            )
                                        }
                                    }
                                }
                                is Resource.Error -> {
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            error = "Failed to load applications: ${result.message}"
                                        )
                                    }
                                }
                                is Resource.Loading -> {
                                    _uiState.update { it.copy(isLoading = true) }
                                }
                            }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                fatalError = "Failed to load user data: ${userResult.message}"
                            )
                        }
                    }
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }
    
    /**
     * Load current application by ID
     */
    private fun loadCurrentApplication(applicationId: String) {
        viewModelScope.launch {
            loanRepository.getApplication(applicationId).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                currentApplication = result.data
                            )
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to load application: ${result.message}"
                            )
                        }
                    }
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }
    
    /**
     * Create a new loan application
     */
    fun createNewApplication(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            userRepository.getCurrentUser().collect { userResult ->
                if (userResult is Resource.Success) {
                    val user = userResult.data
                    
                    loanRepository.createApplication(user.id).collect { result ->
                        when (result) {
                            is Resource.Success -> {
                                val newApplication = result.data
                                
                                // Update current application in state
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        currentApplication = newApplication
                                    )
                                }
                                
                                // Update user's current application ID
                                userRepository.updateCurrentApplicationId(newApplication.id)
                                
                                // Navigate to PAN entry
                                onSuccess()
                            }
                            is Resource.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = "Failed to create application: ${result.message}"
                                    )
                                }
                            }
                            is Resource.Loading -> {
                                _uiState.update { it.copy(isLoading = true) }
                            }
                        }
                    }
                } else if (userResult is Resource.Error) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to get user data: ${userResult.message}"
                        )
                    }
                }
            }
        }
    }

    /**
    * Gets the appropriate screen to navigate to based on current application step
    */
    fun getResumeTargetScreen(): String {
        val currentApp = uiState.value.currentApplication ?: return Screen.PANEntry.route
        
        return when (currentApp.currentStep) {
            ApplicationStep.PAN_VERIFICATION -> Screen.PANEntry.route
            ApplicationStep.PERSONAL_INFO -> Screen.PersonalInfo.route
            ApplicationStep.EMPLOYMENT_DETAILS -> Screen.EmploymentDetails.route
            ApplicationStep.DOCUMENT_UPLOAD -> Screen.DocumentUpload.route
            ApplicationStep.LOAN_OFFER -> Screen.LoanOffer.route
            ApplicationStep.EMPLOYMENT_VERIFICATION -> Screen.EmploymentVerification.route
            ApplicationStep.REVIEW_AND_SUBMIT -> Screen.KeyFactSheet.route
            else -> Screen.Home.route
        }
    }

    // Add a new function to verify and resume application

    fun verifyAndResumeApplication(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                // Try to load from repository with more detailed logging
                AppLogger.d("Attempting to load current application from repository")
                val application = loanRepository.getCurrentApplication()
                
                if (application != null) {
                    AppLogger.d("Successfully loaded application: ${application.id}, step: ${application.currentStep}")
                    
                    // Successfully loaded application
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            currentApplication = application
                        ) 
                    }
                    
                    // Ensure the application is linked to the current user if needed
                    if (application.userId.isEmpty()) {
                        userRepository.getCurrentUser().collect { userResult ->
                            if (userResult is Resource.Success) {
                                val user = userResult.data
                                // Update application's userId if needed
                                // This is a simplification - in real app you might want to update this in Firestore
                                _uiState.update {
                                    it.copy(
                                        currentApplication = application.copy(userId = user.id)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Determine which screen to navigate to based on current step
                    val targetScreen = getResumeTargetScreen()
                    AppLogger.d("Resuming application to screen: $targetScreen")
                    onSuccess(targetScreen)
                } else {
                    AppLogger.e("Failed to load application from repository")
                    
                    // If no existing application found, check if we need to create one
                    userRepository.getCurrentUser().collect { userResult ->
                        if (userResult is Resource.Success) {
                            // User is logged in but no application exists
                            // Update UI to show they should create a new application
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = "You don't have an existing application. Please start a new one."
                                ) 
                            }
                        } else {
                            // User not properly authenticated
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = "Authentication required. Please log in again."
                                ) 
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error in verifyAndResumeApplication: ${e.message}", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Error loading application: ${e.message}"
                    ) 
                }
            }
        }
    }

    
    /**
     * Show void confirmation dialog
     */
    fun showVoidConfirmation() {
        _uiState.update { it.copy(showVoidConfirmation = true) }
    }
    
    /**
     * Dismiss void confirmation dialog
     */
    fun dismissVoidConfirmation() {
        _uiState.update { it.copy(showVoidConfirmation = false) }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}