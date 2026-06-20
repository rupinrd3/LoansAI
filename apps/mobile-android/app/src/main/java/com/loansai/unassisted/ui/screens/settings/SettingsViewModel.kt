package com.loansai.unassisted.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.BuildConfig
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.service.ImageResolution
import com.loansai.unassisted.service.ocr.OCRServiceSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val selectedOCRService: OCRService = OCRService.ML_KIT,
    val aiAssistantEnabled: Boolean = true,
    val appVersion: String = "",
    val buildNumber: String = "",
    val isDebugBuild: Boolean = false,
    val showClearDataDialog: Boolean = false,
    
    // New document upload settings
    val documentUploadEnabled: Boolean = true,
    val documentResolution: ImageResolution = ImageResolution.MEDIUM_2MP
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource,
    private val aiRepository: AIRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state
    
    init {
        loadSettings()
        loadAppInfo()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            // Load OCR service preference
            val ocrService = when (preferencesDataSource.getSelectedOCRService()) {
                OCRServiceSelector.OCR_SERVICE_DOCUMENT_AI -> OCRService.DOCUMENT_AI
                else -> OCRService.ML_KIT
            }
            
            // Load AI assistant preference
            val aiEnabled = preferencesDataSource.isAIAssistantEnabled()
            
            // Load document upload settings
            val docUploadEnabled = preferencesDataSource.getBoolean("pref_document_upload_enabled", true)
            val docResolutionStr = preferencesDataSource.getString("pref_document_resolution") ?: "MEDIUM_2MP"
            val docResolution = try {
                ImageResolution.valueOf(docResolutionStr)
            } catch (e: Exception) {
                ImageResolution.MEDIUM_2MP
            }
            
            _state.update {
                it.copy(
                    selectedOCRService = ocrService,
                    aiAssistantEnabled = aiEnabled,
                    documentUploadEnabled = docUploadEnabled,
                    documentResolution = docResolution
                )
            }
        }
    }
    
    private fun loadAppInfo() {
        _state.update {
            it.copy(
                appVersion = BuildConfig.VERSION_NAME,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                isDebugBuild = BuildConfig.DEBUG
            )
        }
    }
    
    fun selectOCRService(service: OCRService) {
        viewModelScope.launch {
            // Save the selected OCR service
            val ocrServiceValue = when (service) {
                OCRService.DOCUMENT_AI -> OCRServiceSelector.OCR_SERVICE_DOCUMENT_AI
                else -> OCRServiceSelector.OCR_SERVICE_ML_KIT
            }
            
            preferencesDataSource.saveSelectedOCRService(ocrServiceValue)
            
            _state.update { it.copy(selectedOCRService = service) }
        }
    }
    
    fun toggleAIAssistant(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.setAIAssistantEnabled(enabled)
            _state.update { it.copy(aiAssistantEnabled = enabled) }
        }
    }
    
    fun toggleDocumentUpload(enabled: Boolean) {
        viewModelScope.launch {
            preferencesDataSource.saveBoolean("pref_document_upload_enabled", enabled)
            _state.update { it.copy(documentUploadEnabled = enabled) }
        }
    }
    
    fun setDocumentResolution(resolution: ImageResolution) {
        viewModelScope.launch {
            preferencesDataSource.saveString("pref_document_resolution", resolution.name)
            _state.update { it.copy(documentResolution = resolution) }
        }
    }
    
    fun showAIAssistant() {
        viewModelScope.launch {
            val context = mapOf(
                "screen" to "settings",
                "ocrService" to state.value.selectedOCRService.name,
                "aiEnabled" to state.value.aiAssistantEnabled
            )
            
            aiRepository.showAssistant(context)
        }
    }
    
    fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://loansai.com/privacy-policy"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    fun openTermsAndConditions() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://loansai.com/terms-conditions"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
    
    fun confirmClearData() {
        _state.update { it.copy(showClearDataDialog = true) }
    }
    
    fun dismissClearDataDialog() {
        _state.update { it.copy(showClearDataDialog = false) }
    }
    
    fun clearAllData() {
        viewModelScope.launch {
            preferencesDataSource.clearAll()
            // Additional clear operations for other data stores would go here
            
            // Reload settings
            loadSettings()
        }
    }
}