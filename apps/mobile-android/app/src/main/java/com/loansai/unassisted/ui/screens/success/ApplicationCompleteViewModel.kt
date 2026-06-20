package com.loansai.unassisted.ui.screens.success

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApplicationCompleteState(
    val applicationNumber: String = "",
    val status: String = "",
    val approvedAmount: String = "",
    val emi: String = "",
    val isApproved: Boolean = false,
    val isUnderReview: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ApplicationCompleteViewModel @Inject constructor(
    private val loanRepository: LoanRepository,
    private val aiRepository: AIRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(ApplicationCompleteState())
    val state: StateFlow<ApplicationCompleteState> = _state
    
    init {
        loadApplicationResult()
    }
    
    private fun loadApplicationResult() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            try {
                val application = loanRepository.getCurrentApplication()
                
                if (application != null) {
                    updateApplicationState(application)
                } else {
                    _state.update { 
                        it.copy(
                            error = "Application data not found.",
                            isLoading = false
                        ) 
                    }
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
    
    private fun updateApplicationState(application: LoanApplication) {
        val isApproved = application.applicationStatus == ApplicationStatus.APPROVED
        val isUnderReview = application.applicationStatus == ApplicationStatus.UNDER_REVIEW
        
        _state.update {
            it.copy(
                applicationNumber = application.id,
                status = getStatusText(application.applicationStatus),
                approvedAmount = application.loanOffer?.approvedLoanAmount?.toString() ?: "",
                emi = application.loanOffer?.emiAmount?.toString() ?: "",
                isApproved = isApproved,
                isUnderReview = isUnderReview,
                isLoading = false
            )
        }
    }
    
    private fun getStatusText(status: ApplicationStatus): String {
        return when (status) {
            ApplicationStatus.APPROVED -> "Approved"
            ApplicationStatus.REJECTED -> "Rejected"
            ApplicationStatus.UNDER_REVIEW -> "Under Review"
            ApplicationStatus.SUBMITTED -> "Submitted"
            ApplicationStatus.IN_PROGRESS -> "In Progress"
            ApplicationStatus.CREATED -> "Created"
            ApplicationStatus.CANCELLED -> "Cancelled"
            ApplicationStatus.EXPIRED -> "Expired"
        }
    }
    
    fun proceedToDisbursal() {
        // In a real app, this would navigate to the loan disbursal flow
        // For now, we'll just simulate it with a loading state
        _state.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            // Simulate API call delay
            kotlinx.coroutines.delay(1500)
            _state.update { it.copy(isLoading = false) }
            
            // In reality, this would trigger navigation to another screen or flow
        }
    }
    
    fun downloadSummary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            try {
                // In a real app, this would generate and download a PDF
                // For now, we'll just simulate it
                kotlinx.coroutines.delay(1000)
                
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        error = "Failed to download summary: ${e.message}",
                        isLoading = false
                    ) 
                }
            }
        }
    }
    
    fun emailSummary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            try {
                // In a real app, this would send an email with the application summary
                // For now, we'll just simulate it
                kotlinx.coroutines.delay(1000)
                
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        error = "Failed to email summary: ${e.message}",
                        isLoading = false
                    ) 
                }
            }
        }
    }
    
    fun showAIAssistant() {
        viewModelScope.launch {
            val context = mapOf(
                "screen" to "application_complete",
                "status" to state.value.status,
                "isApproved" to state.value.isApproved,
                "isUnderReview" to state.value.isUnderReview
            )
            
            aiRepository.showAssistant(context)
        }
    }
}