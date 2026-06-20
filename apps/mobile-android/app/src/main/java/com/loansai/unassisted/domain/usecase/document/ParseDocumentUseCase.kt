package com.loansai.unassisted.domain.usecase.document

import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentProcessingResult
import com.loansai.unassisted.domain.repository.DocumentRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for parsing and extracting information from a document
 */
class ParseDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository
) {
    /**
     * Parses a document to extract information
     *
     * @param document The document to parse
     * @return The document with extracted information
     */
    suspend operator fun invoke(document: Document): Document {
        val resource = documentRepository.processDocument(document.id).first()
        return when (resource) {
            is Resource.Success -> resource.data
            else -> throw Exception(resource.errorOrNull() ?: "Failed to process document")
        }
    }
}