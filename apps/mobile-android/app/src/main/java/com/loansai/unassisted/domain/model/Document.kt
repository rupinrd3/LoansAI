package com.loansai.unassisted.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model for Document metadata
 */
data class Document(
    val id: String,
    val applicationId: String,
    val documentType: DocumentType,
    val fileType: FileType,
    val fileName: String,
    val fileSize: Long,
    val uploadedAt: LocalDateTime,
    val documentStatus: DocumentStatus = DocumentStatus.UPLOADED,
    val documentSourceType: DocumentSourceType = DocumentSourceType.MANUAL_ENTRY,
    val storageUrl: String? = null,
    val localUri: String? = null,
    val processingResult: DocumentProcessingResult? = null,
    val extractionStatus: ExtractionStatus = ExtractionStatus.NOT_ATTEMPTED,
    val extractedData: Map<String, Any>? = null
)

/**
 * Document processing result
 */
data class DocumentProcessingResult(
    val isProcessed: Boolean = false,
    val processedAt: LocalDateTime? = null,
    val processingMethod: ProcessingMethod? = null,
    val extractedFields: Map<String, String> = emptyMap(),
    val ocrConfidence: Float? = null,
    val extractionErrors: List<String> = emptyList()
)

/**
 * Document type enum
 */
enum class DocumentType {
    PAN_CARD,
    BANK_STATEMENT,
    SALARY_SLIP,
    INCOME_TAX_RETURN,
    FORM_26AS,
    ID_CARD,
    ADDRESS_PROOF,
    OTHER
}

/**
 * Document source type enum
 */
enum class DocumentSourceType {
    CAMERA_IMAGE,
    IMAGE_UPLOAD,
    PDF_UPLOAD,
    MANUAL_ENTRY
}

/**
 * File type enum
 */
enum class FileType {
    PDF,
    JPG,
    PNG,
    TIFF,
    OTHER
}

/**
 * Document status enum
 */
enum class DocumentStatus {
    UPLOADED,
    PROCESSING,
    PROCESSED,
    VERIFICATION_PENDING,
    VERIFIED,
    REJECTED,
    ERROR
}

/**
 * Extraction status enum
 */
enum class ExtractionStatus {
    NOT_ATTEMPTED,
    PENDING,
    SUCCESS,
    FAILURE
}

/**
 * Processing method enum
 */
enum class ProcessingMethod {
    ML_KIT_OCR,
    CLOUD_VISION_OCR,
    DOCUMENT_AI,
    DIGITAL_PDF_EXTRACTION,
    BACKEND_LLM_API,
    MANUAL
}

/**
 * Domain model for Bank Statement Data
 * Added in v1.5.0 for better document extraction support
 */
data class BankStatementData(
    val bankName: String? = null,
    val accountNumber: String? = null,
    val accountHolderName: String? = null,
    val statementPeriodStart: LocalDateTime? = null,
    val statementPeriodEnd: LocalDateTime? = null,
    val openingBalance: Double? = null,
    val closingBalance: Double? = null,
    val averageBalance: Double? = null,
    val totalCredits: Double? = null,
    val totalDebits: Double? = null,
    val transactionsCount: Int? = null,
    // Adding the missing fields from errors
    val statementPeriod: String? = null,
    val transactions: List<Transaction> = emptyList(),
    val salaryCredits: List<SalaryCredit> = emptyList()
)

// Add these additional classes if they don't exist:
data class Transaction(
    val date: LocalDateTime? = null,
    val description: String? = null,
    val amount: Double? = null,
    val type: String? = null,
    val balance: Double? = null,
    val isCredit: Boolean = false,
    val balanceAfter: Double? = null,
    val category: TransactionCategory? = null
)

/**
 * Salary credit model
 */
data class SalaryCredit(
    val date: LocalDate? = null,
    val amount: Double? = null,
    val description: String? = null
)

/**
 * Transaction category enum
 */
enum class TransactionCategory {
    SALARY,
    EMI,
    UTILITY,
    SHOPPING,
    TRANSFER,
    CASH_WITHDRAWAL,
    OTHER
}