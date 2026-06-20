package com.loansai.unassisted.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.MessageType
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the AI Assistant
 */
@HiltViewModel
class AIAssistantViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val userRepository: UserRepository,
    private val loanRepository: LoanRepository
) : ViewModel() {
    
    private val _messages = MutableStateFlow<List<AIAssistantMessage>>(emptyList())
    val messages: StateFlow<List<AIAssistantMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isDialogVisible = MutableStateFlow(false)
    val isDialogVisible: StateFlow<Boolean> = _isDialogVisible.asStateFlow()
    
    private val _unreadSuggestions = MutableStateFlow(0)
    val unreadSuggestions: StateFlow<Int> = _unreadSuggestions.asStateFlow()
    
    private var currentApplicationId: String? = null
    private var currentScreen: String? = null
    
    init {
        loadConversationHistory()
        countUnreadSuggestions()
    }
    
    /**
     * Load conversation history
     */
    private fun loadConversationHistory() {
        viewModelScope.launch {
            // Get current application ID
            userRepository.getCurrentUser().collectLatest { userResource ->
                if (userResource is Resource.Success) {
                    currentApplicationId = userResource.data.currentApplicationId
                    
                    // Load conversation history
                    aiRepository.getConversationHistory(currentApplicationId).collectLatest { result ->
                        if (result is Resource.Success) {
                            _messages.value = result.data.messages
                            countUnreadSuggestions()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Count unread suggestions
     */
    private fun countUnreadSuggestions() {
        val count = _messages.value.count { 
            it.messageType == MessageType.AI_SUGGESTION && !it.isRead 
        }
        _unreadSuggestions.value = count
    }
    
    /**
     * Send a message to the AI assistant
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            // Add user message to the list
            val userMessage = AIAssistantMessage(
                id = UUID.randomUUID().toString(),
                applicationId = currentApplicationId,
                messageType = MessageType.USER_MESSAGE,
                message = message,
                screen = currentScreen,
                isRead = true
            )
            
            // Save user message
            aiRepository.saveMessage(
                applicationId = currentApplicationId,
                message = message,
                messageType = MessageType.USER_MESSAGE,
                screen = currentScreen
            ).collectLatest { result ->
                if (result is Resource.Success) {
                    _messages.update { it + result.data }
                }
            }
            
            // Show loading state
            _isLoading.value = true
            
            // Get application context for AI
            val applicationContext = mutableMapOf<String, Any>()
            currentApplicationId?.let { appId ->
                try {
                    loanRepository.getApplication(appId).collectLatest { appResult ->
                        if (appResult is Resource.Success) {
                            applicationContext["application"] = appResult.data
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error fetching application for AI context", e)
                }
            }
            
            // Get AI response
            aiRepository.getAssistance(
                query = message,
                applicationId = currentApplicationId,
                screen = currentScreen,
                applicationContext = applicationContext
            ).collectLatest { result ->
                _isLoading.value = false
                
                when (result) {
                    is Resource.Success -> {
                        _messages.update { it + result.data }
                    }
                    is Resource.Error -> {
                        // Create a generic error message if not provided
                        val errorMsg = AIAssistantMessage(
                            id = UUID.randomUUID().toString(),
                            applicationId = currentApplicationId,
                            messageType = MessageType.ERROR_MESSAGE,
                            message = "Sorry, I'm having trouble right now. Please try again later.",
                            screen = currentScreen,
                            isRead = true
                        )
                        _messages.update { it + errorMsg }
                        
                        // Save error message
                        aiRepository.saveMessage(
                            applicationId = currentApplicationId,
                            message = errorMsg.message,
                            messageType = MessageType.ERROR_MESSAGE,
                            screen = currentScreen
                        )
                    }
                    is Resource.Loading -> {
                        // Already handling loading state
                    }
                }
            }
        }
    }
    
    /**
     * Get AI suggestions for current screen
     */
    fun getSuggestions(screen: String) {
        currentScreen = screen
        
        viewModelScope.launch {
            // Get application context for AI
            val applicationContext = mutableMapOf<String, Any>()
            currentApplicationId?.let { appId ->
                try {
                    loanRepository.getApplication(appId).collectLatest { appResult ->
                        if (appResult is Resource.Success) {
                            applicationContext["application"] = appResult.data
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error fetching application for AI context", e)
                }
            }
            
            // Get suggestions from AI
            aiRepository.getSuggestions(
                screen = screen,
                applicationId = currentApplicationId,
                applicationContext = applicationContext
            ).collectLatest { result ->
                if (result is Resource.Success) {
                    // Add suggestions that aren't already in the list
                    val newSuggestions = result.data.filter { suggestion ->
                        !_messages.value.any { it.id == suggestion.id }
                    }
                    
                    if (newSuggestions.isNotEmpty()) {
                        _messages.update { it + newSuggestions }
                        countUnreadSuggestions()
                    }
                }
            }
        }
    }
    
    /**
     * Mark all messages as read
     */
    fun markAllMessagesAsRead() {
        val unreadMessages = _messages.value.filter { !it.isRead }
        if (unreadMessages.isEmpty()) return
        
        val unreadIds = unreadMessages.map { it.id }
        
        viewModelScope.launch {
            aiRepository.markMessagesAsRead(unreadIds).collectLatest { result ->
                if (result is Resource.Success) {
                    // Update local state
                    _messages.update { messages ->
                        messages.map { message ->
                            if (unreadIds.contains(message.id)) {
                                message.copy(isRead = true)
                            } else {
                                message
                            }
                        }
                    }
                    _unreadSuggestions.value = 0
                }
            }
        }
    }
    
    /**
     * Show the AI assistant dialog
     */
    fun showDialog() {
        _isDialogVisible.value = true
    }
    
    /**
     * Hide the AI assistant dialog
     */
    fun hideDialog() {
        _isDialogVisible.value = false
    }
    
    /**
     * Clear conversation history
     */
    fun clearConversation() {
        viewModelScope.launch {
            aiRepository.clearConversationHistory(currentApplicationId).collectLatest { result ->
                if (result is Resource.Success) {
                    _messages.value = emptyList()
                    _unreadSuggestions.value = 0
                }
            }
        }
    }
}