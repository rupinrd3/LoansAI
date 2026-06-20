package com.loansai.unassisted.service.ai.impl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.*



import com.loansai.unassisted.BuildConfig
// ... other imports ...
import org.json.JSONArray
import org.json.JSONObject // Import JSONObject
import com.loansai.unassisted.domain.model.TradelineItem
import java.io.ByteArrayOutputStream
import java.io.IOException
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentType
import com.loansai.unassisted.domain.model.FileType
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Service for processing documents using Gemini AI (Multimodal Version)
 */
@Singleton
class GeminiDocumentService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Use Gemini 1.5 Flash as it's generally good for multimodal tasks
    val modelName = "gemini-2.5-flash-preview-04-17"
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val geminiModel by lazy {
        GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )
    }

    /**
     * Process a document using Gemini AI (handles images and PDFs)
     */
    suspend fun processDocument(document: Document): Map<String, Any> = withContext(Dispatchers.IO) {
        AppLogger.d("Starting Gemini document processing for document: ${document.id}, Type: ${document.fileType}")

        // --- Get File Data Part ---
        val fileDataPart: Part? = try {
            getFileDataPart(document)
        } catch (e: IOException) {
            AppLogger.e("Error reading file data for document ${document.id}: ${e.message}", e)
            return@withContext mapOf(
                "error" to "Failed to read document file: ${e.message}",
                "success" to false
            )
        } catch (e: Exception) {
            AppLogger.e("Error preparing file data part for document ${document.id}: ${e.message}", e)
            return@withContext mapOf(
                "error" to "Failed prepare file data: ${e.message}",
                "success" to false
            )
        }

        if (fileDataPart == null) {
            AppLogger.e("Could not get file data part for document ${document.id} (URI: ${document.localUri})")
            return@withContext mapOf(
                "error" to "Could not read document file.",
                "success" to false
            )
        }

        // --- Build Prompt ---
        val textPromptPart = content {
            text(buildPromptForDocument(document))
        }.parts.first() // Get the text part from the content builder

        // --- Call Gemini API ---
        try {
            AppLogger.d("Sending multimodal request to Gemini model $modelName")

            // Combine file data part and text prompt part
            val response = geminiModel.generateContent(
                content {
                    part(fileDataPart) // Send the actual file data
                    part(textPromptPart) // Send your instructions
                }
            )

            // --- Process Response ---
            val responseText = response.text ?: ""
            AppLogger.d("Received response from Gemini: ${responseText.take(200)}...")

            return@withContext parseJsonFromResponse(responseText, document.documentType)

        } catch (e: Exception) {
            AppLogger.e("Error processing document ${document.id} with Gemini: ${e.message}", e)
            // Log the specific error from Gemini if available (e.g., blocked content)
            if (e.message?.contains("response was blocked") == true) {
                 AppLogger.e("Gemini Response Blocked: Potentially harmful content or safety settings.")
            } else if (e is kotlinx.serialization.SerializationException || e.message?.contains("json", ignoreCase = true) == true) {
                 AppLogger.e("Gemini response format error (expected JSON): ${e.message}")
            }
            return@withContext mapOf(
                "error" to "AI processing error: ${e.message}",
                "success" to false
            )
        }
    }

    /**
     * Creates the appropriate Gemini SDK Part (image or blob) based on the document type.
     */
    private fun getFileDataPart(document: Document): Part? {
        val uriString = document.localUri ?: return null
        val uri = Uri.parse(uriString)

        return when (document.fileType) {
            FileType.JPG, FileType.PNG -> {
                AppLogger.d("Preparing Image Part for Gemini from URI: $uri")
                val bitmap = loadBitmapFromUri(uri) // Load bitmap (could optimize size)
                bitmap?.let { 
                    // Create image Part using the correct method from Gemini SDK
                    com.google.ai.client.generativeai.type.content { image(it) }.parts.first() 
                }
            }
            FileType.PDF -> {
                AppLogger.d("Preparing PDF Blob Part for Gemini from URI: $uri")
                val pdfBytes = readBytesFromUri(uri)
                pdfBytes?.let { 
                    com.google.ai.client.generativeai.type.content { 
                        text("") // Empty text part
                        // Use the correct parameter names for the blob method
                        blob("application/pdf", it)  // Uses (mimeType, data) parameters
                    }.parts.last() // Get the blob part
                }
            }
            else -> {
                AppLogger.w("Unsupported file type for Gemini processing: ${document.fileType}")
                null // Or handle other types if Gemini supports them
            }
        }
    }

    /**
     * Loads a Bitmap from a Uri. Includes basic resizing/compression for very large images.
     */
    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Basic check for large images - decode bounds first
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close() // Close the first stream

                // Reopen stream to decode actual bitmap
                context.contentResolver.openInputStream(uri)?.use { streamForBitmap ->
                    val calculateSampleSize = {
                         var sampleSize = 1
                         // Simple resizing if image is huge (e.g., > 4MP)
                         if (options.outWidth * options.outHeight > 4_000_000) {
                              val halfHeight: Int = options.outHeight / 2
                              val halfWidth: Int = options.outWidth / 2
                              while (halfHeight / sampleSize >= 1024 && halfWidth / sampleSize >= 1024) {
                                    sampleSize *= 2
                              }
                         }
                         sampleSize
                    }

                    val bitmapOptions = BitmapFactory.Options().apply { inSampleSize = calculateSampleSize() }
                    val bitmap = BitmapFactory.decodeStream(streamForBitmap, null, bitmapOptions)

                    // Optionally compress further if needed (e.g., if still too large for API)
                    // val outputStream = ByteArrayOutputStream()
                    // bitmap?.compress(Bitmap.CompressFormat.JPEG, 85, outputStream) // Compress to 85% quality
                    // val compressedBytes = outputStream.toByteArray()
                    // BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                    bitmap // Return the potentially downsampled bitmap
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Error loading bitmap from URI $uri: ${e.message}", e)
            null
        }
    }

    /**
     * Reads all bytes from a Uri.
     */
    private fun readBytesFromUri(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            }
        } catch (e: Exception) {
            AppLogger.e("Error reading bytes from URI $uri: ${e.message}", e)
            null
        }
    }


    /**
     * Build a prompt specific to the document type.
     * (Prompts remain largely the same as they instruct Gemini on what to extract from the content it receives)
     */
    private fun buildPromptForDocument(document: Document): String {
        // Use the same prompts as before, they instruct Gemini what to look for
        // in the provided image/PDF content.
        return when (document.documentType) {
            DocumentType.BANK_STATEMENT -> """
                You are an AI assistant specialized in extracting information from bank statements. Analyze the provided document content (image or PDF).

                Please extract the following information in JSON format:
                - bankName: The name of the bank (String)
                - accountNumber: The account number (String, mask all but last 4 digits if present)
                - accountHolderName: The name of the account holder (String)
                - statementPeriodStart: The start date of the statement period (String, format: YYYY-MM-DD)
                - statementPeriodEnd: The end date of the statement period (String, format: YYYY-MM-DD)
                - openingBalance: The opening balance amount (Number, return only numeric value, no currency symbols/commas)
                - closingBalance: The closing balance amount (Number, return only numeric value, no currency symbols/commas)
                - totalCredits: The total amount credited during the period (Number, return only numeric value, no currency symbols/commas)
                - totalDebits: The total amount debited during the period (Number, return only numeric value, no currency symbols/commas)

                If a specific field cannot be found in the document, use a JSON null value for that field.
                IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
            """.trimIndent()

            DocumentType.SALARY_SLIP -> """
                You are an AI assistant specialized in extracting information from salary slips. Analyze the provided document content (image or PDF).

                Please extract the following information in JSON format:
                - employeeName: The name of the employee
                - employeeId: The employee ID
                - companyName: The name of the company
                - month: The month of the salary slip (String, format: YYYY-MM-DD, representing the pay period end or month start)
                - grossSalary: The gross salary amount (Number, return only numeric value, no currency symbols/commas)
                - netSalary: The net salary amount (Number, return only numeric value, no currency symbols/commas)
                - totalDeductions: The total deductions amount (Number, return only numeric value, no currency symbols/commas)
                - basicSalary: The basic salary component (Number, return only numeric value, no currency symbols/commas)

                If a specific field cannot be found in the document, use a JSON null value for that field.
                IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
            """.trimIndent()

            DocumentType.INCOME_TAX_RETURN -> """
                You are an AI assistant specialized in extracting information from income tax returns. Analyze the provided document content (image or PDF).

                Please extract the following information in JSON format:
                - name: The taxpayer's name
                - panNumber: The PAN number (mask if needed)
                - assessmentYear: The assessment year (format: YYYY-YY)
                - filingDate: The filing date (format: YYYY-MM-DD)
                - grossTotalIncome: The gross total income (Number, return only numeric value, no currency symbols/commas)
                - totalDeductions: The total deductions (Number, return only numeric value, no currency symbols/commas)
                - taxableIncome: The taxable income (Number, return only numeric value, no currency symbols/commas)
                - taxPayable: The tax payable (Number, return only numeric value, no currency symbols/commas)

                If a specific field cannot be found in the document, use a JSON null value for that field.
                IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
            """.trimIndent()

            DocumentType.FORM_26AS -> """
                You are an AI assistant specialized in extracting information from Form 26AS. Analyze the provided document content (image or PDF).

                Please extract the following information in JSON format:
                - name: The taxpayer's name
                - panNumber: The PAN number (mask if needed)
                - assessmentYear: The assessment year (format: YYYY-YY)
                - totalTdsDeducted: The total TDS deducted (Number, return only numeric value, no currency symbols/commas)
                - totalTdsDeposited: The total TDS deposited (Number, return only numeric value, no currency symbols/commas)

                If a specific field cannot be found in the document, use a JSON null value for that field.
                IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
            """.trimIndent()
             DocumentType.ID_CARD -> """
                 You are an AI assistant specialized in extracting information from ID cards. Analyze the provided document content (image or PDF).

                 Please extract the following information in JSON format:
                 - companyName: The name of the company/organization (String)
                 - employeeName: The name of the employee/cardholder (String)
                 - employeeId: The employee ID or number (String)
                 - designation: The job title or designation (String)
                 - department: The department (String)
                 - validity: The validity or expiry date (String, format: YYYY-MM-DD or similar)

                 If a specific field cannot be found in the document, use a JSON null value for that field.
                 IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
             """.trimIndent()

             DocumentType.PAN_CARD -> """
                 You are an AI assistant specialized in extracting information from PAN cards. Analyze the provided document content (image or PDF).

                 Please extract the following information in JSON format:
                 - name: The name of the cardholder (String)
                 - fatherName: The father's name (String)
                 - dateOfBirth: The date of birth (String, format: DD/MM/YYYY)
                 - panNumber: The PAN number (String, format: ABCDE1234F)

                 If a specific field cannot be found in the document, use a JSON null value for that field.
                 IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
             """.trimIndent()

            else -> """
                 You are an AI assistant specialized in extracting information from documents. Analyze the provided document content (image or PDF).

                 Please extract all relevant information from this document and return it in a structured JSON format.
                 Focus on key fields such as names, dates, amounts, addresses, identifiers, etc.

                 If a specific field cannot be found in the document, use a JSON null value for that field.
                 IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
             """.trimIndent()
        }
    }

    /**
     * Parse JSON from Gemini response
     * (This function remains the same as before)
     */
    private fun parseJsonFromResponse(response: String, documentType: DocumentType): Map<String, Any> {
        try {
            val jsonStart = response.indexOfFirst { it == '{' }
            val jsonEnd = response.indexOfLast { it == '}' }

            if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
                val jsonString = response.substring(jsonStart, jsonEnd + 1)
                AppLogger.d("Extracted JSON string: $jsonString")

                val jsonObject = JSONObject(jsonString)
                val resultMap = convertJsonToMap(jsonObject).toMutableMap()

                resultMap["documentType"] = documentType.name
                resultMap["success"] = true

                AppLogger.d("Successfully parsed JSON response from Gemini.")
                return resultMap

            } else {
                AppLogger.w("Could not find valid JSON object in Gemini response.")
                return mapOf(
                    "success" to false,
                    "error" to "Could not parse JSON object from response",
                    "rawResponse" to response,
                    "documentType" to documentType.name
                )
            }
        } catch (e: Exception) {
            AppLogger.e("Error parsing JSON from Gemini response: ${e.message}", e)
            return mapOf(
                "success" to false,
                "error" to "Error parsing JSON response: ${e.message}",
                "rawResponse" to response,
                "documentType" to documentType.name
            )
        }
    }

    // Helper functions to convert JSONObjects/Arrays to Maps/Lists
    // (These functions remain the same as before)
    private fun convertJsonValue(value: Any?): Any? {
        return when (value) {
            is JSONObject -> convertJsonToMap(value)
            is JSONArray -> convertJsonToList(value)
            JSONObject.NULL -> null // Handle JSON null
            else -> value
        }
    }

    private fun convertJsonToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = convertJsonValue(jsonObject.opt(key))
            if (value != null) {  // Only add non-null values
                map[key] = value
            }
        }
        return map  // Returns immutable Map<String, Any>
    }


     private fun convertJsonToList(jsonArray: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (i in 0 until jsonArray.length()) {
            list.add(convertJsonValue(jsonArray.opt(i))) // Use opt for safety
        }
        return list
    }

    // Recalculate Obligation function (remains the same, as it takes structured data)
    suspend fun recalculateObligation(
        tradelines: List<TradelineItem>,
        userProvidedEmis: Map<String, Int>,
        userComments: String?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        // Log input parameters
        AppLogger.d("Starting Gemini obligation recalculation with ${tradelines.size} tradelines")
        AppLogger.d("User provided EMIs: ${userProvidedEmis.entries.joinToString { "${it.key}=${it.value}" }}")
        AppLogger.d("User comments: ${userComments ?: "None provided"}")
        
        // Check for empty inputs - early exit if no data
        if (tradelines.isEmpty()) {
            AppLogger.w("No tradelines provided for recalculation, returning error")
            return@withContext mapOf(
                "success" to false,
                "error" to "No tradelines available for recalculation"
            )
        }
        
        try {
            // Build prompt with detailed logging
            AppLogger.d("Building obligation prompt")
            val prompt = buildObligationPrompt(tradelines, userProvidedEmis, userComments)
            AppLogger.d("Prompt built successfully, length: ${prompt.length}")
            AppLogger.d("Sending request to Gemini model for obligation recalculation")
            
            // Add timeout to Gemini API call - 30 seconds
            val response = withTimeoutOrNull(30000L) {
                AppLogger.d("Executing Gemini API call...")
                geminiModel.generateContent(content { text(prompt) })
            }
            
            // Check if timeout occurred
            if (response == null) {
                AppLogger.e("Gemini API call timed out after 30 seconds")
                return@withContext mapOf(
                    "success" to false,
                    "error" to "Timeout while waiting for Gemini response"
                )
            }
            
            // Check for null response text
            val responseText = response.text
            if (responseText == null) {
                AppLogger.e("Gemini returned null response text")
                return@withContext mapOf(
                    "success" to false,
                    "error" to "Gemini returned empty response"
                )
            }
            
            AppLogger.d("Received response from Gemini: ${responseText.take(200)}...")
            
            // Parse the response and check for valid JSON structure
            val result = parseJsonFromResponse(responseText, DocumentType.OTHER)
            AppLogger.d("Parsed Gemini response: $result")
            
            return@withContext result
        } catch (e: Exception) {
            // Detailed error logging with full stack trace
            AppLogger.e("Error recalculating obligation with Gemini: ${e.message}")
            e.printStackTrace()
            
            return@withContext mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error during recalculation")
            )
        }
    }
    // Build Obligation Prompt function (remains the same)
    private fun buildObligationPrompt(
         tradelines: List<TradelineItem>,
         userProvidedEmis: Map<String, Int>,
         userComments: String?
     ): String {
         val tradelineDetails = tradelines.joinToString("\n") { tl ->
             val userEmi = userProvidedEmis[tl.id]
             val reportedEmi = tl.emiAmount.takeIf { it > 0 }
             "- ID: ${tl.id}, Lender: ${tl.memberName}, Type: ${tl.accountType}, Balance: ${tl.currentBalance}, Reported EMI: ${reportedEmi ?: "N/A"}, User Provided EMI: ${userEmi ?: "N/A"}"
         }

         val commentsSection = if (!userComments.isNullOrBlank()) {
             """
             User Comments for context:
             $userComments
             """
         } else {
             "User provided no additional comments."
         }

         return """
             You are an AI assistant analyzing loan obligations for loan underwriting.
             Based on the provided bureau tradelines and user-confirmed/corrected EMI values, calculate the total refined monthly obligation.

             Rules:
             1. Sum the 'User Provided EMI' for all active loans listed.
             2. Analyze the 'User Comments'. If a comment clearly indicates a specific loan (by ID or Lender/Type) is closed, paid off, or should otherwise be excluded, list that loan ID and the reason in the 'excludedLoans' array. Do NOT include excluded loans in the total obligation sum.
             3. If the user didn't provide an EMI for a loan, try to use the 'Reported EMI' if available and reasonable, unless comments suggest otherwise. If neither is available or reliable, you may exclude it with a reason.
             4. Prioritize the 'User Provided EMI' over the 'Reported EMI'.

             Provided Tradeline Data:
             $tradelineDetails

             $commentsSection

             Output Format:
             Return ONLY a valid JSON object with the following structure:
             {
               "recalculatedObligation": <Total calculated monthly obligation amount (Number)>,
               "excludedLoans": [ // List of loans excluded from the sum
                 { "tradelineId": "<ID of excluded loan>", "reason": "<Brief reason for exclusion based on comments or missing data>" }
               ]
             }

             Example Output:
             {
               "recalculatedObligation": 25500,
               "excludedLoans": [
                 { "tradelineId": "trd_123", "reason": "User stated loan is closed." },
                 { "tradelineId": "trd_456", "reason": "Missing reliable EMI information." }
               ]
             }

             IMPORTANT: Your entire response must be ONLY the JSON object, starting with { and ending with }, with no introductory text, explanations, apologies, or markdown formatting.
         """.trimIndent()
     }
}