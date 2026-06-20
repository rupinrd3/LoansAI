package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName
import com.loansai.unassisted.domain.model.BankStatementData
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentProcessingResult
import com.loansai.unassisted.domain.model.DocumentStatus
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.domain.model.ProcessingMethod
import com.loansai.unassisted.domain.model.SalaryCredit
import com.loansai.unassisted.domain.model.Transaction
import com.loansai.unassisted.domain.model.TransactionCategory
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Data Transfer Object for Document
 */
data class DocumentDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("application_id")
    val applicationId: String,
    
    @SerializedName("document_type")
    val documentType: String,
    
    @SerializedName("file_type")
    val fileType: String,
    
    @SerializedName("file_name")
    val fileName: String,
    
    @SerializedName("file_size")
    val fileSize: Long,
    
    @SerializedName("uploaded_at")
    val uploadedAt: String,
    
    @SerializedName("document_status")
    val documentStatus: String = "UPLOADED",
    
    @SerializedName("storage_url")
    val storageUrl: String? = null,
    
    @SerializedName("processing_result")
    val processingResult: DocumentProcessingResultDto? = null
)

/**
 * Data Transfer Object for Document Processing Result
 */
data class DocumentProcessingResultDto(
    @SerializedName("is_processed")
    val isProcessed: Boolean = false,
    
    @SerializedName("processed_at")
    val processedAt: String? = null,
    
    @SerializedName("processing_method")
    val processingMethod: String? = null,
    
    @SerializedName("extracted_fields")
    val extractedFields: Map<String, String> = emptyMap(),
    
    @SerializedName("ocr_confidence")
    val ocrConfidence: Float? = null,
    
    @SerializedName("extraction_errors")
    val extractionErrors: List<String> = emptyList()
)

/**
 * Data Transfer Object for Bank Statement Data
 */
data class BankStatementDataDto(
    @SerializedName("bank_name")
    val bankName: String? = null,

    @SerializedName("account_number")
    val accountNumber: String? = null,

    @SerializedName("account_holder_name")
    val accountHolderName: String? = null,

    @SerializedName("statement_period_start")
    val statementPeriodStart: String? = null,

    @SerializedName("statement_period_end")
    val statementPeriodEnd: String? = null,

    @SerializedName("transactions")
    val transactions: List<TransactionDto> = emptyList(),

    @SerializedName("average_balance")
    val averageBalance: Double? = null,

    @SerializedName("closing_balance")
    val closingBalance: Double? = null,

    @SerializedName("total_credits")
    val totalCredits: Double? = null,

    @SerializedName("total_debits")
    val totalDebits: Double? = null,

    @SerializedName("salary_credits")
    val salaryCredits: List<SalaryCreditDto> = emptyList(),

    // +++ Add this missing field +++
    @SerializedName("statement_period") // Assuming a single string field might be received
    val statementPeriod: String? = null
)

/**
 * Data Transfer Object for Transaction
 */
data class TransactionDto(
    @SerializedName("date")
    val date: String? = null,
    
    @SerializedName("description")
    val description: String? = null,
    
    @SerializedName("amount")
    val amount: Double? = null,
    
    @SerializedName("is_credit")
    val isCredit: Boolean = false,
    
    @SerializedName("balance_after")
    val balanceAfter: Double? = null,
    
    @SerializedName("category")
    val category: String? = null
)

/**
 * Data Transfer Object for Salary Credit
 */
data class SalaryCreditDto(
    @SerializedName("date")
    val date: String? = null,
    
    @SerializedName("amount")
    val amount: Double? = null,
    
    @SerializedName("description")
    val description: String? = null
)

/**
 * Document upload and processing request models
 */
data class UploadDocumentRequest(
    @SerializedName("application_id")
    val applicationId: String,
    
    @SerializedName("document_type")
    val documentType: String,
    
    @SerializedName("file_type")
    val fileType: String,
    
    @SerializedName("file_name")
    val fileName: String,
    
    @SerializedName("file_size")
    val fileSize: Long
)

data class DocumentProcessingRequest(
    @SerializedName("document_id")
    val documentId: String,
    
    @SerializedName("processing_method")
    val processingMethod: String? = null
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun DocumentDto.toDocument(localUri: String? = null): Document {
    return Document(
        id = id,
        applicationId = applicationId,
        documentType = DocumentType.valueOf(documentType),
        fileType = FileType.valueOf(fileType),
        fileName = fileName,
        fileSize = fileSize,
        uploadedAt = LocalDateTime.parse(uploadedAt),
        documentStatus = DocumentStatus.valueOf(documentStatus),
        storageUrl = storageUrl,
        localUri = localUri,
        processingResult = processingResult?.toDocumentProcessingResult()
    )
}

/**
 * Mapping extension function to convert DTO to domain model
 */
fun DocumentProcessingResultDto.toDocumentProcessingResult(): DocumentProcessingResult {
    return DocumentProcessingResult(
        isProcessed = isProcessed,
        processedAt = processedAt?.let { LocalDateTime.parse(it) },
        processingMethod = processingMethod?.let { ProcessingMethod.valueOf(it) },
        extractedFields = extractedFields,
        ocrConfidence = ocrConfidence,
        extractionErrors = extractionErrors
    )
}



/**
 * Mapping extension function to convert DTO to domain model
 */
fun BankStatementDataDto.toBankStatementData(): BankStatementData {
    // --- Modify how statementPeriod is handled ---
    val formattedPeriod = statementPeriod ?: // Use direct field if available
        if (statementPeriodStart != null && statementPeriodEnd != null) {
             "$statementPeriodStart to $statementPeriodEnd" // Combine start/end if separate
         } else {
             null // Otherwise, it's null
         }

    return BankStatementData(
        bankName = bankName,
        accountNumber = accountNumber,
        accountHolderName = accountHolderName,
        statementPeriod = formattedPeriod, // Assign the formatted string
        transactions = transactions.map { it.toTransaction() },
        averageBalance = averageBalance,
        closingBalance = closingBalance,
        totalCredits = totalCredits,
        totalDebits = totalDebits,
        salaryCredits = salaryCredits.map { it.toSalaryCredit() }
        // Ensure transactions and salaryCredits are handled correctly if they were causing issues
    )
}

/**
 * Mapping extension function to convert Transaction DTO to domain model
 */
fun TransactionDto.toTransaction(): Transaction {
    return Transaction(
        // Convert LocalDate to LocalDateTime (using start of day)
        date = date?.let {
            try {
                 LocalDate.parse(it).atStartOfDay() // <-- Convert to LocalDateTime
            } catch (e: Exception) {
                 null // Handle parsing errors
            }
        },
        description = description,
        amount = amount,
        isCredit = isCredit,
        balanceAfter = balanceAfter,
        category = category?.let {
            try {
                TransactionCategory.valueOf(it)
            } catch (e: IllegalArgumentException) {
                TransactionCategory.OTHER
            }
        }
        // Ensure the Transaction model in Document.kt expects LocalDateTime?
        // data class Transaction( ..., val date: LocalDateTime? = null, ... )
    )
}

/**
 * Mapping extension function to convert Salary Credit DTO to domain model
 */
fun SalaryCreditDto.toSalaryCredit(): SalaryCredit {
    return SalaryCredit(
        date = date?.let { LocalDate.parse(it) },
        amount = amount,
        description = description
    )
}