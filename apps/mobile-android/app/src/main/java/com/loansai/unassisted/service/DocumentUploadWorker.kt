package com.loansai.unassisted.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.loansai.unassisted.R
import com.loansai.unassisted.domain.model.DocumentStatus
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.StorageMetadata
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.util.DateConverter
import com.loansai.unassisted.domain.model.ExtractionStatus
import com.loansai.unassisted.domain.model.DocumentSourceType
import androidx.work.Data
import com.google.firebase.firestore.SetOptions







/**
 * WorkManager worker for uploading documents in the background
 */
class DocumentUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    // Corrected: Define constants within the companion object
    companion object {
        // Define TAG constant for logging
        private const val TAG = "DocumentUploadWorker"
        
        // Constants for input data keys
        const val KEY_APPLICATION_ID = "applicationId"
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_DOCUMENT_TYPE = "documentType"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_FILE_NAME = "fileName"
        
        // Corrected: Define Notification constants
        private const val CHANNEL_ID = "document_upload_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    // Corrected: 'context' is available as a property of CoroutineWorker
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    override suspend fun doWork(): Result {
        val applicationId = inputData.getString(KEY_APPLICATION_ID) ?: return Result.failure()
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        val documentType = inputData.getString(KEY_DOCUMENT_TYPE) ?: return Result.failure()
        val filePath = inputData.getString(KEY_FILE_PATH) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "unknown"
        
        try {
            Log.d(TAG, "Starting document upload for ID: $documentId")
            
            // Parse the file URI
            val fileUri = Uri.parse(filePath)
            Log.d(TAG, "File URI: $fileUri")
            
            // Get file type from file name
            val fileType = when {
                fileName.endsWith(".pdf", ignoreCase = true) -> "PDF"
                fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "JPG"
                fileName.endsWith(".png", ignoreCase = true) -> "PNG"
                else -> "OTHER"
            }
            
            // Get content resolver
            val contentResolver = applicationContext.contentResolver
            
            // Get input stream from URI
            val inputStream = contentResolver.openInputStream(fileUri)
                ?: throw IOException("Unable to open input stream")
            
            // Create a reference to Firebase Storage
            val storageRef = FirebaseStorage.getInstance().reference
                .child("applications")
                .child(applicationId)
                .child("documents")
                .child(documentId)
            
            // Get file metadata
            val contentType = when (fileType) {
                "PDF" -> "application/pdf"
                "JPG" -> "image/jpeg"
                "PNG" -> "image/png"
                else -> "application/octet-stream"
            }
            
            // Create metadata
            val metadata = StorageMetadata.Builder()
                .setContentType(contentType)
                .setCustomMetadata("documentType", documentType)
                .setCustomMetadata("applicationId", applicationId)
                .setCustomMetadata("fileName", fileName)
                .build()
            
            // Upload file to Firebase Storage
            val uploadTask = storageRef.putStream(inputStream, metadata).await()
            
            // Get download URL
            val storageUrl = storageRef.downloadUrl.await().toString()
            Log.d(TAG, "File uploaded to: $storageUrl")
            
            // Update document metadata in original Firestore structure
            val firestore = FirebaseFirestore.getInstance()
            val documentRef = firestore.collection("documents").document(documentId)
            
            // Create document data
            val documentData = hashMapOf(
                "id" to documentId,
                "applicationId" to applicationId,
                "documentType" to documentType,
                "fileType" to fileType,
                "fileName" to fileName,
                "fileSize" to uploadTask.totalByteCount,
                "uploadedAt" to LocalDateTime.now().toString(),
                "documentStatus" to "UPLOADED",
                "storageUrl" to storageUrl,
                "documentSourceType" to "IMAGE_UPLOAD", // Or determine based on URI
                "extractionStatus" to "NOT_ATTEMPTED",
                "lastUpdatedAt" to FieldValue.serverTimestamp()
            )
            
            // Save document to Firestore (using set with merge to handle if document already exists)
            documentRef.set(documentData, SetOptions.merge()).await()
            
            // Update application document list
            val applicationRef = firestore.collection("applications").document(applicationId)
            applicationRef.update("documentIds", FieldValue.arrayUnion(documentId)).await()
            
            // Now also update the new collection structure (if applicable)
            // Note: The original code for this update was present but seemed incomplete/incorrect.
            // Assuming the intent is to update the application-documents collection as seen in DocumentRepositoryImpl
            // Adding a placeholder for this update here.
            // updateDocumentInNewStructure(applicationId, documentId, storageUrl, documentType, fileName, fileType)
            
            // Log success
            Log.d(TAG, "Document uploaded and saved to Firestore: $documentId")
            
            // Try to trigger document processing if appropriate

            if (fileType == "JPG" || fileType == "PNG" || fileType == "PDF") {
                try {
                    // Get WorkManager instance
                    val workManager = WorkManager.getInstance(applicationContext)
                    
                    // Update document status to indicate it needs processing
                    try {
                        firestore.collection("documents")
                            .document(documentId)
                            .update(
                                "extractionStatus", "PENDING",
                                "lastUpdatedAt", FieldValue.serverTimestamp()
                            )
                            .await()
                        
                        Log.d(TAG, "Marked document for processing: $documentId")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating document status: ${e.message}", e)
                    }
                    
                    // Note: DocumentProcessingWorker implementation is needed
                    // Commented out until you create DocumentProcessingWorker class
                    /*
                    // Create WorkManager data for processing job
                    val processingData = Data.Builder()
                        .putString("documentId", documentId)
                        .putString("applicationId", applicationId)
                        .build()
                    
                    // Create work request for document processing
                    val processingWorkRequest = OneTimeWorkRequestBuilder<DocumentProcessingWorker>()
                        .setInputData(processingData)
                        .build()
                    
                    // Enqueue the work
                    workManager.enqueue(processingWorkRequest)
                    
                    Log.d(TAG, "Document processing job scheduled for $documentId")
                    */
                } catch (e: Exception) {
                    Log.e(TAG, "Error scheduling document processing: ${e.message}", e)
                    // Not critical - continue
                }
            }
            
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading document: ${e.message}", e)
            return Result.failure()
        }
    }
    
    private suspend fun uploadToStorage(uri: Uri, applicationId: String, documentId: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Create storage reference with correct path
                val storageRef = storage.reference
                    .child("applications")
                    .child(applicationId)  // Use applicationId folder instead of "temp"
                    .child("documents")
                    .child(documentId)
                
                // Get input stream from URI
                val inputStream = applicationContext.contentResolver.openInputStream(uri)
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
    
    private suspend fun updateFirestore(
        documentId: String,
        applicationId: String,
        documentType: DocumentType,
        fileName: String,
        storageUrl: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Get the user ID for security rules
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                
                // Get file size and type
                val fileUri = Uri.parse(storageUrl)
                val fileSize = try {
                    // Corrected: Use applicationContext to access contentResolver
                    val fileDescriptor = applicationContext.contentResolver.openAssetFileDescriptor(fileUri, "r")
                    val size = fileDescriptor?.length ?: 0L
                    fileDescriptor?.close()
                    size
                } catch (e: Exception) {
                    0L
                }
                
                val fileType = when {
                    fileName.endsWith(".pdf", ignoreCase = true) -> FileType.PDF.name
                    fileName.endsWith(".jpg", ignoreCase = true) -> FileType.JPG.name
                    fileName.endsWith(".jpeg", ignoreCase = true) -> FileType.JPG.name
                    fileName.endsWith(".png", ignoreCase = true) -> FileType.PNG.name
                    else -> FileType.OTHER.name
                }
                
                // Create document data for Firestore
                val documentData = hashMapOf(
                    "id" to documentId,
                    "applicationId" to applicationId,
                    "documentType" to documentType.name,
                    "fileName" to fileName,
                    "fileType" to fileType,
                    "fileSize" to fileSize,
                    "storageUrl" to storageUrl,
                    "uploadedAt" to LocalDateTime.now().toString(),
                    "documentStatus" to DocumentStatus.PROCESSED.name,
                    "extractionStatus" to ExtractionStatus.NOT_ATTEMPTED.name,
                    "documentSourceType" to DocumentSourceType.PDF_UPLOAD.name,  // Or determine based on file type
                    "userId" to userId  // Add userId for security rules
                )
                
                // Save to Firestore
                firestore.collection("documents")
                    .document(documentId)
                    .set(documentData)
                    .await()
                
                // Update application document list
                firestore.collection("applications")
                    .document(applicationId)
                    .update("documentIds", com.google.firebase.firestore.FieldValue.arrayUnion(documentId))
                    .await()
                
                // Update application progress if needed
                updateApplicationProgress(applicationId)
                
            } catch (e: Exception) {
                AppLogger.e("Error saving to Firestore: ${e.message}", e)
                throw e
            }
        }
    }

    // Add this new method to update application progress
    private suspend fun updateApplicationProgress(applicationId: String) {
        try {
            // Get current application data
            val applicationDoc = firestore.collection("applications")
                .document(applicationId)
                .get()
                .await()
            
            // Check if documents step needs to be marked as completed
            val currentStep = applicationDoc.getString("currentStep")
            if (currentStep == ApplicationStep.DOCUMENT_UPLOAD.name) {
                // Add DOCUMENT_UPLOAD to completedSteps if not already present
                val completedSteps = applicationDoc.get("completedSteps") as? List<String> ?: emptyList()
                if (!completedSteps.contains(ApplicationStep.DOCUMENT_UPLOAD.name)) {
                    firestore.collection("applications")
                        .document(applicationId)
                        .update(
                            "completedSteps", 
                            FieldValue.arrayUnion(ApplicationStep.DOCUMENT_UPLOAD.name),
                            "currentStep", ApplicationStep.LOAN_OFFER.name,  // Move to next step
                            "lastUpdatedAt", DateConverter.toTimestamp(LocalDateTime.now())  // Use proper timestamp format
                        )
                        .await()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Error updating application progress: ${e.message}", e)
        }
    }


    

    /**
    * Update document in the new structure with the storage URL
    */
    private suspend fun updateDocumentInNewStructure(
        applicationId: String,
        documentId: String,
        storageUrl: String,
        documentType: String,
        fileName: String,
        fileType: String
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            val appDocRef = db.collection("application-documents").document(applicationId)
            val appDoc = appDocRef.get().await()
            
            if (appDoc.exists()) {
                val documentsArray = appDoc.get("documents") as? List<Map<String, Any>> ?: emptyList()
                val documentIndex = documentsArray.indexOfFirst { it["id"] == documentId }
                
                if (documentIndex >= 0) {
                    // Document exists in array - update it
                    // Create a mutable copy of all documents
                    val updatedDocuments = documentsArray.toMutableList()
                    
                    // Create a mutable copy of the document
                    val updatedDocument = (documentsArray[documentIndex] as Map<String, Any>).toMutableMap()
                    
                    // Update file details
                    val fileDetails = (updatedDocument["fileDetails"] as? Map<String, Any>)?.toMutableMap() 
                        ?: mutableMapOf<String, Any>()
                    
                    // Add storage URL to file details - Fix the map creation syntax
                    val updatedFileDetails = fileDetails.toMutableMap().apply {
                        put("storageUrl", storageUrl)
                    }
                    
                    // Update document with new file details
                    updatedDocument["fileDetails"] = updatedFileDetails
                    
                    // Replace document in array
                    updatedDocuments[documentIndex] = updatedDocument
                    
                    // Update the document with the modified array
                    appDocRef.update(
                        "documents", updatedDocuments,
                        "lastUpdatedAt", FieldValue.serverTimestamp()
                    ).await()
                    
                    Log.d(TAG, "Updated storage URL in new structure for document: $documentId")
                } else {
                    // Document not found in array - add it
                    val newDocumentMap = mapOf(
                        "id" to documentId,
                        "documentType" to documentType,
                        "documentStatus" to "UPLOADED",
                        "documentSourceType" to "IMAGE_UPLOAD",
                        "fileDetails" to mapOf(
                            "fileName" to fileName,
                            "fileType" to fileType,
                            "fileSize" to 0L,
                            "storageUrl" to storageUrl
                        ),
                        "uploadedAt" to FieldValue.serverTimestamp(),
                        "extractionStatus" to "NOT_ATTEMPTED"
                    )
                    
                    appDocRef.update(
                        "documents", FieldValue.arrayUnion(newDocumentMap),
                        "lastUpdatedAt", FieldValue.serverTimestamp()
                    ).await()
                    
                    Log.d(TAG, "Added new document to application-documents: $documentId")
                }
            } else {
                // Application document doesn't exist - create it
                // Fix map creation syntax
                val newDocument = hashMapOf(
                    "applicationId" to applicationId,
                    "userId" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
                    "documents" to listOf(
                        mapOf(
                            "id" to documentId,
                            "documentType" to documentType,
                            "documentStatus" to "UPLOADED", 
                            "documentSourceType" to "IMAGE_UPLOAD",
                            "fileDetails" to mapOf(
                                "fileName" to fileName,
                                "fileType" to fileType,
                                "fileSize" to 0L,
                                "storageUrl" to storageUrl
                            ),
                            "uploadedAt" to FieldValue.serverTimestamp(),
                            "extractionStatus" to "NOT_ATTEMPTED"
                        )
                    ),
                    "lastUpdatedAt" to FieldValue.serverTimestamp()
                )
                
                appDocRef.set(newDocument).await()
                
                Log.d(TAG, "Created new application-documents record with document: $documentId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating document in new structure: ${e.message}", e)
            // Continue anyway as this is a secondary update
        }
    }
    
    // Corrected: createForegroundInfo requires 'context' which is available as a Worker property
    private fun createForegroundInfo(progress: String): ForegroundInfo {
        // Create notification channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, // Corrected: Use constant from companion object
                "Document Upload",
                NotificationManager.IMPORTANCE_LOW
            )
            // Corrected: Use applicationContext to access getSystemService
            val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Create notification
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID) // Corrected: Use applicationContext and constant
            .setContentTitle("Uploading Document") // Corrected: Use setContentTitle method
            .setContentText(progress)
            .setSmallIcon(android.R.drawable.ic_menu_upload) // Using a system icon instead
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        // Corrected: Use constant NOTIFICATION_ID
        // Return with foreground service type for Android 14+ (API 34+)
        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(
                NOTIFICATION_ID, // Corrected: Use constant
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification) // Corrected: Use constant
        }
    }
}