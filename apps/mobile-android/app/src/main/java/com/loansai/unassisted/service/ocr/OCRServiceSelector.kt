package com.loansai.unassisted.service.ocr

import android.content.Context
import android.net.Uri
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.OCRScanState
import com.loansai.unassisted.service.ocr.impl.MLKitOCRService
import com.loansai.unassisted.service.DocumentProcessingService
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strategy pattern implementation for selecting the appropriate OCR service
 * based on user settings and document type
 */
@Singleton
class OCRServiceSelector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesDataSource: PreferencesDataSource
) {
    
    companion object {
        const val OCR_SERVICE_ML_KIT = "ml_kit"
        const val OCR_SERVICE_DOCUMENT_AI = "document_ai"
        
        // For singleton access in repositories that don't have DI
        private var instance: OCRServiceSelector? = null
        
        fun getInstanceWithContext(context: Context): OCRServiceSelector {
            if (instance == null) {
                instance = OCRServiceSelector(
                    context,
                    // Create a temporary PreferencesDataSource since we can't inject it here
                    PreferencesDataSource(context)
                )
            }
            return instance!!
        }
    }
    
    // Lazy initialization of services
    private val mlKitOCRService by lazy {
        MLKitOCRService(
            context,
            DocumentProcessingService(context)
        )
    }
    
    // Document AI service would be initialized here in a similar way
    
    /**
     * Gets the appropriate OCR service based on user settings and document type
     */
    fun getOCRService(documentType: DocumentType): OCRService {
        val selectedService = runBlocking { 
            preferencesDataSource.getString("pref_selected_ocr_service") ?: OCR_SERVICE_ML_KIT
        }
        
        return when (selectedService) {
            OCR_SERVICE_DOCUMENT_AI -> {
                // If Document AI service is selected but not available/implemented,
                // fall back to ML Kit
                // In a real app, we would initialize and return Document AI service
                // Especially good for complex documents like bank statements
                if (documentType == DocumentType.BANK_STATEMENT || 
                    documentType == DocumentType.SALARY_SLIP) {
                    // Would return Document AI service
                    mlKitOCRService
                } else {
                    mlKitOCRService
                }
            }
            else -> {
                // Default to ML Kit OCR Service
                mlKitOCRService
            }
        }
    }
    
    /**
     * Processes a document with the appropriate OCR service
     */
    suspend fun processDocument(uri: Uri, documentType: DocumentType): Flow<OCRScanState> {
        val service = getOCRService(documentType)
        return service.recognizeText(uri)
    }
}