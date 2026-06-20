package com.loansai.unassisted.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.extensions.isValidMobileNumber
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LoginUIState - State container for the Login screen
 */
data class LoginUIState(
    val phoneNumber: String = "",
    val isPhoneNumberValid: Boolean = false,
    val phoneNumberError: String? = null,
    val otp: String = "",
    val otpError: String? = null,
    val isOtpSent: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val fatalError: String? = null,
    val navigateToPrivacyPolicy: Boolean = false,
    val navigateToHome: Boolean = false
)

/**
 * ViewModel for the Login screen
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUIState())
    val uiState: StateFlow<LoginUIState> = _uiState.asStateFlow()
    
    /**
     * Update the phone number and validate it
     */
    fun updatePhoneNumber(phoneNumber: String) {
        val isValid = phoneNumber.isValidMobileNumber()
        _uiState.update { 
            it.copy(
                phoneNumber = phoneNumber,
                isPhoneNumberValid = isValid,
                phoneNumberError = if (phoneNumber.isNotEmpty() && !isValid && phoneNumber.length == 10) {
                    "Please enter a valid mobile number"
                } else {
                    null
                }
            ) 
        }
    }
    
    /**
     * Update the OTP
     */
    fun updateOtp(otp: String) {
        _uiState.update { it.copy(otp = otp, otpError = null) }
    }
    
    /**
     * Request OTP for the entered phone number
     */
    fun requestOtp() {
        val phoneNumber = uiState.value.phoneNumber
        
        // Validate phone number
        if (!phoneNumber.isValidMobileNumber()) {
            _uiState.update { 
                it.copy(phoneNumberError = "Please enter a valid 10-digit mobile number") 
            }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            userRepository.sendOTP(phoneNumber).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                isOtpSent = true,
                                otp = "",
                                otpError = null
                            ) 
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = result.message
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
     * Verify the OTP
     */
    fun verifyOtp() {
        val phoneNumber = uiState.value.phoneNumber
        val otp = uiState.value.otp
        
        // Validate OTP
        if (otp.length != 6) {
            _uiState.update { it.copy(otpError = "Please enter a valid 6-digit OTP") }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            userRepository.verifyOTP(phoneNumber, otp).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val user = result.data
                        AppLogger.i("User authenticated: ${user.id}")
                        
                        // Record privacy policy acceptance if user continues from OTP screen
                        // This implicitly means they've accepted policy as shown in text
                        viewModelScope.launch {
                            try {
                                userRepository.updatePrivacyPolicyAcceptance(true).collect { updateResult ->
                                    // Log result but proceed regardless
                                    if (updateResult is Resource.Error) {
                                        AppLogger.e("Failed to update privacy policy: ${updateResult.message}")
                                    }
                                }
                            } catch (e: Exception) {
                                AppLogger.e("Error updating privacy policy: ${e.message}", e)
                            }
                        }
                        
                        // Check if privacy policy has been accepted
                        if (user.isPrivacyPolicyAccepted) {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    navigateToHome = true
                                ) 
                            }
                        } else {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    navigateToPrivacyPolicy = true
                                ) 
                            }
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                otpError = "Invalid OTP. Please try again.",
                                error = result.message
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
                navigateToPrivacyPolicy = false,
                navigateToHome = false
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
    }
}