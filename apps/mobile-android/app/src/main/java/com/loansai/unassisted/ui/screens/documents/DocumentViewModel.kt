package com.loansai.unassisted.ui.screens.documents

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentEvent
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.ExtractionStatus
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.domain.model.LlmInteractionEvent
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.model.OCRScanState
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.domain.repository.DocumentRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.service.ocr.OCRService
import com.loansai.unassisted.service.ocr.OCRServiceSelector
import com.loansai.unassisted.ui.navigation.Screen
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay

/**
 * ViewModel for the Document Upload screen
 * Updated for v1.5.0 with Backend LLM API integration
 */
@HiltViewModel
class DocumentViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val loanRepository: LoanRepository,
    private val aiRepository: AIRepository,
    private val ocrServiceSelector: OCRServiceSelector,
    private val metadataRepository: MetadataRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentScreenState())
    val state: StateFlow<DocumentScreenState> = _state.asStateFlow()
    
    // New states for document processing
    private val _processingState = MutableStateFlow<DocumentProcessingState>(DocumentProcessingState.Idle)
    val processingState: StateFlow<DocumentProcessingState> = _processingState.asStateFlow()
    
    // Document processing status map by document type (for status summary UI)
    private val _processingStatusMap = MutableStateFlow<Map<DocumentType, ProcessingStatusInfo>>(emptyMap())
    val processingStatusMap: StateFlow<Map<DocumentType, ProcessingStatusInfo>> = _processingStatusMap.asStateFlow()
    
    // Track which document type is being processed
    private var currentDocumentType: DocumentType? = null
    
    // Pending uploads to process when Continue button is clicked
    private val pendingUploads = mutableListOf<Document>()

    private val activeSectionTimings = mutableMapOf<String, String>() // Section name -> Section ID

    init {
        loadApplicationDocuments()
    }

    /**
     * Start the camera to capture a document
     */
    fun startCamera(documentType: DocumentType, cameraLauncher: ActivityResultLauncher<Uri>) {
        currentDocumentType = documentType
        
        try {
            // Create a temporary file for the camera output
            val context = _state.value.context ?: return
            val tempFile = createTempImageFile(context)
            
            // Get content URI for the file
            val uri = getUriForFile(context, tempFile)
            
            // Launch camera with the URI
            AppLogger.d("Starting camera with URI: $uri")
            cameraLauncher.launch(uri)
            
            // Update state to show we're waiting for camera
            _processingState.value = DocumentProcessingState.WaitingForCamera(uri)
            
            // Record document event metadata
            recordDocumentEvent(
                documentType = documentType,
                documentSourceType = "CAMERA_IMAGE",
                action = "CAPTURE_STARTED",
                status = "INITIATED"
            )
            
        } catch (e: Exception) {
            AppLogger.e("Error starting camera: ${e.message}", e)
            _processingState.value = DocumentProcessingState.Error("Failed to start camera: ${e.message}")
            
            // Record error in metadata
            recordDocumentEvent(
                documentType = documentType,
                documentSourceType = "CAMERA_IMAGE",
                action = "CAPTURE_STARTED",
                status = "ERROR",
                failureReason = e.message ?: "Unknown error starting camera"
            )
        }
    }

    /**
     * Open the gallery to select a document
     */
    fun openGallery(documentType: DocumentType, filePicker: ActivityResultLauncher<String>) {
        currentDocumentType = documentType
        
        try {
            // Launch file picker for images and PDFs
            filePicker.launch("*/*")
            
            // Update state to show we're waiting for file selection
            _processingState.value = DocumentProcessingState.WaitingForFileSelection
            
            // Record document event metadata
            recordDocumentEvent(
                documentType = documentType,
                documentSourceType = "IMAGE_UPLOAD",
                action = "SELECT_STARTED",
                status = "INITIATED"
            )
            
        } catch (e: Exception) {
            AppLogger.e("Error opening file picker: ${e.message}", e)
            _processingState.value = DocumentProcessingState.Error("Failed to open file picker: ${e.message}")
            
            // Record error in metadata
            recordDocumentEvent(
                documentType = documentType,
                documentSourceType = "IMAGE_UPLOAD",
                action = "SELECT_STARTED",
                status = "ERROR",
                failureReason = e.message ?: "Unknown error opening file picker"
            )
        }
    }


    /**
     * Process the selected image or document
     */
    fun processSelectedFile(uri: Uri?) {
        if (uri == null) {
            _processingState.value = DocumentProcessingState.Error("No file selected")
            recordDocumentEvent(
                documentType = currentDocumentType ?: DocumentType.OTHER,
                documentSourceType = "IMAGE_UPLOAD", // Or determine source type
                action = "FILE_SELECTED",
                status = "ERROR",
                failureReason = "No file selected"
            )
            return
        }

        val documentType = currentDocumentType
        if (documentType == null) {
            _processingState.value = DocumentProcessingState.Error("Document type not specified")
            recordDocumentEvent(
                documentType = DocumentType.OTHER,
                documentSourceType = "UNKNOWN", // Or determine source type
                action = "FILE_SELECTED",
                status = "ERROR",
                failureReason = "Document type not specified"
            )
            return
        }

        viewModelScope.launch {
            try {
                // Indicate processing starts
                _processingState.value = DocumentProcessingState.Processing
                updateProcessingStatus(documentType, ProcessingStatus.PROCESSING)

                val context = _state.value.context ?: run {
                    _processingState.value = DocumentProcessingState.Error("Internal error: Context not available")
                    recordDocumentEvent(documentType, "UNKNOWN", "PROCESSING", "ERROR", "Context not available")
                    return@launch
                }

                // Get file information
                val fileName = getFileName(context, uri)
                val fileType = getFileType(fileName)
                val fileSize = getFileSize(uri) ?: 0L

                // Record file selected in metadata
                recordDocumentEvent(
                    documentType = documentType,
                    documentSourceType = determineDocumentSourceType(fileType, uri).name,
                    action = "FILE_SELECTED",
                    status = "SUCCESS",
                    fileMetadata = mapOf(
                        "fileName" to fileName,
                        "fileSize" to fileSize
                    )
                )

                // --- CHANGE: Directly create document and proceed to upload for ALL types ---
                // Gemini service will handle the content extraction later
                val document = createTempDocument(
                    documentType,
                    uri,
                    fileName,
                    fileType,
                    null // NO pre-extracted text needed now
                )

                // Proceed to upload the document metadata and file
                // The upload process will trigger the LLM processing
                uploadDocument(document)
                // --- END CHANGE ---

            } catch (e: Exception) {
                AppLogger.e("Error processing file: ${e.message}", e)
                _processingState.value = DocumentProcessingState.Error("Error processing file: ${e.message}")
                updateProcessingStatus(documentType, ProcessingStatus.ERROR)
// Get the context, with null check
                val contextToUse = _state.value.context
                if (contextToUse != null) {
                    // Now contextToUse is non-nullable Context, not Context?
                    recordDocumentEvent(
                        documentType = documentType,
                        documentSourceType = determineDocumentSourceType(getFileType(getFileName(contextToUse, uri)), uri).name,
                        action = "PROCESSING",
                        status = "ERROR",
                        failureReason = e.message ?: "Unknown error processing file"
                    )
                } else {
                    // Handle the case when context is null
                    recordDocumentEvent(
                        documentType = documentType,
                        documentSourceType = "UNKNOWN", // Use a default when context is null
                        action = "PROCESSING",
                        status = "ERROR",
                        failureReason = e.message ?: "Unknown error processing file"
                    )
                }
            }
        }
    }

    /**
     * Remove a document
     */
    fun removeDocument(documentType: DocumentType) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // Get the document ID based on type
                val documentToRemove = when (documentType) {
                    DocumentType.BANK_STATEMENT -> _state.value.bankStatement
                    DocumentType.SALARY_SLIP -> _state.value.salarySlip
                    DocumentType.INCOME_TAX_RETURN -> _state.value.incomeTaxReturn
                    DocumentType.FORM_26AS -> _state.value.form26AS
                    else -> null
                }

                if (documentToRemove != null) {
                    documentRepository.deleteDocument(documentToRemove.id).collect { result ->
                        if (result is Resource.Success) {
                            // Update the state to remove the document
                            _state.update {
                                when (documentType) {
                                    DocumentType.BANK_STATEMENT -> it.copy(bankStatement = null)
                                    DocumentType.SALARY_SLIP -> it.copy(salarySlip = null)
                                    DocumentType.INCOME_TAX_RETURN -> it.copy(incomeTaxReturn = null)
                                    DocumentType.FORM_26AS -> it.copy(form26AS = null)
                                    else -> it
                                }
                            }
                            
                            // Clear the processing status for this document type
                            updateProcessingStatus(documentType, null)
                            
                            AppLogger.d("Document removed: $documentType")
                            
                            // Record document deletion in metadata
                            recordDocumentEvent(
                                documentType = documentType,
                                documentSourceType = documentToRemove.documentSourceType.toString(),
                                action = "DOCUMENT_DELETED",
                                status = "SUCCESS"
                            )
                        } else if (result is Resource.Error) {
                            AppLogger.e("Error removing document: ${result.message}")
                            
                            // Record deletion error in metadata
                            recordDocumentEvent(
                                documentType = documentType,
                                documentSourceType = documentToRemove.documentSourceType.toString(),
                                action = "DOCUMENT_DELETED",
                                status = "ERROR",
                                failureReason = result.message
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Exception removing document: ${e.message}", e)
                
                // Record error in metadata
                recordDocumentEvent(
                    documentType = documentType,
                    documentSourceType = "UNKNOWN",
                    action = "DOCUMENT_DELETED",
                    status = "ERROR",
                    failureReason = e.message ?: "Unknown error deleting document"
                )
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Upload a document after it has been processed
     */
    fun uploadDocument(document: Document) {
        viewModelScope.launch {
            try {
                // Get current application ID
                val currentApp = loanRepository.getCurrentApplication()
                val applicationId = currentApp?.id ?: return@launch
                
                // Set uploading state
                _processingState.value = DocumentProcessingState.Uploading
                
                // Update processing status map for UI
                updateProcessingStatus(document.documentType, ProcessingStatus.PROCESSING)
                
                // Record upload started in metadata
                recordDocumentEvent(
                    documentType = document.documentType,
                    documentSourceType = document.documentSourceType.name,
                    action = "UPLOAD_STARTED",
                    status = "INITIATED",
                    fileMetadata = mapOf(
                        "fileName" to document.fileName,
                        "fileSize" to document.fileSize
                    )
                )
                
                // Use the correct application ID, not the one in the document
                documentRepository.uploadDocument(
                    applicationId = applicationId,
                    documentType = document.documentType,
                    fileType = document.fileType,
                    fileUri = Uri.parse(document.localUri ?: ""),
                    fileName = document.fileName
                ).collect { result ->
                    when (result) {
                        is Resource.Success -> {
                            // Update document in UI state
                            updateDocumentInState(result.data)
                            
                            // Update processing state to success
                            _processingState.value = DocumentProcessingState.Success(result.data)
                            
                            // Record upload success in metadata
                            recordDocumentEvent(
                                documentType = document.documentType,
                                documentSourceType = document.documentSourceType.name,
                                action = "UPLOAD_COMPLETED",
                                status = "SUCCESS",
                                extractionStatus = result.data.extractionStatus.name
                            )
                            
                            // *** NEW for v1.5.0: Trigger the backend LLM API for processing ***
                            processDocumentWithLLM(result.data.id)
                        }
                        is Resource.Error -> {
                            _processingState.value = DocumentProcessingState.Error(result.message ?: "Upload failed")
                            
                            // Update processing status map for UI
                            updateProcessingStatus(document.documentType, ProcessingStatus.ERROR)
                            
                            // Record upload error in metadata
                            recordDocumentEvent(
                                documentType = document.documentType,
                                documentSourceType = document.documentSourceType.name,
                                action = "UPLOAD_COMPLETED",
                                status = "ERROR",
                                failureReason = result.message ?: "Unknown upload error"
                            )
                        }
                        is Resource.Loading -> {
                            _processingState.value = DocumentProcessingState.Uploading
                        }
                    }
                }
            } catch (e: Exception) {
                _processingState.value = DocumentProcessingState.Error("Upload failed: ${e.message}")
                
                // Update processing status map for UI
                updateProcessingStatus(document.documentType, ProcessingStatus.ERROR)
                
                // Record error in metadata
                recordDocumentEvent(
                    documentType = document.documentType,
                    documentSourceType = document.documentSourceType.name,
                    action = "UPLOAD_COMPLETED",
                    status = "ERROR",
                    failureReason = e.message ?: "Unknown error during upload"
                )
            }
        }
    }
    
    /**
     * Process document with LLM through the backend API
     * New method for v1.5.0 to trigger backend LLM processing
     */
    private fun processDocumentWithLLM(documentId: String) {
        viewModelScope.launch {
            try {
                // Get the document from the state
                val documentToProcess = getDocumentById(documentId) ?: return@launch
                
                // Update processing status map for UI
                updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.PROCESSING)
                
                // Call the repository method to process the document
                documentRepository.processDocument(documentId).collect { result ->
                    when (result) {
                        is Resource.Success -> {
                            // Document is in processing state on the backend
                            // We will check its status periodically
                            startBackendProcessingStatusCheck(documentId)
                        }
                        is Resource.Error -> {
                            AppLogger.e("Error processing document with LLM: ${result.message}")
                            
                            // Update processing status map for UI
                            updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.ERROR)
                            
                            // Record error in metadata
                            recordDocumentEvent(
                                documentType = documentToProcess.documentType,
                                documentSourceType = documentToProcess.documentSourceType.name,
                                action = "LLM_PROCESSING",
                                status = "ERROR",
                                failureReason = result.message ?: "Unknown error during LLM processing",
                                extractionStatus = "FAILURE"
                            )
                        }
                        is Resource.Loading -> {
                            // Update processing status map for UI
                            updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.PROCESSING)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error triggering document processing: ${e.message}", e)
                
                // Get the document from local state since we don't have the result from the repository
                val documentToProcess = getDocumentById(documentId)
                if (documentToProcess != null) {
                    // Update processing status map for UI
                    updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.ERROR)
                    
                    // Record error in metadata
                    recordDocumentEvent(
                        documentType = documentToProcess.documentType,
                        documentSourceType = documentToProcess.documentSourceType.name,
                        action = "LLM_PROCESSING",
                        status = "ERROR",
                        failureReason = e.message ?: "Unknown error during LLM processing",
                        extractionStatus = "FAILURE"
                    )
                }
            }
        }
    }
    
    /**
     * Start checking the backend processing status periodically
     * New method for v1.5.0 to handle asynchronous backend processing
     */
    private fun startBackendProcessingStatusCheck(documentId: String) {
        viewModelScope.launch {
            try {
                val documentToProcess = getDocumentById(documentId) ?: return@launch
                var checkCount = 0
                val maxChecks = 10 // Limit the number of checks to avoid infinite polling
                
                while (checkCount < maxChecks) {
                    // Wait before checking again
                    delay(3000) // 3 seconds between checks
                    checkCount++
                    
                    // Check the processing status
                    documentRepository.checkDocumentProcessingStatus(documentId).collect { result ->
                        when (result) {
                            is Resource.Success -> {
                                val updatedDocument = result.data
                                
                                // Update document in state
                                updateDocumentInState(updatedDocument)
                                
                                // Update processing status map based on extraction status
                                when (updatedDocument.extractionStatus) {
                                    ExtractionStatus.SUCCESS -> {
                                        updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.SUCCESS)
                                        // Stop checking once we get a success
                                        checkCount = maxChecks
                                    }
                                    ExtractionStatus.FAILURE -> {
                                        updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.ERROR)
                                        // Stop checking on failure
                                        checkCount = maxChecks
                                    }
                                    ExtractionStatus.PENDING -> {
                                        updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.PROCESSING)
                                        // Continue checking if still pending
                                    }
                                    else -> {
                                        // For NOT_ATTEMPTED or other states, keep the current status
                                    }
                                }
                            }
                            is Resource.Error -> {
                                AppLogger.e("Error checking document processing status: ${result.message}")
                                // Continue checking despite errors
                            }
                            is Resource.Loading -> {
                                // No action needed
                            }
                        }
                    }
                }
                
                // If we've reached the maximum number of checks and still processing,
                // update the status to indicate it's taking longer than expected
                val finalDocumentState = getDocumentById(documentId)
                if (finalDocumentState?.extractionStatus == ExtractionStatus.PENDING) {
                    // Update UI to show still processing but don't block the user
                    updateProcessingStatus(documentToProcess.documentType, ProcessingStatus.ONGOING)
                    
                    // Record the status in metadata
                    recordDocumentEvent(
                        documentType = documentToProcess.documentType,
                        documentSourceType = documentToProcess.documentSourceType.name,
                        action = "LLM_PROCESSING_TIMEOUT",
                        status = "ONGOING",
                        extractionStatus = "PENDING"
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("Error in backend processing status check: ${e.message}", e)
            }
        }
    }

    /**
     * Helper method to get a document by ID from the current state
     */
    private fun getDocumentById(documentId: String): Document? {
        val state = _state.value
        return when {
            state.bankStatement?.id == documentId -> state.bankStatement
            state.salarySlip?.id == documentId -> state.salarySlip
            state.incomeTaxReturn?.id == documentId -> state.incomeTaxReturn
            state.form26AS?.id == documentId -> state.form26AS
            else -> null
        }
    }

    /**
     * Update the processing status map for a document type
     */
    private fun updateProcessingStatus(documentType: DocumentType, status: ProcessingStatus?) {
        val currentMap = _processingStatusMap.value.toMutableMap()
        
        if (status == null) {
            // Remove the status if null (e.g., document deleted)
            currentMap.remove(documentType)
        } else {
            // Update or add the status
            currentMap[documentType] = ProcessingStatusInfo(
                status = status,
                timestamp = LocalDateTime.now()
            )
        }
        
        _processingStatusMap.value = currentMap
    }

    /**
     * View a document
     */
    fun viewDocument(document: Document) {
        // Implementation will depend on document viewing capabilities
        AppLogger.d("Viewing document: ${document.id}")
        // This might navigate to a document viewer screen or open a viewer intent
        
        // Record document view in metadata
        recordDocumentEvent(
            documentType = document.documentType,
            documentSourceType = document.documentSourceType.name,
            action = "DOCUMENT_VIEWED",
            status = "SUCCESS"
        )
    }

    /**
     * Show AI Assistant
     */
    fun showAIAssistant() {
        viewModelScope.launch {
            try {
                // Get contextual assistance for document upload screen
                aiRepository.getAssistance(
                    query = "How to upload documents properly?",
                    screen = "document_upload",
                    applicationContext = mapOf(
                        "hasDocuments" to _state.value.hasAnyDocument
                    )
                ).collect { /* No action needed with the result */ }
            } catch (e: Exception) {
                AppLogger.e("Error showing AI assistant: ${e.message}", e)
            }
        }
    }
    
    /**
     * Set the context for file operations
     */
    fun setContext(context: Context) {
        _state.update { it.copy(context = context) }
    }
    
    /**
     * Process any pending uploads when user continues
     */
    fun processPendingUploads() {
        // This would be where we handle background uploads of documents
        // We could also queue Work Manager tasks here
    }


    /**
     * Load documents for the current application
     */
    private fun loadApplicationDocuments() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }

            try {
                // Get current application
                val currentApp = loanRepository.getCurrentApplication()
                if (currentApp == null) {
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }

                val applicationId = currentApp.id

                // Get documents for this application
                documentRepository.getApplicationDocuments(applicationId).collect { docResult ->
                    if (docResult is Resource.Success) {
                        // Sort documents by type
                        var bankStatement: Document? = null
                        var salarySlip: Document? = null
                        var incomeTaxReturn: Document? = null
                        var form26AS: Document? = null

                        for (document in docResult.data) {
                            when (document.documentType) {
                                DocumentType.BANK_STATEMENT -> bankStatement = document
                                DocumentType.SALARY_SLIP -> salarySlip = document
                                DocumentType.INCOME_TAX_RETURN -> incomeTaxReturn = document
                                DocumentType.FORM_26AS -> form26AS = document
                                else -> { /* Ignore other document types */ }
                            }
                            
                            // Initialize the processing status map based on document status
                            val status = when (document.extractionStatus) {
                                ExtractionStatus.SUCCESS -> ProcessingStatus.SUCCESS
                                ExtractionStatus.FAILURE -> ProcessingStatus.ERROR
                                ExtractionStatus.PENDING -> ProcessingStatus.PROCESSING
                                ExtractionStatus.NOT_ATTEMPTED -> null // No status shown for not attempted
                            }
                            
                            if (status != null) {
                                updateProcessingStatus(document.documentType, status)
                            }
                        }

                        // Update state with documents
                        _state.update {
                            it.copy(
                                bankStatement = bankStatement,
                                salarySlip = salarySlip,
                                incomeTaxReturn = incomeTaxReturn,
                                form26AS = form26AS,
                                isLoading = false
                            )
                        }
                    } else {
                        _state.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error loading application documents: ${e.message}", e)
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Update a document in the state
     */
    private fun updateDocumentInState(document: Document) {
        _state.update {
            when (document.documentType) {
                DocumentType.BANK_STATEMENT -> it.copy(bankStatement = document)
                DocumentType.SALARY_SLIP -> it.copy(salarySlip = document)
                DocumentType.INCOME_TAX_RETURN -> it.copy(incomeTaxReturn = document)
                DocumentType.FORM_26AS -> it.copy(form26AS = document)
                else -> it
            }
        }
    }
    
    /**
     * Record a document event in metadata
     */
    private fun recordDocumentEvent(
        documentType: DocumentType,
        documentSourceType: String,
        action: String,
        status: String? = null,
        failureReason: String? = null,
        extractionStatus: String? = null,
        fileMetadata: Map<String, Any>? = null
    ) {
        viewModelScope.launch {
            try {
                // Get current application ID
                val currentApp = loanRepository.getCurrentApplication()
                val applicationId = currentApp?.id ?: return@launch
                
                // Create document event
                val documentEvent = DocumentEvent(
                    documentType = documentType.toString(),
                    documentSourceType = documentSourceType,
                    action = action,
                    timestamp = LocalDateTime.now(),
                    status = status,
                    failureReason = failureReason,
                    extractionStatus = extractionStatus,
                    fileMetadata = fileMetadata
                )
                
                // Record event in metadata
                metadataRepository.recordDocumentEvent(applicationId, documentEvent).collect()
                
                // Send event to orchestrator
                metadataRepository.sendEventToOrchestrator(
                    applicationId = applicationId,
                    eventType = MetadataEventType.DOCUMENT_EVENT,
                    metadata = mapOf(
                        "documentEvent" to documentEvent
                    )
                ).collect()
                
            } catch (e: Exception) {
                AppLogger.e("Error recording document event metadata: ${e.message}", e)
            }
        }
    }
    
    /**
     * Record an LLM interaction event in metadata
     */
    private fun recordLlmInteractionEvent(
        callType: String,
        status: String,
        modelUsed: String? = null,
        failureReason: String? = null
    ) {
        viewModelScope.launch {
            try {
                // Get current application ID
                val currentApp = loanRepository.getCurrentApplication()
                val applicationId = currentApp?.id ?: return@launch
                
                // Create LLM interaction event
                val llmEvent = LlmInteractionEvent(
                    llmCallType = callType,
                    status = status,
                    modelUsed = modelUsed,
                    failureReason = failureReason,
                    timestamp = LocalDateTime.now()
                )
                
                // Record LLM interaction in metadata
                metadataRepository.recordLlmInteractionEvent(applicationId, llmEvent).collect()
                
                // Send event to orchestrator
                val eventType = if (status == "INITIATED") {
                    MetadataEventType.LLM_CALL_INITIATED
                } else {
                    MetadataEventType.LLM_CALL_COMPLETED
                }
                
                metadataRepository.sendEventToOrchestrator(
                    applicationId = applicationId,
                    eventType = eventType,
                    metadata = mapOf(
                        "llmInteraction" to mapOf(
                            "llmCallType" to callType,
                            "status" to status,
                            "modelUsed" to (modelUsed ?: ""),
                            "failureReason" to (failureReason ?: ""),
                            "timestamp" to LocalDateTime.now().toString()
                        )
                    )
                ).collect()
                
            } catch (e: Exception) {
                AppLogger.e("Error recording LLM interaction event: ${e.message}", e)
            }
        }
    }
    
    // Helper functions for file operations
    
    private fun createTempImageFile(context: Context): File {
        val timeStamp = LocalDateTime.now().toString().replace(":", "_")
        val fileName = "DOC_${timeStamp}.jpg"
        return File(context.cacheDir, fileName)
    }
    
    private fun getUriForFile(context: Context, file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "com.loansai.unassisted.fileprovider",
            file
        )
    }
    
    private fun getFileName(context: Context, uri: Uri): String {
        // Try to get the actual file name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex)
                }
            }
        }
        
        // Fallback to a generated name
        val timeStamp = LocalDateTime.now().toString().replace(":", "_")
        return "DOC_${timeStamp}"
    }
    
    private fun getFileType(fileName: String): FileType {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> FileType.PDF
            fileName.endsWith(".jpg", ignoreCase = true) -> FileType.JPG
            fileName.endsWith(".jpeg", ignoreCase = true) -> FileType.JPG
            fileName.endsWith(".png", ignoreCase = true) -> FileType.PNG
            else -> FileType.OTHER
        }
    }
    
    private suspend fun createTempDocument(
        documentType: DocumentType,
        uri: Uri,
        fileName: String,
        fileType: FileType,
        extractedText: String? = null
    ): Document {
        // Get the current application ID first
        val currentApplication = loanRepository.getCurrentApplication()
        val applicationId = currentApplication?.id ?: "temp"       
        // Create a temporary document before it's properly uploaded
        return Document(            
            id = "temp_" + System.currentTimeMillis().toString(),
            applicationId = applicationId,  // Will be replaced during actual upload
            documentType = documentType,
            fileType = fileType,
            fileName = fileName,
            fileSize = getFileSize(uri) ?: 0L,
            uploadedAt = LocalDateTime.now(),
            documentStatus = com.loansai.unassisted.domain.model.DocumentStatus.UPLOADED,
            documentSourceType = determineDocumentSourceType(fileType, uri),
            storageUrl = null,
            localUri = uri.toString(),
            processingResult = if (extractedText != null) {
                com.loansai.unassisted.domain.model.DocumentProcessingResult(
                    isProcessed = true,
                    processedAt = LocalDateTime.now(),
                    processingMethod = com.loansai.unassisted.domain.model.ProcessingMethod.ML_KIT_OCR,
                    extractedFields = mapOf("FULL_TEXT" to extractedText),
                    ocrConfidence = 0.9f,
                    extractionErrors = emptyList()
                )
            } else null,
            extractionStatus = ExtractionStatus.NOT_ATTEMPTED
        )
    }
    
    /**
     * Determine the document source type based on file type and URI
     */
    private fun determineDocumentSourceType(fileType: FileType, uri: Uri): com.loansai.unassisted.domain.model.DocumentSourceType {
        return when {
            uri.toString().startsWith("content://android.media.action.IMAGE_CAPTURE") -> 
                com.loansai.unassisted.domain.model.DocumentSourceType.CAMERA_IMAGE
            fileType == FileType.PDF -> 
                com.loansai.unassisted.domain.model.DocumentSourceType.PDF_UPLOAD
            fileType == FileType.JPG || fileType == FileType.PNG -> 
                com.loansai.unassisted.domain.model.DocumentSourceType.IMAGE_UPLOAD
            else -> 
                com.loansai.unassisted.domain.model.DocumentSourceType.MANUAL_ENTRY
        }
    }
    
    private fun getFileSize(uri: Uri): Long? {
        val context = _state.value.context ?: return null
        try {
            val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            val size = descriptor?.length
            descriptor?.close()
            return size
        } catch (e: Exception) {
            AppLogger.e("Error getting file size: ${e.message}", e)
            return null
        }
    }

    /**
     * Get the current application ID from the document state
     */
    fun getCurrentApplicationId(): String? {
        // Get application ID from the current document state
        return _state.value.bankStatement?.applicationId
            ?: _state.value.salarySlip?.applicationId
            ?: _state.value.incomeTaxReturn?.applicationId
            ?: _state.value.form26AS?.applicationId
    }

    /**
     * Record when a screen is visited for metadata tracking
     */
    fun recordScreenVisit(screenName: String) {
        viewModelScope.launch {
            try {
                val currentApp = loanRepository.getCurrentApplication()
                val applicationId = currentApp?.id ?: return@launch
                
                metadataRepository.recordScreenVisit(applicationId, screenName).collect()
                
                // Send to orchestrator
                metadataRepository.sendEventToOrchestrator(
                    applicationId = applicationId,
                    eventType = MetadataEventType.SCREEN_VISIT,
                    metadata = mapOf(
                        "screenName" to screenName,
                        "startTime" to LocalDateTime.now().toString()
                    )
                ).collect()
            } catch (e: Exception) {
                AppLogger.e("Error recording screen visit: ${e.message}", e)
            }
        }
    }

    /**
     * Record when leaving a screen for metadata tracking
     */
    fun endScreenVisit(screenName: String) {
        viewModelScope.launch {
            try {
                val currentApp = loanRepository.getCurrentApplication()
                val applicationId = currentApp?.id ?: return@launch
                
                metadataRepository.endScreenVisit(applicationId, screenName).collect()
            } catch (e: Exception) {
                AppLogger.e("Error ending screen visit: ${e.message}", e)
            }
        }
    }

    /**
     * Start timing for a section
     */
    fun startSectionTiming(screenName: String, sectionName: String) {
        viewModelScope.launch {
            try {
                val currentApp = loanRepository.getCurrentApplication()
                val applicationId = currentApp?.id ?: return@launch
                
                // Start section timing
                metadataRepository.startSectionTiming(
                    applicationId = applicationId, 
                    screenName = screenName,
                    sectionName = sectionName
                ).collect { result ->
                    if (result is Resource.Success) {
                        // Store section ID for later completion
                        val sectionKey = "$sectionName"
                        activeSectionTimings[sectionKey] = sectionName
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error starting section timing: ${e.message}", e)
            }
        }
    }

    /**
     * Complete all active section timings
     */
    fun completeSectionTimings() {
        viewModelScope.launch {
            try {
                val currentApp = loanRepository.getCurrentApplication()
                val applicationId = currentApp?.id ?: return@launch
                
                // Complete all active section timings
                for ((sectionKey, sectionName) in activeSectionTimings) {
                    metadataRepository.completeSectionTiming(
                        applicationId = applicationId,
                        screenName = Screen.DocumentUpload.route,
                        sectionName = sectionName
                    ).collect()
                }
                
                // Clear active timings
                activeSectionTimings.clear()
            } catch (e: Exception) {
                AppLogger.e("Error completing section timings: ${e.message}", e)
            }
        }
    }

    /**
     * Update application progress when user continues
     */
    fun saveProgress() {
        viewModelScope.launch {
            try {
                // Get current application
                val currentApp = loanRepository.getCurrentApplication() ?: return@launch
                
                // Check if documents have been uploaded
                val hasAnyDocument = _state.value.let { state ->
                    state.bankStatement != null || 
                    state.salarySlip != null || 
                    state.incomeTaxReturn != null || 
                    state.form26AS != null
                }
                
                if (hasAnyDocument) {
                    // Create list of completed steps
                    val completedSteps = currentApp.completedSteps.toMutableList()
                    
                    // Add DOCUMENT_UPLOAD to completed steps if not already there
                    if (!completedSteps.contains(ApplicationStep.DOCUMENT_UPLOAD)) {
                        completedSteps.add(ApplicationStep.DOCUMENT_UPLOAD)
                    }
                    
                    // Set next step based on bureau score (v1.5.0 update)
                    // In a real implementation, we'd check the bureau score from the repository
                    // For now, we're using a hardcoded approach to demonstrate the flow
                    val hasBureauScore = true  // In real app, check bureau report
                    val bureauScore = 750      // Example score, should come from repository
                    
                    val nextStep = if (hasBureauScore && bureauScore in 1..1000) {
                        ApplicationStep.BUREAU_CONFIRMATION
                    } else {
                        ApplicationStep.LOAN_OFFER
                    }
                    
                    // Update application progress
                    loanRepository.saveApplicationProgressWithFlow(
                        applicationId = currentApp.id,
                        completedSteps = completedSteps,
                        currentStep = nextStep
                    ).collect()
                    
                    // Record section completion in metadata
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = currentApp.id,
                        eventType = MetadataEventType.SECTION_COMPLETED,
                        metadata = mapOf(
                            "sectionName" to "DOCUMENT_UPLOAD",
                            "completedAt" to LocalDateTime.now().toString()
                        )
                    ).collect()
                }
            } catch (e: Exception) {
                AppLogger.e("Error saving progress: ${e.message}", e)
            }
        }
    }
}

/**
 * State holder for the Document Upload screen
 */
data class DocumentScreenState(
    val bankStatement: Document? = null,
    val salarySlip: Document? = null,
    val incomeTaxReturn: Document? = null,
    val form26AS: Document? = null,
    val isLoading: Boolean = false,
    val context: Context? = null  // Added context for file operations
) {
    val hasAnyDocument: Boolean
        get() = bankStatement != null || salarySlip != null || 
                incomeTaxReturn != null || form26AS != null
}

/**
 * States for document processing
 */
sealed class DocumentProcessingState {
    object Idle : DocumentProcessingState()
    data class WaitingForCamera(val outputUri: Uri) : DocumentProcessingState()
    object WaitingForFileSelection : DocumentProcessingState()
    object Processing : DocumentProcessingState()
    object Uploading : DocumentProcessingState()
    data class Success(val document: Document) : DocumentProcessingState()
    data class Error(val message: String) : DocumentProcessingState()
}

/**
 * Processing status enum for UI display
 */
enum class ProcessingStatus {
    PROCESSING,  // Show spinning indicator
    SUCCESS,     // Show green checkmark
    ERROR,       // Show red error icon
    ONGOING      // Show spinning indicator with "taking longer than expected" message
}

/**
 * Processing status info for tracking in the UI
 */
data class ProcessingStatusInfo(
    val status: ProcessingStatus,
    val timestamp: LocalDateTime
)