package com.loansai.unassisted.domain.usecase.document

import android.net.Uri
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.domain.repository.DocumentRepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * Use case for uploading a document
 */
class UploadDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val loanRepository: LoanRepository
) {
    /**
     * Uploads a document from a URI
     *
     * @param uri The URI of the document to upload
     * @param documentType The type of document being uploaded
     * @return The uploaded document
     */
    suspend operator fun invoke(uri: Uri, documentType: DocumentType): Document {
        val applicationResource = loanRepository.getCurrentApplication()
        if (applicationResource == null) {
            throw Exception("No active application found")
        }
        
        val filename = getFileName(uri)
        val fileType = getFileType(filename)
        
        val resource = documentRepository.uploadDocument(
            applicationId = applicationResource.id,
            documentType = documentType,
            fileType = fileType,
            fileUri = uri,
            fileName = filename
        ).first()
        
        return when (resource) {
            is Resource.Success -> resource.data
            else -> throw Exception(resource.errorOrNull() ?: "Failed to upload document")
        }
    }
    
    private fun getFileName(uri: Uri): String {
        return uri.lastPathSegment ?: "document_${System.currentTimeMillis()}"
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
}