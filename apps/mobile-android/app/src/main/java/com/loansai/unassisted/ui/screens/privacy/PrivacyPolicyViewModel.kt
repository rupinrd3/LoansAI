package com.loansai.unassisted.ui.screens.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

/**
 * UI state for the Privacy Policy screen
 */
data class PrivacyPolicyUIState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val fatalError: String? = null,
    val navigateToHome: Boolean = false
)

/**
 * ViewModel for the Privacy Policy screen
 */
@HiltViewModel
class PrivacyPolicyViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PrivacyPolicyUIState())
    val uiState: StateFlow<PrivacyPolicyUIState> = _uiState.asStateFlow()
    
    /**
     * Accept the privacy policy
     */
    fun acceptPrivacyPolicy() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Version can be stored in a config or hardcoded for now
            val privacyPolicyVersion = "1.0"
            
            userRepository.updatePrivacyPolicyAcceptance(true).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        // Additional Firestore update if needed
                        try {
                            // This would ideally be part of the UserRepository implementation
                            // But adding as extra safeguard to ensure version and timestamp are recorded
                            val firestore = FirebaseFirestore.getInstance()
                            val auth = FirebaseAuth.getInstance()
                            
                            auth.currentUser?.uid?.let { userId ->
                                firestore.collection("users").document(userId)
                                    .update(
                                        mapOf(
                                            "isPrivacyPolicyAccepted" to true,
                                            "privacyPolicyVersion" to privacyPolicyVersion,
                                            "privacyPolicyAcceptedAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                    .addOnSuccessListener {
                                        AppLogger.d("Privacy policy acceptance recorded in Firestore")
                                    }
                                    .addOnFailureListener { e ->
                                        AppLogger.e("Error updating privacy policy in Firestore: ${e.message}", e)
                                        // Don't fail the flow if this update fails
                                    }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("Error with additional Firestore update: ${e.message}", e)
                            // Don't fail the flow if this update fails
                        }
                        
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                navigateToHome = true
                            ) 
                        }
                    }
                    is Resource.Error -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "Failed to update privacy policy acceptance: ${result.message}"
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
     * Reset navigation flags
     */
    fun resetNavigation() {
        _uiState.update { it.copy(navigateToHome = false) }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}