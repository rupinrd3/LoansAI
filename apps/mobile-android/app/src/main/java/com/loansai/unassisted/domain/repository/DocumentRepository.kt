package com.loansai.unassisted.domain.repository

import android.net.Uri
import com.loansai.unassisted.domain.model.BankStatementData
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for document operations
 * Updated for v1.5.0 with Backend LLM API integration
 */
interface DocumentRepository {
    
    /**
     * Upload a document
     *
     * @param applicationId The ID of the application
     * @param documentType The type of document
     * @param fileType The file type
     * @param fileUri The URI of the file to upload
     * @param fileName The name of the file
     * @return Flow of Resource<Document> with the uploaded document details
     */
    suspend fun uploadDocument(
        applicationId: String,
        documentType: DocumentType,
        fileType: FileType,
        fileUri: Uri,
        fileName: String
    ): Flow<Resource<Document>>
    
    /**
     * Process a document for data extraction using the backend LLM API
     *
     * @param documentId The ID of the document to process
     * @return Flow of Resource<Document> with the processed document
     */
    suspend fun processDocument(documentId: String): Flow<Resource<Document>>
    
    /**
     * Check the processing status of a document that's being processed by the backend LLM API
     *
     * @param documentId The ID of the document to check
     * @return Flow of Resource<Document> with the updated document
     */
    suspend fun checkDocumentProcessingStatus(documentId: String): Flow<Resource<Document>>
    
    /**
     * Get all documents for an application
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<List<Document>> with all application documents
     */
    fun getApplicationDocuments(applicationId: String): Flow<Resource<List<Document>>>
    
    /**
     * Get documents of a specific type for an application
     *
     * @param applicationId The ID of the application
     * @param documentType The type of documents to retrieve
     * @return Flow of Resource<List<Document>> with matching documents
     */
    fun getDocumentsByType(
        applicationId: String,
        documentType: DocumentType
    ): Flow<Resource<List<Document>>>
    
    /**
     * Delete a document
     *
     * @param documentId The ID of the document to delete
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun deleteDocument(documentId: String): Flow<Resource<Boolean>>
    
    /**
     * Extract bank statement data from a document
     *
     * @param documentId The ID of the bank statement document
     * @return Flow of Resource<BankStatementData> with extracted data
     */
    suspend fun extractBankStatementData(documentId: String): Flow<Resource<BankStatementData>>
    
    /**
     * Get all documents for the current application
     *
     * @return List of all documents for the current application
     */
    suspend fun getAllDocuments(): List<Document>
}