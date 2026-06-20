package com.loansai.unassisted.service.ocr.impl

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.loansai.unassisted.domain.model.DocumentProcessingResult
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.OCRScanState
import com.loansai.unassisted.domain.model.ProcessingMethod
import com.loansai.unassisted.service.ImageResolution
import com.loansai.unassisted.service.DocumentProcessingService
import com.loansai.unassisted.service.ocr.OCRService
import com.loansai.unassisted.service.ocr.OCRServiceType
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ML Kit implementation of OCR Service
 */
class MLKitOCRService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentProcessingService: DocumentProcessingService
) : OCRService {
    
    // TextRecognizer instance
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    /**
    * Recognize text in an image using ML Kit
    */
    override fun recognizeText(imageUri: Uri): Flow<OCRScanState> = flow {
        emit(OCRScanState.Scanning)
        
        try {
            AppLogger.d("MLKitOCRService: Starting to process image from URI: $imageUri")
            
            // Check if the image needs preprocessing (compression, etc.)
            val processedUri = documentProcessingService.processImage(
                imageUri,
                ImageResolution.MEDIUM_2MP,
                4.0f
            )
            
            try {
                // Check if URI is accessible
                context.contentResolver.openInputStream(processedUri)?.use {
                    AppLogger.d("MLKitOCRService: Successfully opened input stream for URI")
                } ?: run {
                    AppLogger.e("MLKitOCRService: Could not open input stream for URI")
                    emit(OCRScanState.Error("Unable to access the image. The file may be missing or inaccessible."))
                    return@flow
                }
                
                val image = InputImage.fromFilePath(context, processedUri)
                AppLogger.d("MLKitOCRService: Created InputImage successfully")
                
                // Use suspendCoroutine to convert callback-based API to suspend function
                val result = suspendCancellableCoroutine<OCRScanState> { continuation ->
                    AppLogger.d("MLKitOCRService: Starting text recognition process")
                    
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            // Success result
                            val text = visionText.text
                            AppLogger.d("MLKitOCRService: Text recognition successful, extracted text length: ${text.length}")
                            
                            // Pass both the processed text and the original image URI back
                            continuation.resume(OCRScanState.Success(text, imageUri))
                        }
                        .addOnFailureListener { e ->
                            // Failure result
                            AppLogger.e("MLKitOCRService: Text recognition failed", e)
                            continuation.resume(OCRScanState.Error("Text recognition failed: ${e.message}"))
                        }
                }
                
                // Now we can emit the result
                emit(result)
                
            } catch (e: IOException) {
                // Handle I/O exceptions
                AppLogger.e("MLKitOCRService: Error processing image - IO Exception", e)
                emit(OCRScanState.Error("Error accessing the image: ${e.message}"))
            } catch (e: SecurityException) {
                AppLogger.e("MLKitOCRService: Security exception accessing image", e)
                emit(OCRScanState.Error("Security error accessing the image. Please check app permissions."))
            }
        } catch (e: Exception) {
            // Handle other exceptions
            AppLogger.e("MLKitOCRService: Unexpected error", e)
            emit(OCRScanState.Error("Unexpected error: ${e.message}"))
        }
    }
    
    /**
     * Extract specific fields from a document using ML Kit
     */
    override fun extractDocumentFields(
        imageUri: Uri,
        documentType: DocumentType
    ): Flow<Map<String, String>> = flow {
        try {
            // First compress/process the image if needed
            val processedUri = documentProcessingService.processImage(
                imageUri,
                ImageResolution.MEDIUM_2MP,
                4.0f
            )
            
            val image = InputImage.fromFilePath(context, processedUri)
            
            // Use suspendCancellableCoroutine to convert callback to suspendable function
            val extractedFields = suspendCancellableCoroutine<Map<String, String>> { continuation ->
                val fields = mutableMapOf<String, String>()
                
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        // Extract fields based on document type using enhanced extraction
                        when (documentType) {
                            DocumentType.PAN_CARD -> extractPANFields(visionText, fields)
                            DocumentType.BANK_STATEMENT -> extractBankStatementFields(visionText, fields)
                            DocumentType.SALARY_SLIP -> extractSalarySlipFields(visionText, fields)
                            DocumentType.ID_CARD -> extractIDCardFields(visionText, fields)
                            DocumentType.INCOME_TAX_RETURN -> extractITRFields(visionText, fields)
                            DocumentType.FORM_26AS -> extract26ASFields(visionText, fields)
                            else -> extractGeneralFields(visionText, fields)
                        }
                        
                        // Resume the coroutine with the result
                        continuation.resume(fields)
                    }
                    .addOnFailureListener { e ->
                        AppLogger.e("Field extraction failed", e)
                        continuation.resume(emptyMap())
                    }
            }
            
            // Now we can emit the result safely
            emit(extractedFields)
        } catch (e: IOException) {
            AppLogger.e("Error processing document", e)
            emit(emptyMap())
        } catch (e: Exception) {
            AppLogger.e("Unexpected error", e)
            emit(emptyMap())
        }
    }
    
    /**
     * Process a document image and extract all available information
     */
    override fun processDocument(
        imageUri: Uri,
        documentType: DocumentType
    ): Flow<DocumentProcessingResult> = flow {
        try {
            // First compress/process the image if needed
            val processedUri = documentProcessingService.processImage(
                imageUri,
                ImageResolution.MEDIUM_2MP,
                4.0f
            )
            
            val image = InputImage.fromFilePath(context, processedUri)
            
            // Use suspendCancellableCoroutine to convert callback to suspendable function
            val result = suspendCancellableCoroutine<DocumentProcessingResult> { continuation ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val extractedFields = mutableMapOf<String, String>()
                        
                        // Extract fields based on document type
                        when (documentType) {
                            DocumentType.PAN_CARD -> extractPANFields(visionText, extractedFields)
                            DocumentType.BANK_STATEMENT -> extractBankStatementFields(visionText, extractedFields)
                            DocumentType.SALARY_SLIP -> extractSalarySlipFields(visionText, extractedFields)
                            DocumentType.ID_CARD -> extractIDCardFields(visionText, extractedFields)
                            DocumentType.INCOME_TAX_RETURN -> extractITRFields(visionText, extractedFields)
                            DocumentType.FORM_26AS -> extract26ASFields(visionText, extractedFields)
                            else -> extractGeneralFields(visionText, extractedFields)
                        }
                        
                        // Calculate confidence score
                        val confidenceScore = calculateConfidenceScore(visionText, extractedFields)
                        
                        val processingResult = DocumentProcessingResult(
                            isProcessed = true,
                            processedAt = LocalDateTime.now(),
                            processingMethod = ProcessingMethod.ML_KIT_OCR,
                            extractedFields = extractedFields,
                            ocrConfidence = confidenceScore,
                            extractionErrors = emptyList()
                        )
                        
                        continuation.resume(processingResult)
                    }
                    .addOnFailureListener { e ->
                        val processingResult = DocumentProcessingResult(
                            isProcessed = false,
                            processedAt = LocalDateTime.now(),
                            processingMethod = ProcessingMethod.ML_KIT_OCR,
                            extractedFields = emptyMap(),
                            ocrConfidence = 0f,
                            extractionErrors = listOf(e.message ?: "Unknown error")
                        )
                        
                        continuation.resume(processingResult)
                    }
            }
            
            // Now we can emit the result safely
            emit(result)
        } catch (e: Exception) {
            val failResult = DocumentProcessingResult(
                isProcessed = false,
                processedAt = LocalDateTime.now(),
                processingMethod = ProcessingMethod.ML_KIT_OCR,
                extractedFields = emptyMap(),
                ocrConfidence = 0f,
                extractionErrors = listOf(e.message ?: "Unknown error")
            )
            
            emit(failResult)
        }
    }
    
    /**
     * Get the service type
     */
    override fun getServiceType(): OCRServiceType {
        return OCRServiceType.ML_KIT
    }
    
    /**
     * Extract PAN card fields - Enhanced implementation
     */
    private fun extractPANFields(visionText: Text, fields: MutableMap<String, String>) {
        val text = visionText.text
        
        // Extract PAN number using regex
        val panPattern = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]{1}")
        val panMatcher = panPattern.matcher(text)
        if (panMatcher.find()) {
            fields["PAN_NUMBER"] = panMatcher.group() ?: ""
        }
        
        // Extract name with improved logic
        val possibleNames = mutableListOf<String>()
        
        // First check for standard name patterns
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text.trim()
                
                // Check for lines with "Name" label
                if (lineText.contains("NAME", ignoreCase = true) && !lineText.equals("NAME", ignoreCase = true)) {
                    val nameParts = lineText.split(":", limit = 2)
                    if (nameParts.size > 1 && nameParts[1].trim().isNotEmpty()) {
                        possibleNames.add(nameParts[1].trim())
                    } else {
                        // If the name is on the next line after "NAME"
                        val blockText = block.text
                        val nameIndex = blockText.indexOf("NAME", ignoreCase = true) + "NAME".length
                        if (nameIndex < blockText.length) {
                            val nameCandidate = blockText.substring(nameIndex).trim()
                            if (nameCandidate.isNotEmpty() && !nameCandidate.startsWith(":")) {
                                possibleNames.add(nameCandidate)
                            }
                        }
                    }
                }
                
                // Check for lines in ALL CAPS that might be names (common in Indian government documents)
                if (lineText == lineText.uppercase() && 
                    lineText.length > 3 && 
                    !lineText.contains("PAN") && 
                    !lineText.contains("INCOME") && 
                    !lineText.contains("TAX") && 
                    !lineText.contains("PERMANENT")) {
                    possibleNames.add(lineText)
                }
            }
        }
        
        // Select the most likely name
        if (possibleNames.isNotEmpty()) {
            // Prefer names with at least two words (first and last name)
            val multiWordNames = possibleNames.filter { it.contains(" ") }
            if (multiWordNames.isNotEmpty()) {
                fields["NAME"] = multiWordNames.first()
            } else {
                fields["NAME"] = possibleNames.first()
            }
        }
        
        // Extract date of birth with improved patterns
        // Check for multiple date formats (DD/MM/YYYY, DD-MM-YYYY, etc.)
        val dobPatterns = listOf(
            "\\d{2}/\\d{2}/\\d{4}", // DD/MM/YYYY
            "\\d{2}-\\d{2}-\\d{4}", // DD-MM-YYYY
            "\\d{2}\\.\\d{2}\\.\\d{4}" // DD.MM.YYYY
        )
        
        for (pattern in dobPatterns) {
            val dobRegex = Pattern.compile(pattern)
            val dobMatcher = dobRegex.matcher(text)
            if (dobMatcher.find()) {
                fields["DATE_OF_BIRTH"] = dobMatcher.group() ?: ""
                break
            }
        }
        
        // Extract father's name if available
        for (block in visionText.textBlocks) {
            val blockText = block.text
            if (blockText.contains("FATHER", ignoreCase = true)) {
                // Try to extract father's name
                val fatherNameRegex = Pattern.compile("FATHER.*?[:;]\\s*([A-Z\\s]+)", Pattern.CASE_INSENSITIVE)
                val fatherNameMatcher = fatherNameRegex.matcher(blockText)
                
                if (fatherNameMatcher.find() && fatherNameMatcher.groupCount() >= 1) {
                    fields["FATHER_NAME"] = fatherNameMatcher.group(1).trim()
                }
            }
        }
    }
    
    /**
     * Extract bank statement fields - Enhanced implementation
     */
    private fun extractBankStatementFields(visionText: Text, fields: MutableMap<String, String>) {
        val text = visionText.text
        
        // First save the full text for backup
        fields["FULL_TEXT"] = text
        
        // Extract bank name
        val commonBanks = listOf(
            "STATE BANK OF INDIA", "SBI", 
            "HDFC BANK", "HDFC", 
            "ICICI BANK", "ICICI",
            "AXIS BANK", "AXIS",
            "PUNJAB NATIONAL BANK", "PNB",
            "BANK OF BARODA", "BOB",
            "KOTAK MAHINDRA BANK", "KOTAK",
            "IDFC FIRST BANK", "IDFC",
            "UNION BANK OF INDIA",
            "CANARA BANK",
            "INDIAN BANK",
            "YES BANK"
        )
        
        // Check first few lines for bank name
        for (block in visionText.textBlocks.take(5)) {
            for (bank in commonBanks) {
                if (block.text.contains(bank, ignoreCase = true)) {
                    fields["BANK_NAME"] = bank
                    break
                }
            }
            if (fields.containsKey("BANK_NAME")) break
        }
        
        // Extract account number with multiple patterns
        val accountPatterns = listOf(
            "A/C\\s+No\\.?\\s*:?\\s*(\\d+)",
            "ACCOUNT\\s+N[O0]\\.?\\s*:?\\s*(\\d+)",
            "ACCOUNT\\s+NUMBER\\s*:?\\s*(\\d+)",
            "A/C\\s*:?\\s*(\\d+)"
        )
        
        for (pattern in accountPatterns) {
            val accountRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val accountMatcher = accountRegex.matcher(text)
            if (accountMatcher.find() && accountMatcher.groupCount() >= 1) {
                fields["ACCOUNT_NUMBER"] = accountMatcher.group(1) ?: ""
                break
            }
        }
        
        // Extract account holder name
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text
                if ((lineText.contains("NAME", ignoreCase = true) && 
                    (lineText.contains("CUSTOMER", ignoreCase = true) || 
                     lineText.contains("ACCOUNT", ignoreCase = true))) ||
                    lineText.contains("A/C HOLDER", ignoreCase = true)) {
                    
                    val parts = lineText.split(":", limit = 2)
                    if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                        fields["ACCOUNT_HOLDER_NAME"] = parts[1].trim()
                        break
                    } else if (block.lines.size > block.lines.indexOf(line) + 1) {
                        // Name might be on the next line
                        val nextLine = block.lines[block.lines.indexOf(line) + 1].text.trim()
                        if (nextLine.isNotEmpty() && !nextLine.contains(":")) {
                            fields["ACCOUNT_HOLDER_NAME"] = nextLine
                            break
                        }
                    }
                }
            }
            if (fields.containsKey("ACCOUNT_HOLDER_NAME")) break
        }
        
        // Extract statement period with improved pattern
        val periodPatterns = listOf(
            "(?:STATEMENT|PERIOD)\\s+(?:FOR|FROM)\\s+(?:THE\\s+PERIOD|THE\\s+DURATION)?\\s*:?\\s*" +
            "(\\d{1,2}[/\\s.-]\\d{1,2}[/\\s.-]\\d{2,4})\\s*(?:TO|TILL|-|–)\\s*" +
            "(\\d{1,2}[/\\s.-]\\d{1,2}[/\\s.-]\\d{2,4})",
            
            "(\\d{1,2}[/\\s.-]\\d{1,2}[/\\s.-]\\d{2,4})\\s+TO\\s+(\\d{1,2}[/\\s.-]\\d{1,2}[/\\s.-]\\d{2,4})"
        )
        
        for (pattern in periodPatterns) {
            val periodRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val periodMatcher = periodRegex.matcher(text)
            if (periodMatcher.find() && periodMatcher.groupCount() >= 2) {
                fields["STATEMENT_PERIOD_START"] = periodMatcher.group(1) ?: ""
                fields["STATEMENT_PERIOD_END"] = periodMatcher.group(2) ?: ""
                break
            }
        }
        
        // Extract opening balance
        val openingBalancePatterns = listOf(
            "OPENING\\s+BALANCE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "BEGINNING\\s+BALANCE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in openingBalancePatterns) {
            val balanceRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val balanceMatcher = balanceRegex.matcher(text)
            if (balanceMatcher.find() && balanceMatcher.groupCount() >= 1) {
                fields["OPENING_BALANCE"] = balanceMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
        
        // Extract closing balance
        val closingBalancePatterns = listOf(
            "CLOSING\\s+BALANCE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "ENDING\\s+BALANCE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "BALANCE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in closingBalancePatterns) {
            val balanceRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val balanceMatcher = balanceRegex.matcher(text)
            if (balanceMatcher.find() && balanceMatcher.groupCount() >= 1) {
                fields["CLOSING_BALANCE"] = balanceMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
    }
    
    /**
     * Extract salary slip fields - Enhanced implementation
     */
    private fun extractSalarySlipFields(visionText: Text, fields: MutableMap<String, String>) {
        val text = visionText.text
        
        // First save the full text for backup
        fields["FULL_TEXT"] = text
        
        // Extract company name (usually at the top of the slip)
        val firstFewLines = visionText.textBlocks.take(3).joinToString("\n") { it.text }
        val companyNamePattern = Pattern.compile("^(.+)$", Pattern.MULTILINE)
        val companyNameMatcher = companyNamePattern.matcher(firstFewLines)
        if (companyNameMatcher.find()) {
            val potentialCompanyName = companyNameMatcher.group(1)?.trim() ?: ""
            if (potentialCompanyName.length > 3 && !potentialCompanyName.contains("SALARY", ignoreCase = true)) {
                fields["COMPANY_NAME"] = potentialCompanyName
            }
        }
        
        // Extract employee name with multiple patterns
        val employeeNamePatterns = listOf(
            "(?:EMPLOYEE|EMP)?\\s*NAME\\s*:?\\s*([A-Za-z\\s.]+)",
            "NAME\\s*:?\\s*([A-Za-z\\s.]+)"
        )
        
        for (pattern in employeeNamePatterns) {
            val nameRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val nameMatcher = nameRegex.matcher(text)
            if (nameMatcher.find() && nameMatcher.groupCount() >= 1) {
                val name = nameMatcher.group(1)?.trim() ?: ""
                if (name.length > 2) {
                    fields["EMPLOYEE_NAME"] = name
                    break
                }
            }
        }
        
        // If no name found by regex, try line-based extraction
        if (!fields.containsKey("EMPLOYEE_NAME")) {
            for (block in visionText.textBlocks) {
                for (line in block.lines) {
                    val lineText = line.text
                    if (lineText.contains("NAME", ignoreCase = true) && 
                        lineText.contains("EMPLOYEE", ignoreCase = true)) {
                        val parts = lineText.split(":", limit = 2)
                        if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                            fields["EMPLOYEE_NAME"] = parts[1].trim()
                            break
                        } else if (block.lines.size > block.lines.indexOf(line) + 1) {
                            // Name might be on the next line
                            fields["EMPLOYEE_NAME"] = block.lines[block.lines.indexOf(line) + 1].text.trim()
                            break
                        }
                    }
                }
                if (fields.containsKey("EMPLOYEE_NAME")) break
            }
        }
        
        // Extract employee ID
        val empIdPatterns = listOf(
            "(?:EMPLOYEE|EMP)\\s+(?:ID|CODE|NO)\\s*:?\\s*([A-Z0-9]+)",
            "(?:ID|CODE|NO)\\s*:?\\s*([A-Z0-9]+)"
        )
        
        for (pattern in empIdPatterns) {
            val empIdRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val empIdMatcher = empIdRegex.matcher(text)
            if (empIdMatcher.find() && empIdMatcher.groupCount() >= 1) {
                fields["EMPLOYEE_ID"] = empIdMatcher.group(1) ?: ""
                break
            }
        }
        
        // Extract salary month
        val monthPatterns = listOf(
            "(?:SALARY|PAY)\\s+(?:FOR|OF)\\s+(?:THE\\s+)?(?:MONTH|PERIOD)\\s*:?\\s*" +
            "(\\w+[\\s,]*\\d{4})",
            "(?:MONTH|PERIOD)\\s*:?\\s*(\\w+[\\s,]*\\d{4})"
        )
        
        for (pattern in monthPatterns) {
            val monthRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val monthMatcher = monthRegex.matcher(text)
            if (monthMatcher.find() && monthMatcher.groupCount() >= 1) {
                fields["SALARY_MONTH"] = monthMatcher.group(1) ?: ""
                break
            }
        }
        
        // Extract gross salary
        val grossPatterns = listOf(
            "GROSS\\s+(?:SALARY|PAY|EARNINGS)\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "TOTAL\\s+EARNINGS\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in grossPatterns) {
            val grossRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val grossMatcher = grossRegex.matcher(text)
            if (grossMatcher.find() && grossMatcher.groupCount() >= 1) {
                fields["GROSS_SALARY"] = grossMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
        
        // Extract net salary
        val netPatterns = listOf(
            "NET\\s+(?:SALARY|PAY|AMOUNT)\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "TAKE\\s+HOME\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "AMOUNT\\s+PAYABLE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in netPatterns) {
            val netRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val netMatcher = netRegex.matcher(text)
            if (netMatcher.find() && netMatcher.groupCount() >= 1) {
                fields["NET_SALARY"] = netMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
        
        // Extract total deductions
        val deductionPatterns = listOf(
            "TOTAL\\s+DEDUCTION[S]?\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in deductionPatterns) {
            val deductionRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val deductionMatcher = deductionRegex.matcher(text)
            if (deductionMatcher.find() && deductionMatcher.groupCount() >= 1) {
                fields["TOTAL_DEDUCTIONS"] = deductionMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
    }
    
    /**
     * Extract ID card fields
     */
    private fun extractIDCardFields(visionText: Text, fields: MutableMap<String, String>) {
        val text = visionText.text
        
        // First save the full text for backup
        fields["FULL_TEXT"] = text
        
        // Extract company name (usually at the top of the ID card)
        val firstFewLines = visionText.textBlocks.take(2).joinToString("\n") { it.text }
        fields["COMPANY_NAME"] = firstFewLines.split("\n").firstOrNull()?.trim() ?: ""
        
        // Extract employee name
        val namePatterns = listOf(
            "NAME\\s*:?\\s*([A-Za-z\\s.]+)",
            "(?:EMPLOYEE|EMP)?\\s*NAME\\s*:?\\s*([A-Za-z\\s.]+)"
        )
        
        for (pattern in namePatterns) {
            val nameRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val nameMatcher = nameRegex.matcher(text)
            if (nameMatcher.find() && nameMatcher.groupCount() >= 1) {
                fields["NAME"] = nameMatcher.group(1)?.trim() ?: ""
                break
            }
        }
        
        // Extract employee ID
        val empIdPatterns = listOf(
            "(?:EMPLOYEE|EMP)\\s+(?:ID|CODE|NO)\\s*:?\\s*([A-Z0-9]+)",
            "(?:ID|CODE|NO)\\s*:?\\s*([A-Z0-9]+)"
        )
        
        for (pattern in empIdPatterns) {
            val empIdRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val empIdMatcher = empIdRegex.matcher(text)
            if (empIdMatcher.find() && empIdMatcher.groupCount() >= 1) {
                fields["EMPLOYEE_ID"] = empIdMatcher.group(1) ?: ""
                break
            }
        }
        
        // Extract designation
        val designationPatterns = listOf(
            "DESIGNATION\\s*:?\\s*([A-Za-z\\s.]+)",
            "POSITION\\s*:?\\s*([A-Za-z\\s.]+)"
        )
        
        for (pattern in designationPatterns) {
            val designationRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val designationMatcher = designationRegex.matcher(text)
            if (designationMatcher.find() && designationMatcher.groupCount() >= 1) {
                fields["DESIGNATION"] = designationMatcher.group(1)?.trim() ?: ""
                break
            }
        }
        
        // Extract department
        val departmentPatterns = listOf(
            "DEPARTMENT\\s*:?\\s*([A-Za-z\\s.]+)",
            "DEPT\\s*:?\\s*([A-Za-z\\s.]+)"
        )
        
        for (pattern in departmentPatterns) {
            val departmentRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val departmentMatcher = departmentRegex.matcher(text)
            if (departmentMatcher.find() && departmentMatcher.groupCount() >= 1) {
                fields["DEPARTMENT"] = departmentMatcher.group(1)?.trim() ?: ""
                break
            }
        }
        
        // Extract blood group
        val bloodGroupRegex = Pattern.compile("BLOOD\\s+GROUP\\s*:?\\s*([ABO+-]+)", Pattern.CASE_INSENSITIVE)
        val bloodGroupMatcher = bloodGroupRegex.matcher(text)
        if (bloodGroupMatcher.find() && bloodGroupMatcher.groupCount() >= 1) {
            fields["BLOOD_GROUP"] = bloodGroupMatcher.group(1) ?: ""
        }
        
        // Extract validity or expiry date
        val validityPatterns = listOf(
            "VALID\\s+(?:TILL|UNTIL)\\s*:?\\s*(\\d{1,2}[/\\s.-]\\d{1,2}[/\\s.-]\\d{2,4})",
            "EXPIRY\\s+DATE\\s*:?\\s*(\\d{1,2}[/\\s.-]\\d{1,2}[/\\s.-]\\d{2,4})"
        )
        
        for (pattern in validityPatterns) {
            val validityRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val validityMatcher = validityRegex.matcher(text)
            if (validityMatcher.find() && validityMatcher.groupCount() >= 1) {
                fields["VALIDITY"] = validityMatcher.group(1) ?: ""
                break
            }
        }
    }
    
    /**
     * Extract Income Tax Return (ITR) fields
     */
    private fun extractITRFields(visionText: Text, fields: MutableMap<String, String>) {
        val text = visionText.text
        
        // First save the full text for backup
        fields["FULL_TEXT"] = text
        
        // Extract assessment year
        val assessmentYearRegex = Pattern.compile("ASSESSMENT\\s+YEAR\\s*:?\\s*(\\d{4}-\\d{2,4})", Pattern.CASE_INSENSITIVE)
        val assessmentYearMatcher = assessmentYearRegex.matcher(text)
        if (assessmentYearMatcher.find() && assessmentYearMatcher.groupCount() >= 1) {
            fields["ASSESSMENT_YEAR"] = assessmentYearMatcher.group(1) ?: ""
        }
        
        // Extract PAN
        val panPattern = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]{1}")
        val panMatcher = panPattern.matcher(text)
        if (panMatcher.find()) {
            fields["PAN_NUMBER"] = panMatcher.group() ?: ""
        }
        
        // Extract name
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text
                if (lineText.contains("NAME", ignoreCase = true) && 
                    !lineText.contains("FATHER", ignoreCase = true)) {
                    val parts = lineText.split(":", limit = 2)
                    if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                        fields["NAME"] = parts[1].trim()
                        break
                    } else if (block.lines.size > block.lines.indexOf(line) + 1) {
                        // Name might be on the next line
                        fields["NAME"] = block.lines[block.lines.indexOf(line) + 1].text.trim()
                        break
                    }
                }
            }
            if (fields.containsKey("NAME")) break
        }
        
        // Extract gross total income
        val grossIncomePatterns = listOf(
            "GROSS\\s+TOTAL\\s+INCOME\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "TOTAL\\s+INCOME\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in grossIncomePatterns) {
            val grossIncomeRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val grossIncomeMatcher = grossIncomeRegex.matcher(text)
            if (grossIncomeMatcher.find() && grossIncomeMatcher.groupCount() >= 1) {
                fields["GROSS_TOTAL_INCOME"] = grossIncomeMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
        
        // Extract total tax payable
        val taxPayablePatterns = listOf(
            "TOTAL\\s+TAX\\s+PAYABLE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "TAX\\s+PAYABLE\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in taxPayablePatterns) {
            val taxPayableRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val taxPayableMatcher = taxPayableRegex.matcher(text)
            if (taxPayableMatcher.find() && taxPayableMatcher.groupCount() >= 1) {
                fields["TOTAL_TAX_PAYABLE"] = taxPayableMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
        
        // Extract tax paid
        val taxPaidPatterns = listOf(
            "TAX\\s+PAID\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "TOTAL\\s+TAXES\\s+PAID\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in taxPaidPatterns) {
            val taxPaidRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val taxPaidMatcher = taxPaidRegex.matcher(text)
            if (taxPaidMatcher.find() && taxPaidMatcher.groupCount() >= 1) {
                fields["TAX_PAID"] = taxPaidMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
    }
    
    /**
     * Extract Form 26AS fields
     */
    private fun extract26ASFields(visionText: Text, fields: MutableMap<String, String>) {
        val text = visionText.text
        
        // First save the full text for backup
        fields["FULL_TEXT"] = text
        
        // Extract PAN
        val panPattern = Pattern.compile("[A-Z]{5}[0-9]{4}[A-Z]{1}")
        val panMatcher = panPattern.matcher(text)
        if (panMatcher.find()) {
            fields["PAN_NUMBER"] = panMatcher.group() ?: ""
        }
        
        // Extract name
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text
                if (lineText.contains("NAME", ignoreCase = true) && 
                    !lineText.contains("FATHER", ignoreCase = true)) {
                    val parts = lineText.split(":", limit = 2)
                    if (parts.size > 1 && parts[1].trim().isNotEmpty()) {
                        fields["NAME"] = parts[1].trim()
                        break
                    } else if (block.lines.size > block.lines.indexOf(line) + 1) {
                        // Name might be on the next line
                        fields["NAME"] = block.lines[block.lines.indexOf(line) + 1].text.trim()
                        break
                    }
                }
            }
            if (fields.containsKey("NAME")) break
        }
        
        // Extract financial year
        val financialYearRegex = Pattern.compile("(?:FINANCIAL|ASSESSMENT)\\s+YEAR\\s*:?\\s*(\\d{4}-\\d{2,4})", Pattern.CASE_INSENSITIVE)
        val financialYearMatcher = financialYearRegex.matcher(text)
        if (financialYearMatcher.find() && financialYearMatcher.groupCount() >= 1) {
            fields["FINANCIAL_YEAR"] = financialYearMatcher.group(1) ?: ""
        }
        
        // Extract TDS deductions
        val tdsPatterns = listOf(
            "TOTAL\\s+TDS\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "TDS\\s+DEDUCTED\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in tdsPatterns) {
            val tdsRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val tdsMatcher = tdsRegex.matcher(text)
            if (tdsMatcher.find() && tdsMatcher.groupCount() >= 1) {
                fields["TOTAL_TDS"] = tdsMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
        
        // Extract total salary
        val salaryPatterns = listOf(
            "GROSS\\s+SALARY\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)",
            "TOTAL\\s+SALARY\\s*:?\\s*(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)"
        )
        
        for (pattern in salaryPatterns) {
            val salaryRegex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val salaryMatcher = salaryRegex.matcher(text)
            if (salaryMatcher.find() && salaryMatcher.groupCount() >= 1) {
                fields["GROSS_SALARY"] = salaryMatcher.group(1)?.replace(",", "") ?: ""
                break
            }
        }
    }
    
    /**
     * Extract general document fields
     */
    private fun extractGeneralFields(visionText: Text, fields: MutableMap<String, String>) {
        // Default implementation - just store full text
        fields["FULL_TEXT"] = visionText.text
        
        // Try to extract any dates in the document
        val datePatterns = listOf(
            "\\d{2}/\\d{2}/\\d{4}", // DD/MM/YYYY
            "\\d{2}-\\d{2}-\\d{4}", // DD-MM-YYYY
            "\\d{2}\\.\\d{2}\\.\\d{4}" // DD.MM.YYYY
        )
        
        val dates = mutableListOf<String>()
        for (pattern in datePatterns) {
            val dateRegex = Pattern.compile(pattern)
            val dateMatcher = dateRegex.matcher(visionText.text)
            while (dateMatcher.find()) {
                dates.add(dateMatcher.group())
            }
        }
        
        if (dates.isNotEmpty()) {
            fields["DATES_FOUND"] = dates.joinToString(", ")
        }
        
        // Try to extract any monetary values in the document
        val amountRegex = Pattern.compile("(?:RS\\.?|₹)?\\s*(\\d+[,\\d]*\\.?\\d*)")
        val amountMatcher = amountRegex.matcher(visionText.text)
        val amounts = mutableListOf<String>()
        
        while (amountMatcher.find()) {
            amounts.add(amountMatcher.group().trim())
        }
        
        if (amounts.isNotEmpty()) {
            fields["AMOUNTS_FOUND"] = amounts.joinToString(", ")
        }
    }
    
    /**
     * Calculate confidence score from vision text blocks
     */
    private fun calculateConfidenceScore(visionText: Text, extractedFields: Map<String, String>): Float {
        // Base confidence from ML Kit (approximating since ML Kit doesn't provide direct confidence)
        var baseConfidence = 0.8f
        
        // Adjust based on number of extracted fields (more fields = higher confidence)
        val fieldConfidence = when {
            extractedFields.size > 5 -> 0.9f
            extractedFields.size > 3 -> 0.8f
            extractedFields.size > 1 -> 0.7f
            else -> 0.5f
        }
        
        // Adjust based on text block recognition quality
        var blockQualitySum = 0f
        var blockCount = 0
        
        for (block in visionText.textBlocks) {
            // Calculate quality based on line consistency, character spacing, etc.
            val lineCount = block.lines.size
            val linesWithDates = block.lines.count { 
                it.text.matches(Regex(".*\\d{1,2}[/\\-.]\\d{1,2}[/\\-.]\\d{2,4}.*")) 
            }
            val linesWithAmounts = block.lines.count { 
                it.text.matches(Regex(".*\\d+[,\\d]*\\.?\\d*.*")) 
            }
            
            // More structured data (dates, amounts) indicates higher quality recognition
            val blockQuality = if (lineCount > 0) {
                0.7f + (0.1f * (linesWithDates.toFloat() / lineCount)) + (0.1f * (linesWithAmounts.toFloat() / lineCount))
            } else {
                0.7f
            }
            
            blockQualitySum += blockQuality
            blockCount++
        }
        
        val avgBlockQuality = if (blockCount > 0) blockQualitySum / blockCount else 0.7f
        
        // Combine different confidence metrics
        return (baseConfidence * 0.4f) + (fieldConfidence * 0.4f) + (avgBlockQuality * 0.2f)
    }
}