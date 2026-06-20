package com.loansai.unassisted.service.ocr

import android.net.Uri
import com.loansai.unassisted.domain.model.DocumentProcessingResult
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.OCRScanState
import kotlinx.coroutines.flow.Flow

/**
 * Service interface for OCR (Optical Character Recognition)
 */
interface OCRService {
    
    /**
     * Recognize text in an image
     * 
     * @param imageUri The URI of the image file
     * @return Flow of OCRScanState indicating the state of the recognition process
     */
    fun recognizeText(imageUri: Uri): Flow<OCRScanState>
    
    /**
     * Extract specific fields from a document
     * 
     * @param imageUri The URI of the document image
     * @param documentType The type of document being processed
     * @return Flow of key-value pairs containing extracted fields
     */
    fun extractDocumentFields(
        imageUri: Uri,
        documentType: DocumentType
    ): Flow<Map<String, String>>
    
    /**
     * Process a document image and extract all available information
     * 
     * @param imageUri The URI of the document image
     * @param documentType The type of document being processed
     * @return Flow containing the processing result
     */
    fun processDocument(
        imageUri: Uri,
        documentType: DocumentType
    ): Flow<DocumentProcessingResult>
    
    /**
     * Get the service type
     */
    fun getServiceType(): OCRServiceType
}

/**
 * Enum for OCR service types
 */
enum class OCRServiceType {
    ML_KIT,
    CLOUD_VISION,
    DOCUMENT_AI
}