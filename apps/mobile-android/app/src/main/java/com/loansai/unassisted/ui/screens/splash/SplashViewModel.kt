package com.loansai.unassisted.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.loansai.unassisted.util.logger.AppLogger

/**
 * ViewModel for the splash screen
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated
    
    init {
        viewModelScope.launch {
            // First, check if we have a valid auth state
            AppLogger.d("Checking authentication status...")
            
            // Add a small delay to ensure splash screen is visible
            delay(1000)
            
            // Check if the user is authenticated from the repository
            userRepository.isUserAuthenticated().collect { isAuthenticated ->
                AppLogger.d("User authentication status: $isAuthenticated")
                
                // If not authenticated, ensure we're signed out
                if (!isAuthenticated) {
                    AppLogger.d("User not authenticated, cleaning up")
                    try {
                        userRepository.signOut().collect {
                            // After signout is complete, update state
                            _isAuthenticated.value = false
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error during signout: ${e.message}", e)
                        // Still update state even if signout fails
                        _isAuthenticated.value = false
                    }
                } else {
                    AppLogger.d("User is authenticated")
                    _isAuthenticated.value = true
                    
                    // Optional: Verify that we can get the user data as an extra validation
                    // Only uncomment if you want this additional check
                    /*
                    userRepository.getCurrentUser().collect { userResult ->
                        // Use pattern matching to handle the result
                        when (userResult) {
                            is Resource.Success -> {
                                val user = userResult.getOrNull()
                                if (user != null) {
                                    AppLogger.d("Current user retrieved: ${user.id}")
                                    _isAuthenticated.value = true
                                } else {
                                    AppLogger.e("User result success but null user")
                                    _isAuthenticated.value = false
                                }
                            }
                            is Resource.Error -> {
                                AppLogger.e("Error getting current user: ${userResult.errorOrNull()}")
                                // Auth token might be invalid, sign out and restart flow
                                userRepository.signOut().collect {}
                                _isAuthenticated.value = false
                            }
                            is Resource.Loading -> {
                                // This is handled by the initial isLoading state
                            }
                        }
                    }
                    */
                }
            }
        }
    }
}