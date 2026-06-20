package com.loansai.unassisted.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.data.model.BankStatementDataDto
import com.loansai.unassisted.data.model.DocumentDto
import com.loansai.unassisted.data.model.DocumentProcessingRequest
import com.loansai.unassisted.data.model.ProcessDocumentRequest
import com.loansai.unassisted.data.model.UploadDocumentRequest
import com.loansai.unassisted.data.model.toBankStatementData
import com.loansai.unassisted.data.model.toDocument
import com.loansai.unassisted.data.remote.api.DocumentApi
import com.loansai.unassisted.data.remote.api.LlmProcessingApi
import com.loansai.unassisted.domain.model.BankStatementData
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentStatus
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.ExtractionStatus
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.domain.model.LlmInteractionEvent
import com.loansai.unassisted.domain.model.ProcessingMethod
import com.loansai.unassisted.domain.repository.DocumentRepository
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.service.DocumentProcessingService
import com.loansai.unassisted.service.DocumentUploadWorker
import com.loansai.unassisted.service.ImageResolution
import com.loansai.unassisted.util.FileUtils
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.loansai.unassisted.service.ai.impl.GeminiDocumentService
import com.loansai.unassisted.domain.model.DocumentEvent
import com.loansai.unassisted.domain.model.DocumentProcessingResult
import com.loansai.unassisted.util.DateConverter











/**
 * Implementation of DocumentRepository
 * Updated for v1.5.0 with Backend LLM API integration
 */
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentApi: DocumentApi,
    private val llmProcessingApi: LlmProcessingApi, // Inject the new LLM Processing API
    private val preferencesDataSource: PreferencesDataSource,
    private val documentProcessingService: DocumentProcessingService,
    private val metadataRepository: MetadataRepository,
    private val geminiDocumentService: GeminiDocumentService
) : DocumentRepository {

    private val gson = Gson()
    private val documentsCache = mutableMapOf<String, Document>()
    private val cacheFile = File(context.cacheDir, "documents_cache.json")
    
    // Firebase instances
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val workManager = WorkManager.getInstance(context)

    init {
        loadCache()
    }


    /**
    * Upload a document
    */
    override suspend fun uploadDocument(
        applicationId: String,
        documentType: DocumentType,
        fileType: FileType,
        fileUri: Uri,
        fileName: String
    ): Flow<Resource<Document>> = flow {
        emit(Resource.Loading())
        
        try {
            // Check document upload settings
            val uploadEnabled = preferencesDataSource.getBoolean("pref_document_upload_enabled", true)
            val resolutionStr = preferencesDataSource.getString("pref_document_resolution") ?: "MEDIUM_2MP"
            val resolution = try {
                ImageResolution.valueOf(resolutionStr)
            } catch (e: Exception) {
                ImageResolution.MEDIUM_2MP
            }
            
            // Create a document ID
            val documentId = UUID.randomUUID().toString()
            
            // First, create a temporary document to return quickly to the UI
            val tempDocument = Document(
                id = documentId,
                applicationId = applicationId,
                documentType = documentType,
                fileType = fileType,
                fileName = fileName,
                fileSize = FileUtils.getFileSize(context, fileUri),
                uploadedAt = LocalDateTime.now(),
                documentStatus = DocumentStatus.UPLOADED,
                documentSourceType = determineDocumentSourceType(fileType, fileUri),
                storageUrl = null,
                localUri = fileUri.toString(),
                processingResult = null,
                extractionStatus = ExtractionStatus.NOT_ATTEMPTED
            )
            
            // Save to local cache
            saveDocumentToCache(tempDocument)
            
            // Emit the temporary document for immediate UI feedback
            emit(Resource.Success(tempDocument))
            
            // Process the document if needed
            val processedUri = if (fileType == FileType.JPG || fileType == FileType.PNG) {
                documentProcessingService.processImage(fileUri, resolution)
            } else {
                fileUri
            }
            
            // Add to the new collection structure
            try {
                // Prepare document map for the new structure
                val documentMap = mapOf(
                    "id" to documentId,
                    "documentType" to documentType.name,
                    "documentStatus" to DocumentStatus.UPLOADED.name,
                    "documentSourceType" to determineDocumentSourceType(fileType, fileUri).name,
                    "fileDetails" to mapOf(
                        "fileName" to fileName,
                        "fileType" to fileType.name,
                        "fileSize" to tempDocument.fileSize
                        // storageUrl will be added later in background upload
                    ),
                    "uploadedAt" to DateConverter.toTimestamp(LocalDateTime.now()),
                    "extractionStatus" to ExtractionStatus.NOT_ATTEMPTED.name
                )
                
                // Check if application-documents collection has a document for this application
                val appDocRef = firestore.collection("application-documents").document(applicationId)
                val appDoc = appDocRef.get().await()
                
                if (appDoc.exists()) {
                    // Update existing document
                    appDocRef.update(
                        "documents", FieldValue.arrayUnion(documentMap),
                        "lastUpdatedAt", FieldValue.serverTimestamp()
                    ).await()
                    AppLogger.d("Added document to existing application-documents record")
                } else {
                    // Create new document
                    val dataMap = HashMap<String, Any>()
                    dataMap["applicationId"] = applicationId
                    dataMap["userId"] = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    dataMap["documents"] = listOf(documentMap)
                    dataMap["lastUpdatedAt"] = FieldValue.serverTimestamp()
                    
                    appDocRef.set(dataMap).await()
                    AppLogger.d("Created new application-documents record")
                }
            } catch (e: Exception) {
                AppLogger.e("Error adding document to new structure: ${e.message}", e)
                // Continue anyway - document will still be uploaded to original structure
            }
            
            // Schedule background upload if enabled
            if (uploadEnabled) {
                scheduleDocumentUpload(
                    applicationId,
                    documentId,
                    documentType,
                    processedUri.toString(),
                    fileName
                )
            }
            
            // Log the document upload event
            metadataRepository.recordDocumentEvent(
                applicationId = applicationId,
                documentEvent = DocumentEvent(
                    documentType = documentType.name,
                    documentSourceType = determineDocumentSourceType(fileType, fileUri).name,
                    action = "UPLOAD_STARTED",
                    fileMetadata = mapOf(
                        "fileName" to fileName,
                        "fileSize" to tempDocument.fileSize,
                        "fileType" to fileType.name
                    )
                )
            )
            
        } catch (e: Exception) {
            val errorMessage = "Error uploading document: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // Create a local document entity for offline functionality
            try {
                val documentId = UUID.randomUUID().toString()
                
                val document = Document(
                    id = documentId,
                    applicationId = applicationId,
                    documentType = documentType,
                    fileType = fileType,
                    fileName = fileName,
                    fileSize = FileUtils.getFileSize(context, fileUri),
                    uploadedAt = LocalDateTime.now(),
                    documentStatus = DocumentStatus.ERROR,
                    documentSourceType = determineDocumentSourceType(fileType, fileUri),
                    storageUrl = null,
                    localUri = fileUri.toString(),
                    processingResult = null,
                    extractionStatus = ExtractionStatus.NOT_ATTEMPTED
                )
                
                // Save to local cache
                saveDocumentToCache(document)
                
                // Log the error event
                metadataRepository.recordDocumentEvent(
                    applicationId = applicationId,
                    documentEvent = DocumentEvent(
                        documentType = documentType.name,
                        documentSourceType = determineDocumentSourceType(fileType, fileUri).name,
                        action = "UPLOAD_ERROR",
                        status = "ERROR",
                        failureReason = errorMessage,
                        fileMetadata = mapOf(
                            "fileName" to fileName,
                            "fileSize" to document.fileSize,
                            "fileType" to fileType.name
                        )
                    )
                )
                
                // Emit the local document
                emit(Resource.Success(document))
                
            } catch (e2: Exception) {
                AppLogger.e("Error creating local document: ${e2.message}", e2)
                // Original error is more important
                emit(Resource.Error(errorMessage))
            }
        }
    }




    /**
     * Upload an already created document
     * This method is used by the DocumentViewModel
     */
    suspend fun uploadDocument(document: Document): Flow<Resource<Document>> = flow {
        emit(Resource.Loading())
        
        try {
            // Save to local cache first (already created document object)
            saveDocumentToCache(document)
            
            // Emit the document for immediate UI feedback
            emit(Resource.Success(document))
            
            // Check document upload settings
            val uploadEnabled = preferencesDataSource.getBoolean("pref_document_upload_enabled", true)
            
            // Process the document if needed
            if (document.localUri != null) {
                val fileUri = Uri.parse(document.localUri)
                val resolutionStr = preferencesDataSource.getString("pref_document_resolution") ?: "MEDIUM_2MP"
                val resolution = try {
                    ImageResolution.valueOf(resolutionStr)
                } catch (e: Exception) {
                    ImageResolution.MEDIUM_2MP
                }
                
                val processedUri = if (document.fileType == FileType.JPG || document.fileType == FileType.PNG) {
                    documentProcessingService.processImage(fileUri, resolution)
                } else {
                    fileUri
                }
                
                // Schedule background upload if enabled
                if (uploadEnabled) {
                    scheduleDocumentUpload(
                        document.applicationId,
                        document.id,
                        document.documentType,
                        processedUri.toString(),
                        document.fileName
                    )
                }
                
                // Log the document upload event
                metadataRepository.recordDocumentEvent(
                    applicationId = document.applicationId,
                    documentEvent = com.loansai.unassisted.domain.model.DocumentEvent(
                        documentType = document.documentType.name,
                        documentSourceType = document.documentSourceType.name,
                        action = "UPLOAD_STARTED",
                        fileMetadata = mapOf(
                            "fileName" to document.fileName,
                            "fileSize" to document.fileSize,
                            "fileType" to document.fileType.name
                        )
                    )
                )
            }
            
        } catch (e: Exception) {
            val errorMessage = "Error uploading document: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
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
    
    /**
     * Schedule a document upload using WorkManager
     */
    private fun scheduleDocumentUpload(
        applicationId: String,
        documentId: String,
        documentType: DocumentType,
        filePath: String,
        fileName: String
    ) {
        try {
            // Create input data for the worker
            val inputData = Data.Builder()
                .putString(DocumentUploadWorker.KEY_APPLICATION_ID, applicationId)
                .putString(DocumentUploadWorker.KEY_DOCUMENT_ID, documentId)
                .putString(DocumentUploadWorker.KEY_DOCUMENT_TYPE, documentType.name)
                .putString(DocumentUploadWorker.KEY_FILE_PATH, filePath)
                .putString(DocumentUploadWorker.KEY_FILE_NAME, fileName)
                .build()
            
            // Create work request
            val uploadWorkRequest = OneTimeWorkRequestBuilder<DocumentUploadWorker>()
                .setInputData(inputData)
                .build()
            
            // Enqueue the work
            workManager.enqueue(uploadWorkRequest)
            
            AppLogger.d("Document upload scheduled for document ID: $documentId")
        } catch (e: Exception) {
            AppLogger.e("Error scheduling document upload: ${e.message}", e)
        }
    }
    
    /**
     * Upload a document directly to Firebase Storage (for foreground uploads)
     */
    private suspend fun uploadToFirebaseStorage(uri: Uri, applicationId: String, documentId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Create storage reference
                val storageRef = storage.reference
                    .child("applications")
                    .child(applicationId)
                    .child("documents")
                    .child(documentId)
                
                // Get input stream from URI
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Unable to open input stream for URI: $uri")
                
                // Upload to Firebase Storage
                val uploadTask = storageRef.putStream(inputStream)
                
                // Wait for upload to complete
                uploadTask.await()
                
                // Get download URL
                val downloadUrl = storageRef.downloadUrl.await()
                
                // Return the download URL as string
                downloadUrl.toString()
                
            } catch (e: Exception) {
                AppLogger.e("Error uploading to Firebase Storage: ${e.message}", e)
                throw e
            }
        }
    }
    
    /**
     * Save document metadata to Firestore
     */
    private suspend fun saveToFirestore(
        document: Document,
        storageUrl: String
    ): Document {
        return withContext(Dispatchers.IO) {
            try {
                // Get the user ID for security rules
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                
                // Update document with storage URL
                val updatedDocument = document.copy(
                    storageUrl = storageUrl,
                    documentStatus = DocumentStatus.PROCESSED
                )
                
                // Create document data for Firestore
                val documentData = hashMapOf(
                    "id" to updatedDocument.id,
                    "applicationId" to updatedDocument.applicationId,
                    "documentType" to updatedDocument.documentType.name,
                    "documentSourceType" to updatedDocument.documentSourceType.name,
                    "fileType" to updatedDocument.fileType.name,
                    "fileName" to updatedDocument.fileName,
                    "fileSize" to updatedDocument.fileSize,
                    "uploadedAt" to updatedDocument.uploadedAt.toString(),
                    "documentStatus" to updatedDocument.documentStatus.name,
                    "storageUrl" to updatedDocument.storageUrl,
                    "extractionStatus" to updatedDocument.extractionStatus.name,
                    "userId" to userId  // Add userId for security rules
                )
                
                // Save to Firestore
                firestore.collection("documents")
                    .document(updatedDocument.id)
                    .set(documentData)
                    .await()
                
                // Update application document list
                firestore.collection("applications")
                    .document(updatedDocument.applicationId)
                    .update("documentIds", com.google.firebase.firestore.FieldValue.arrayUnion(updatedDocument.id))
                    .await()
                
                updatedDocument
            } catch (e: Exception) {
                AppLogger.e("Error saving to Firestore: ${e.message}", e)
                document
            }
        }
    }

    /**
     * Process a document for data extraction using the backend LLM API
     * New method for v1.5.0 to integrate with backend LLM service
     */

    override suspend fun processDocument(documentId: String): Flow<Resource<Document>> = flow {
        emit(Resource.Loading())
        var applicationIdForLog: String? = null // For logging in case of failure
        var documentTypeForLog: DocumentType? = null

        try {
            // Get the existing document from cache
            val existingDocument = documentsCache[documentId]
            if (existingDocument == null || existingDocument.localUri == null) {
                val errorMsg = "Document not found or local URI missing: $documentId"
                AppLogger.e(errorMsg)
                emit(Resource.Error(errorMsg))
                return@flow
            }
            applicationIdForLog = existingDocument.applicationId
            documentTypeForLog = existingDocument.documentType

            AppLogger.d("Processing document $documentId locally using GeminiDocumentService")

            // Update document status to PROCESSING in cache
            val processingDocument = existingDocument.copy(
                documentStatus = DocumentStatus.PROCESSING,
                extractionStatus = ExtractionStatus.PENDING
            )
            saveDocumentToCache(processingDocument)
            
            // Update original documents collection
            try {
                firestore.collection("documents").document(documentId).update(
                    mapOf(
                        "documentStatus" to DocumentStatus.PROCESSING.name,
                        "extractionStatus" to ExtractionStatus.PENDING.name,
                        "lastUpdatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                AppLogger.d("Updated original Firestore collection status to PROCESSING for $documentId")
                
                // Also update new structure
                try {
                    val applicationId = existingDocument.applicationId
                    val appDocRef = firestore.collection("application-documents").document(applicationId)
                    val appDoc = appDocRef.get().await()
                    
                    if (appDoc.exists()) {
                        val documentsArray = appDoc.get("documents") as? List<Map<String, Any>> ?: emptyList()
                        val documentIndex = documentsArray.indexOfFirst { it["id"] == documentId }
                        
                        if (documentIndex >= 0) {
                            // Create a mutable copy of the documents array
                            val updatedDocuments = documentsArray.toMutableList()
                            val updatedDocument = (documentsArray[documentIndex] as Map<String, Any>).toMutableMap()
                            
                            // Update the status
                            updatedDocument["documentStatus"] = DocumentStatus.PROCESSING.name
                            updatedDocument["extractionStatus"] = ExtractionStatus.PENDING.name
                            
                            // Replace the document in the array
                            updatedDocuments[documentIndex] = updatedDocument
                            
                            // Update the whole array
                            appDocRef.update(
                                "documents", updatedDocuments,
                                "lastUpdatedAt", FieldValue.serverTimestamp()
                            ).await()
                            
                            AppLogger.d("Updated new structure status to PROCESSING for $documentId")
                        }
                    }
                } catch (e2: Exception) {
                    AppLogger.w("Error updating new structure: ${e2.message}")
                    // Continue processing even if new structure update fails
                }
            } catch (e: Exception) {
                AppLogger.w("Failed to update Firestore status to PROCESSING for $documentId: ${e.message}")
                // Continue processing even if status update fails initially
            }

            // Record LLM interaction initiated
            metadataRepository.recordLlmInteractionEvent(
                applicationId = processingDocument.applicationId,
                llmInteraction = LlmInteractionEvent(
                    llmCallType = "DOC_PROCESSING_GEMINI", 
                    status = "INITIATED",
                    modelUsed = geminiDocumentService.modelName
                )
            ).collect { } // Execute the flow

            // Call the local Gemini service directly
            val extractedDataMap = geminiDocumentService.processDocument(processingDocument)

            // Update document with extracted data and status
            val success = extractedDataMap["success"] as? Boolean ?: false
            val finalStatus = if (success) DocumentStatus.PROCESSED else DocumentStatus.ERROR
            val finalExtractionStatus = if (success) ExtractionStatus.SUCCESS else ExtractionStatus.FAILURE
            val failureReason = if (!success) extractedDataMap["error"] as? String else null
            
            // Get the extracted data map, removing internal flags
            val actualExtractedData = if (success) extractedDataMap.filterKeys { it != "success" && it != "documentType" } else null

            val processedDocument = processingDocument.copy(
                documentStatus = finalStatus,
                extractionStatus = finalExtractionStatus,
                extractedData = actualExtractedData,
                processingResult = processingDocument.processingResult?.copy(
                    isProcessed = success,
                    processedAt = LocalDateTime.now(),
                    extractionErrors = if(failureReason != null) listOf(failureReason) else emptyList()
                ) ?: DocumentProcessingResult(
                    isProcessed = success,
                    processedAt = LocalDateTime.now(),
                    processingMethod = ProcessingMethod.BACKEND_LLM_API,
                    extractedFields = emptyMap(),
                    extractionErrors = if(failureReason != null) listOf(failureReason) else emptyList()
                )
            )
            saveDocumentToCache(processedDocument)

            // Update in documents collection

            try {
                firestore.collection("documents")
                    .document(documentId)
                    .set( // Use set with merge for robustness
                        mapOf(
                            "documentStatus" to finalStatus.name,
                            "extractionStatus" to finalExtractionStatus.name,
                            "extractedData" to actualExtractedData,
                            "failureReason" to failureReason,
                            "processedAt" to FieldValue.serverTimestamp(), // OK here (root level)
                            "lastUpdatedAt" to FieldValue.serverTimestamp() // OK here (root level)
                        ),
                        SetOptions.merge() // Merge with existing data if document exists
                    )
                    .await()
                AppLogger.d("Original Firestore collection updated for document $documentId with status: $finalStatus")

                // --- FIX for updating the 'application-documents' collection ---
                try {
                    val applicationId = existingDocument.applicationId // Use the correct app ID
                    val appDocRef = firestore.collection("application-documents").document(applicationId)
                    val appDoc = appDocRef.get().await()

                    // Get current client-side timestamp
                    val nowTimestamp = DateConverter.toTimestamp(LocalDateTime.now())

                    if (appDoc.exists()) {
                        val documentsArray = appDoc.get("documents") as? List<Map<String, Any>> ?: emptyList()
                        val documentIndex = documentsArray.indexOfFirst { it["id"] == documentId }

                        if (documentIndex >= 0) {
                            // Create a mutable copy of the documents array
                            val updatedDocuments = documentsArray.toMutableList()
                            // Create a mutable copy of the specific document map
                            val updatedDocumentMap = documentsArray[documentIndex].toMutableMap()

                            // Update the fields within the map
                            updatedDocumentMap["documentStatus"] = finalStatus.name
                            updatedDocumentMap["extractionStatus"] = finalExtractionStatus.name
                            updatedDocumentMap["processedAt"] = nowTimestamp // Use CLIENT timestamp
                            updatedDocumentMap["failureReason"] = failureReason ?: FieldValue.delete() // Remove if null

                            if (actualExtractedData != null) {
                                updatedDocumentMap["extractedData"] = actualExtractedData
                            } else {
                                updatedDocumentMap.remove("extractedData") // Remove if null
                            }

                            // Replace the old map with the updated map in the list
                            updatedDocuments[documentIndex] = updatedDocumentMap

                            // Update the entire 'documents' array field in Firestore
                            appDocRef.update(
                                mapOf(
                                    "documents" to updatedDocuments,
                                    "lastUpdatedAt" to FieldValue.serverTimestamp() // OK at root level
                                )
                            ).await()

                            AppLogger.d("New structure updated for document $documentId with status: $finalStatus")
                        } else {
                            AppLogger.w("Document $documentId not found within the documents array of application $applicationId.")
                            // Optionally, add the document if not found using arrayUnion
                             val newDocumentMap = mapOf(
                                 "id" to documentId,
                                 "documentType" to existingDocument.documentType.name, // Get type from existing doc
                                 "documentStatus" to finalStatus.name,
                                 "documentSourceType" to existingDocument.documentSourceType.name, // Get source type
                                 "fileDetails" to (existingDocument.processingResult?.extractedFields?.get("fileDetails") ?: mapOf<String, Any>()), // Get fileDetails if available
                                 "uploadedAt" to DateConverter.toTimestamp(existingDocument.uploadedAt), // Use existing upload time
                                 "processedAt" to nowTimestamp, // Use CLIENT timestamp
                                 "extractionStatus" to finalExtractionStatus.name,
                                 "extractedData" to actualExtractedData,
                                 "failureReason" to failureReason
                             )
                             appDocRef.update(
                                 "documents", FieldValue.arrayUnion(newDocumentMap),
                                 "lastUpdatedAt", FieldValue.serverTimestamp()
                             ).await()
                             AppLogger.d("Added document $documentId to documents array for application $applicationId.")
                        }
                    } else {
                         AppLogger.w("Application document $applicationId not found in application-documents collection.")
                         // Handle case where the parent document doesn't exist if necessary
                    }
                } catch (e2: Exception) {
                    AppLogger.e("Error updating new structure: ${e2.message}", e2)
                    // Decide if this error should block the flow or just be logged
                }
                // --- END FIX ---

            } catch (e: Exception) {
                AppLogger.e("Error updating document status in Firestore: ${e.message}", e)
                // Decide how to handle this - e.g., retry, log, emit error?
            }

            // Log LLM completion
            metadataRepository.recordLlmInteractionEvent(
                applicationId = processedDocument.applicationId,
                llmInteraction = LlmInteractionEvent(
                    llmCallType = "DOC_PROCESSING_GEMINI",
                    status = if (success) "COMPLETED" else "FAILED",
                    failureReason = failureReason,
                    modelUsed = geminiDocumentService.modelName
                )
            ).collect { }

            // Record document event completion

            try {
                recordDocumentEventInternal(
                    applicationId = processedDocument.applicationId,
                    documentType = processedDocument.documentType,
                    documentSourceType = processedDocument.documentSourceType.name,
                    action = "LLM_PROCESSING",
                    status = if (success) "SUCCESS" else "ERROR",
                    extractionStatus = finalExtractionStatus.name,
                    failureReason = failureReason
                )
            } catch (e: Exception) {
                AppLogger.e("Error recording document event: ${e.message}", e)
            }

            if (success) {
                emit(Resource.Success(processedDocument))
            } else {
                emit(Resource.Error(failureReason ?: "Gemini processing failed"))
            }

        } catch (e: Exception) {
            val errorMessage = "Error processing document $documentId locally with Gemini: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // Comprehensive error handling in catch block
            if (applicationIdForLog != null) {
                try {
                    // Update error status in original collection
                    firestore.collection("documents").document(documentId).update(
                        mapOf(
                            "documentStatus" to DocumentStatus.ERROR.name,
                            "extractionStatus" to ExtractionStatus.FAILURE.name,
                            "failureReason" to errorMessage,
                            "lastUpdatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    
                    // Try to update new structure too
                    try {
                        val appDocRef = firestore.collection("application-documents").document(applicationIdForLog)
                        val appDoc = appDocRef.get().await()
                        
                        if (appDoc.exists()) {
                            val documentsArray = appDoc.get("documents") as? List<Map<String, Any>> ?: emptyList()
                            val documentIndex = documentsArray.indexOfFirst { it["id"] == documentId }
                            
                            if (documentIndex >= 0) {
                                // Create updated copy
                                val updatedDocuments = documentsArray.toMutableList()
                                val updatedDocument = (documentsArray[documentIndex] as Map<String, Any>).toMutableMap()
                                
                                // Update status
                                updatedDocument["documentStatus"] = DocumentStatus.ERROR.name
                                updatedDocument["extractionStatus"] = ExtractionStatus.FAILURE.name
                                updatedDocument["failureReason"] = errorMessage
                                
                                // Replace in the array
                                updatedDocuments[documentIndex] = updatedDocument
                                
                                // Update the array
                                appDocRef.update(
                                    "documents", updatedDocuments,
                                    "lastUpdatedAt", FieldValue.serverTimestamp()
                                ).await()
                            }
                        }
                    } catch (e3: Exception) {
                        AppLogger.e("Error updating new structure for error status: ${e3.message}")
                        // Continue anyway
                    }
                    
                    // Record LLM failure metadata
                    metadataRepository.recordLlmInteractionEvent(
                        applicationId = applicationIdForLog,
                        llmInteraction = LlmInteractionEvent(
                            llmCallType = "DOC_PROCESSING_GEMINI",
                            status = "FAILED",
                            failureReason = errorMessage,
                            modelUsed = geminiDocumentService.modelName
                        )
                    ).collect { }

                    // Record document event failure
                    if (documentTypeForLog != null) {
                        recordDocumentEventInternal(
                            applicationId = applicationIdForLog,
                            documentType = documentTypeForLog,
                            documentSourceType = "UNKNOWN", // Source type might be unknown here
                            action = "LLM_PROCESSING",
                            status = "ERROR",
                            extractionStatus = ExtractionStatus.FAILURE.name,
                            failureReason = errorMessage
                        )
                    }
                } catch (fsError: Exception) { 
                    AppLogger.e("Failed to update Firestore status to ERROR: ${fsError.message}") 
                }
            }

            emit(Resource.Error(errorMessage))
        }
    }

    // Helper to reduce duplication in error handling for metadata recording
    // (Add this helper function to the class if it doesn't exist)
    private suspend fun recordDocumentEventInternal(
        applicationId: String,
        documentType: DocumentType,
        documentSourceType: String,
        action: String,
        status: String? = null,
        failureReason: String? = null,
        extractionStatus: String? = null
    ) {
         try {
             metadataRepository.recordDocumentEvent(
                 applicationId = applicationId,
                 documentEvent = DocumentEvent(
                     documentType = documentType.name,
                     documentSourceType = documentSourceType,
                     action = action,
                     timestamp = LocalDateTime.now(),
                     status = status,
                     failureReason = failureReason,
                     extractionStatus = extractionStatus
                 )
             ).collect { }
         } catch (e: Exception) {
             AppLogger.e("Error recording document event metadata: ${e.message}", e)
         }
    }
    



    /**
     * Check document processing status
     * This method polls Firestore to check if the backend LLM API has updated the document
     */
    override suspend fun checkDocumentProcessingStatus(documentId: String): Flow<Resource<Document>> = flow {
        emit(Resource.Loading())
        
        try {
            // Query Firestore for the latest document state
            val documentSnapshot = firestore.collection("documents")
                .document(documentId)
                .get()
                .await()
            
            if (documentSnapshot.exists()) {
                // Convert Firestore data to Document
                val extractionStatus = documentSnapshot.getString("extractionStatus")?.let {
                    try { ExtractionStatus.valueOf(it) } catch (e: Exception) { ExtractionStatus.NOT_ATTEMPTED }
                } ?: ExtractionStatus.NOT_ATTEMPTED
                
                val documentStatus = documentSnapshot.getString("documentStatus")?.let {
                    try { DocumentStatus.valueOf(it) } catch (e: Exception) { DocumentStatus.UPLOADED }
                } ?: DocumentStatus.UPLOADED
                
                val failureReason = documentSnapshot.getString("failureReason")
                
                // Get extracted data if available
                val extractedData = documentSnapshot.get("extractedData") as? Map<String, Any>
                
                // Update local cache
                val existingDocument = documentsCache[documentId]
                if (existingDocument != null) {
                    val updatedDocument = existingDocument.copy(
                        documentStatus = documentStatus,
                        extractionStatus = extractionStatus,
                        extractedData = extractedData
                    )
                    
                    // Save to local cache
                    saveDocumentToCache(updatedDocument)
                    
                    // Log the document processing status event
                    metadataRepository.recordDocumentEvent(
                        applicationId = updatedDocument.applicationId,
                        documentEvent = com.loansai.unassisted.domain.model.DocumentEvent(
                            documentType = updatedDocument.documentType.name,
                            documentSourceType = updatedDocument.documentSourceType.name,
                            action = "PROCESSING_STATUS",
                            status = documentStatus.name,
                            extractionStatus = extractionStatus.name,
                            failureReason = failureReason,
                            timestamp = LocalDateTime.now()
                        )
                    )
                    
                    emit(Resource.Success(updatedDocument))
                } else {
                    emit(Resource.Error("Document not found in local cache: $documentId"))
                }
            } else {
                emit(Resource.Error("Document not found in Firestore: $documentId"))
            }
        } catch (e: Exception) {
            val errorMessage = "Error checking document processing status: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Get all documents for an application
     */
    override fun getApplicationDocuments(applicationId: String): Flow<Resource<List<Document>>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try to get from new structure first
            try {
                val appDocRef = firestore.collection("application-documents").document(applicationId)
                val appDoc = appDocRef.get().await()
                
                if (appDoc.exists()) {
                    val documentsArray = appDoc.get("documents") as? List<Map<String, Any>> ?: emptyList()
                    
                    if (documentsArray.isNotEmpty()) {
                        val documentsFromNewStructure = documentsArray.mapNotNull { docMap ->
                            try {
                                // Extract fileDetails map
                                val fileDetailsMap = docMap["fileDetails"] as? Map<String, Any> ?: emptyMap()
                                
                                Document(
                                    id = docMap["id"] as? String ?: UUID.randomUUID().toString(),
                                    applicationId = applicationId,
                                    documentType = (docMap["documentType"] as? String)?.let { 
                                        try { DocumentType.valueOf(it) } 
                                        catch (e: Exception) { DocumentType.OTHER }
                                    } ?: DocumentType.OTHER,
                                    documentSourceType = (docMap["documentSourceType"] as? String)?.let {
                                        try { com.loansai.unassisted.domain.model.DocumentSourceType.valueOf(it) }
                                        catch (e: Exception) { com.loansai.unassisted.domain.model.DocumentSourceType.MANUAL_ENTRY }
                                    } ?: com.loansai.unassisted.domain.model.DocumentSourceType.MANUAL_ENTRY,
                                    fileType = (fileDetailsMap["fileType"] as? String)?.let { 
                                        try { FileType.valueOf(it) } 
                                        catch (e: Exception) { FileType.OTHER }
                                    } ?: FileType.OTHER,
                                    fileName = fileDetailsMap["fileName"] as? String ?: "Unknown",
                                    fileSize = (fileDetailsMap["fileSize"] as? Number)?.toLong() ?: 0L,
                                    uploadedAt = DateConverter.parseFirestoreValue(docMap["uploadedAt"]) ?: LocalDateTime.now(),
                                    documentStatus = (docMap["documentStatus"] as? String)?.let {
                                        try { DocumentStatus.valueOf(it) } 
                                        catch (e: Exception) { DocumentStatus.UPLOADED }
                                    } ?: DocumentStatus.UPLOADED,
                                    storageUrl = fileDetailsMap["storageUrl"] as? String,
                                    extractionStatus = (docMap["extractionStatus"] as? String)?.let {
                                        try { ExtractionStatus.valueOf(it) } 
                                        catch (e: Exception) { ExtractionStatus.NOT_ATTEMPTED }
                                    } ?: ExtractionStatus.NOT_ATTEMPTED,
                                    extractedData = docMap["extractedData"] as? Map<String, Any>,
                                    localUri = documentsCache[docMap["id"] as? String]?.localUri // Preserve local URI from cache
                                )
                            } catch (e: Exception) {
                                AppLogger.e("Error parsing document from new structure: ${e.message}", e)
                                null
                            }
                        }
                        
                        // Update local cache with the fetched documents
                        documentsFromNewStructure.forEach { saveDocumentToCache(it) }
                        
                        emit(Resource.Success(documentsFromNewStructure))
                        return@flow
                    } else {
                        AppLogger.d("New structure has empty document array, falling back to original structure")
                    }
                } else {
                    AppLogger.d("New structure document not found, falling back to original structure")
                }
            } catch (e: Exception) {
                AppLogger.e("Error fetching from new structure: ${e.message}", e)
                // Continue to try the original structure
            }
            
            // Fall back to original collection if needed
            try {
                // Query Firestore for documents
                val snapshot = firestore.collection("documents")
                    .whereEqualTo("applicationId", applicationId)
                    .get()
                    .await()
                
                // Convert document snapshots to Document objects
                val documentsFromFirestore = snapshot.documents.mapNotNull { doc ->
                    try {
                        val documentType = doc.getString("documentType")?.let { 
                            DocumentType.valueOf(it) 
                        } ?: DocumentType.OTHER
                        
                        val documentSourceType = doc.getString("documentSourceType")?.let {
                            try { com.loansai.unassisted.domain.model.DocumentSourceType.valueOf(it) }
                            catch (e: Exception) { com.loansai.unassisted.domain.model.DocumentSourceType.MANUAL_ENTRY }
                        } ?: com.loansai.unassisted.domain.model.DocumentSourceType.MANUAL_ENTRY
                        
                        val extractionStatus = doc.getString("extractionStatus")?.let {
                            try { ExtractionStatus.valueOf(it) }
                            catch (e: Exception) { ExtractionStatus.NOT_ATTEMPTED }
                        } ?: ExtractionStatus.NOT_ATTEMPTED
                        
                        val extractedData = doc.get("extractedData") as? Map<String, Any>
                        
                        Document(
                            id = doc.getString("id") ?: doc.id,
                            applicationId = doc.getString("applicationId") ?: applicationId,
                            documentType = documentType,
                            documentSourceType = documentSourceType,
                            fileType = doc.getString("fileType")?.let { 
                                FileType.valueOf(it) 
                            } ?: FileType.OTHER,
                            fileName = doc.getString("fileName") ?: "Unknown",
                            fileSize = doc.getLong("fileSize") ?: 0L,
                            uploadedAt = doc.getString("uploadedAt")?.let {
                                try { LocalDateTime.parse(it) } catch (e: Exception) { LocalDateTime.now() }
                            } ?: LocalDateTime.now(),
                            documentStatus = doc.getString("documentStatus")?.let {
                                try { DocumentStatus.valueOf(it) } catch (e: Exception) { DocumentStatus.UPLOADED }
                            } ?: DocumentStatus.UPLOADED,
                            storageUrl = doc.getString("storageUrl"),
                            extractionStatus = extractionStatus,
                            extractedData = extractedData,
                            localUri = documentsCache[doc.id]?.localUri // Preserve local URI from cache if available
                        )
                    } catch (e: Exception) {
                        AppLogger.e("Error parsing Firestore document: ${e.message}", e)
                        null
                    }
                }
                
                // Update local cache with Firestore data
                documentsFromFirestore.forEach { saveDocumentToCache(it) }
                
                // Also try to add to the new structure for future use (if we got documents)
                if (documentsFromFirestore.isNotEmpty()) {
                    try {
                        val appDocRef = firestore.collection("application-documents").document(applicationId)
                        
                        // Convert documents to the new format
                        val documentsArray = documentsFromFirestore.map { doc ->
                            mapOf(
                                "id" to doc.id,
                                "documentType" to doc.documentType.name,
                                "documentStatus" to doc.documentStatus.name,
                                "documentSourceType" to doc.documentSourceType.name,
                                "fileDetails" to mapOf(
                                    "fileName" to doc.fileName,
                                    "fileType" to doc.fileType.name,
                                    "fileSize" to doc.fileSize,
                                    "storageUrl" to (doc.storageUrl ?: "")
                                ),
                                "uploadedAt" to DateConverter.toTimestamp(doc.uploadedAt),
                                "extractionStatus" to doc.extractionStatus.name,
                                "extractedData" to (doc.extractedData ?: emptyMap<String, Any>())
                            )
                        }
                        
                        // Check if document already exists
                        val existingDoc = appDocRef.get().await()
                        if (!existingDoc.exists()) {
                            // Create new document
                            // Replace with HashMap construction
                            val documentData = HashMap<String, Any>()
                            documentData["applicationId"] = applicationId
                            documentData["userId"] = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            documentData["documents"] = documentsArray
                            documentData["lastUpdatedAt"] = FieldValue.serverTimestamp()
                            // Then use documentData as before
                            
                            appDocRef.set(documentData).await()
                            
                            AppLogger.d("Created new application-documents record with ${documentsArray.size} documents")
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error creating new structure document: ${e.message}", e)
                        // Continue anyway - we'll return the documents from original structure
                    }
                }
                
                emit(Resource.Success(documentsFromFirestore))
                
            } catch (e: Exception) {
                AppLogger.e("Error fetching documents from Firestore: ${e.message}", e)
                
                // Fall back to local cache
                try {
                    val documentsFromCache = documentsCache.values
                        .filter { it.applicationId == applicationId }
                        .toList()
                    
                    emit(Resource.Success(documentsFromCache))
                } catch (e2: Exception) {
                    emit(Resource.Error("Error getting documents: ${e.message}"))
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting application documents: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Get documents of a specific type for an application
     */
    override fun getDocumentsByType(
        applicationId: String,
        documentType: DocumentType
    ): Flow<Resource<List<Document>>> = flow {
        emit(Resource.Loading())
        
        try {
            // Reuse getApplicationDocuments and filter by type
            getApplicationDocuments(applicationId).collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val filteredDocuments = result.data.filter { it.documentType == documentType }
                        emit(Resource.Success(filteredDocuments))
                    }
                    is Resource.Error -> emit(result)
                    is Resource.Loading -> emit(Resource.Loading())
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting documents by type: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Delete a document
     */
    override suspend fun deleteDocument(documentId: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get document from cache to know the application ID
            val document = documentsCache[documentId]
            
            if (document == null) {
                emit(Resource.Error("Document not found in cache: $documentId"))
                return@flow
            }
            
            val applicationId = document.applicationId
            
            // Delete from new Firestore structure
            try {
                val appDocRef = firestore.collection("application-documents").document(applicationId)
                val appDoc = appDocRef.get().await()
                
                if (appDoc.exists()) {
                    val documentsArray = appDoc.get("documents") as? List<Map<String, Any>> ?: emptyList()
                    val updatedDocuments = documentsArray.filter { it["id"] != documentId }
                    
                    // Update with removed document
                    appDocRef.update(
                        "documents", updatedDocuments,
                        "lastUpdatedAt", FieldValue.serverTimestamp()
                    ).await()
                    
                    AppLogger.d("Removed document from application-documents collection")
                }
            } catch (e: Exception) {
                AppLogger.e("Error removing from new structure: ${e.message}", e)
                // Continue with old deletion approach
            }
            
            // Delete from original Firestore collection - PRESERVE ORIGINAL LOGIC
            try {
                // Delete from Firestore
                firestore.collection("documents")
                    .document(documentId)
                    .delete()
                    .await()
                
                // If document has a storage URL, delete from Storage
                if (document.storageUrl != null) {
                    // Extract the path from the storage URL
                    val url = document.storageUrl!!
                    val path = url.substringAfter("o/").substringBefore("?")
                    
                    // Delete from Storage
                    storage.reference.child(path).delete().await()
                }
                
                // Log the document deletion event
                metadataRepository.recordDocumentEvent(
                    applicationId = document.applicationId,
                    documentEvent = com.loansai.unassisted.domain.model.DocumentEvent(
                        documentType = document.documentType.name,
                        documentSourceType = document.documentSourceType.name,
                        action = "DELETE",
                        timestamp = LocalDateTime.now()
                    )
                )
                
            } catch (e: Exception) {
                AppLogger.e("Error deleting from Firestore/Storage: ${e.message}", e)
                // Continue with local deletion
            }
            
            // Update application's documentIds field - PRESERVE ORIGINAL LOGIC
            try {
                firestore.collection("applications")
                    .document(applicationId)
                    .update("documentIds", FieldValue.arrayRemove(documentId))
                    .await()
            } catch (e: Exception) {
                AppLogger.e("Error updating application documentIds: ${e.message}", e)
                // Continue with local deletion
            }
            
            // Delete from local cache - PRESERVE ORIGINAL LOGIC
            documentsCache.remove(documentId)
            saveCache()
            
            // Remove the actual file if it exists locally - PRESERVE ORIGINAL LOGIC
            document.localUri?.let { localUri ->
                try {
                    val file = File(Uri.parse(localUri).path ?: "")
                    if (file.exists() && file.isFile) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error deleting local file: ${e.message}", e)
                }
            }
            
            emit(Resource.Success(true))
            
        } catch (e: Exception) {
            val errorMessage = "Error deleting document: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // Try to delete locally even if remote deletion fails - PRESERVE ORIGINAL LOGIC
            try {
                documentsCache.remove(documentId)
                saveCache()
                emit(Resource.Success(true))
            } catch (e2: Exception) {
                AppLogger.e("Error deleting document locally: ${e2.message}", e2)
                emit(Resource.Error(errorMessage))
            }
        }
    }


    /**
    * Safely extract a string value from a map
    */
    private fun extractString(map: Map<String, Any?>, key: String): String? {
        return map[key] as? String
    }

    /**
    * Safely extract a numeric value from a map as a string
    */
    private fun extractNumber(map: Map<String, Any?>, key: String): String? {
        val value = map[key]
        return when (value) {
            is Number -> value.toString()
            is String -> value
            else -> null
        }
    }







    /**
     * Extract bank statement data from a document
     */
    override suspend fun extractBankStatementData(documentId: String): Flow<Resource<BankStatementData>> = flow {
        emit(Resource.Loading())
        
        try {
            // First check if the document is in our cache
            val document = documentsCache[documentId]
            
            if (document?.processingResult != null && 
                document.documentType == DocumentType.BANK_STATEMENT) {
                
                // Try to extract from processing result
                val extractedFields = document.processingResult!!.extractedFields
                
                // If we have enough fields, construct BankStatementData
                if (extractedFields.containsKey("BANK_NAME") ||
                    extractedFields.containsKey("ACCOUNT_NUMBER")) {
                    
                    val bankStatementData = BankStatementData(
                        bankName = extractedFields["BANK_NAME"],
                        accountNumber = extractedFields["ACCOUNT_NUMBER"],
                        accountHolderName = extractedFields["ACCOUNT_HOLDER_NAME"],
                        statementPeriod = if (extractedFields.containsKey("STATEMENT_PERIOD_START") &&
                        extractedFields.containsKey("STATEMENT_PERIOD_END")) {
                        val startStr = extractedFields["STATEMENT_PERIOD_START"]
                        val endStr = extractedFields["STATEMENT_PERIOD_END"]
                        if (startStr != null && endStr != null) {
                            try {
                                val startDate = parseDate(startStr)
                                val endDate = parseDate(endStr)
                                if (startDate != null && endDate != null) {
                                    "$startDate to $endDate" // Format as a string
                                } else {
                                    null
                                }
                            } catch (e: Exception) {
                                null
                            }
                        } else null
                    } else null,
                        // Other fields would be populated similarly
                        transactions = emptyList(),
                        averageBalance = extractedFields["AVERAGE_BALANCE"]?.toDoubleOrNull(),
                        closingBalance = extractedFields["CLOSING_BALANCE"]?.toDoubleOrNull(),
                        totalCredits = null,
                        totalDebits = null,
                        salaryCredits = emptyList()
                    )
                    
                    emit(Resource.Success(bankStatementData))
                    return@flow
                }
            }
            
            // Try to extract from extractedData
            // Try to extract from extractedData within processingResult
            val extractedFields = document?.processingResult?.extractedFields // Use the nested map
            if (extractedFields != null &&
                document?.documentType == DocumentType.BANK_STATEMENT) { // Check document type as well

                // Now extract using the extractedFields map
                val bankName = extractString(extractedFields, "bankName") // Use helper for safety
                val accountNumber = extractString(extractedFields, "accountNumber")
                val accountHolderName = extractString(extractedFields, "accountHolderName")
                val averageBalance = extractNumber(extractedFields, "averageBalance") // Use helper
                val closingBalance = extractNumber(extractedFields, "closingBalance")
                // Parse other fields similarly using extractString/extractNumber helpers...
                val totalCredits = extractNumber(extractedFields, "totalCredits")
                val totalDebits = extractNumber(extractedFields, "totalDebits")

                // Only create BankStatementData if we have something meaningful
                if (bankName != null || accountNumber != null || accountHolderName != null || averageBalance != null || closingBalance != null) {
                     val bankStatementData = BankStatementData(
                        bankName = bankName,
                        accountNumber = accountNumber,
                        accountHolderName = accountHolderName,
                        statementPeriod = null, // Add logic to parse start/end date if available
                        transactions = emptyList(),
                        averageBalance = averageBalance?.replace(",", "")?.toDoubleOrNull(), // Convert formatted string back
                        closingBalance = closingBalance?.replace(",", "")?.toDoubleOrNull(),
                        totalCredits = totalCredits?.replace(",", "")?.toDoubleOrNull(),
                        totalDebits = totalDebits?.replace(",", "")?.toDoubleOrNull(),
                        salaryCredits = emptyList()
                    )
                    emit(Resource.Success(bankStatementData))
                    return@flow
                }
            }
            
            // If we couldn't extract locally, try API
            val response = documentApi.extractBankStatementData(documentId)
            
            if (response.isSuccessful && response.body() != null) {
                val bankStatementData = response.body()!!.toBankStatementData()
                emit(Resource.Success(bankStatementData))
            } else {
                val errorMessage = "Failed to extract bank statement data: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error extracting bank statement data: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Get all documents for the current application
     * 
     * @return List of all documents for the current application
     */
    override suspend fun getAllDocuments(): List<Document> {
        return documentsCache.values.toList()
    }
    
    /**
     * Parse date string from various formats
     */
    private fun parseDate(dateStr: String): java.time.LocalDate? {
        val formats = listOf(
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "dd.MM.yyyy"
        )
        
        for (format in formats) {
            try {
                return java.time.LocalDate.parse(dateStr, java.time.format.DateTimeFormatter.ofPattern(format))
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        return null
    }
    
    /**
     * Save document to local cache
     */
    private fun saveDocumentToCache(document: Document) {
        documentsCache[document.id] = document
        saveCache()
    }
    
    /**
     * Save the cache to a file
     */
    private fun saveCache() {
        try {
            FileWriter(cacheFile).use { writer ->
                val json = gson.toJson(documentsCache.values.toList())
                writer.write(json)
            }
        } catch (e: Exception) {
            AppLogger.e("Error saving document cache: ${e.message}", e)
        }
    }
    
    /**
     * Load the cache from a file
     */
    private fun loadCache() {
        if (!cacheFile.exists()) return
        
        try {
            FileReader(cacheFile).use { reader ->
                val type = object : TypeToken<List<Document>>() {}.type
                val documents = gson.fromJson<List<Document>>(reader, type)
                
                documentsCache.clear()
                documents.forEach { document ->
                    documentsCache[document.id] = document
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Error loading document cache: ${e.message}", e)
            // If there's an error loading the cache, we'll start with an empty cache
            documentsCache.clear()
        }
    }
}