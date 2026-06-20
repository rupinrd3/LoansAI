package com.loansai.unassisted.domain.usecase.ai.impl

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.google.gson.Gson
import com.loansai.unassisted.BuildConfig
import com.loansai.unassisted.data.remote.api.AIApi
import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.DocumentStatus
import com.loansai.unassisted.domain.model.EmploymentType
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.MessageType
import com.loansai.unassisted.domain.model.OCRScanState
import com.loansai.unassisted.service.ai.AIAssistantService
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AI assistant service using OpenAI API
 * Updated for v1.5.0 to reflect backend LLM API architecture
 */
@Singleton
class GPTAIAssistantService @Inject constructor(
    private val aiApi: AIApi,
    private val openAI: OpenAI,
    private val gson: Gson
) : AIAssistantService {
    
    // Default model to use for direct OpenAI calls
    private val defaultModel = ModelId("gpt-4o-mini")
    
    // System prompt that sets the behavior of the AI assistant
    private val baseSystemPrompt = """
        You are a helpful AI assistant for a loan application app called LoansAI. 
        Your purpose is to assist users with their loan application process.
        
        Here are some guidelines:
        1. Be concise and clear in your responses.
        2. Provide accurate information about the loan application process.
        3. Respect user privacy - never ask for sensitive information like actual PAN numbers, bank details, etc.
        4. When asked about issues with the application, try to provide troubleshooting tips.
        5. If you're not sure about something, admit it and suggest the user contact customer support.
        6. Focus on being helpful and providing accurate guidance for the Indian loan application context.
        
        The application has the following main screens:
        - Login: Phone verification with OTP
        - Home: Starting point showing options to create/resume applications
        - PAN Entry: Where users enter their PAN card details
        - Personal Info: Where users enter personal information
        - Employment Details: Where users enter employment and salary details
        - Document Upload: Where users upload required documents
        - Bureau Confirmation: Where users confirm their bureau data and EMIs
        - Loan Offer: Where users can view their approved loan amount
        - Employment Verification: Where employment details are verified
        - Key Fact Sheet: Final review before submission
        - Application Complete: Confirmation of submission
        
        Only provide information relevant to the user's current screen and query.
    """.trimIndent()
    
    /**
     * Gets contextual assistance based on the current screen
     */
    override suspend fun getContextualAssistance(
        screen: String,
        context: Map<String, Any>,
        application: LoanApplication?
    ): AIAssistantMessage = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("Getting contextual assistance for screen: $screen")
            
            // Prepare enhanced context
            val enhancedContext = buildEnhancedContext(screen, context, application)
            
            // Create prompt with screen-specific hints
            val screenSpecificPrompt = getScreenSpecificPrompt(screen)
            
            // Build full system prompt
            val fullSystemPrompt = """
                $baseSystemPrompt
                
                $screenSpecificPrompt
                
                Current screen: $screen
                
                Context information:
                ${formatContextForPrompt(enhancedContext)}
            """.trimIndent()
            
            // User query for contextual assistance
            val userQuery = "Show me helpful information for the $screen screen based on my current situation."
            
            // Get response from OpenAI
            val chatCompletion = getOpenAIResponse(fullSystemPrompt, userQuery)
            
            // Create and return AI assistant message
            return@withContext AIAssistantMessage(
                id = UUID.randomUUID().toString(),
                applicationId = application?.id,
                timestamp = LocalDateTime.now(),
                messageType = MessageType.AI_RESPONSE,
                message = chatCompletion?.choices?.firstOrNull()?.message?.content
                    ?: "I can assist you with your loan application process. What would you like to know?",
                screen = screen
            )
        } catch (e: Exception) {
            AppLogger.e("Error getting contextual assistance", e)
            
            // Return fallback message on error
            return@withContext createErrorMessage(
                "I'm having trouble connecting right now. Please try again later.",
                application?.id,
                screen
            )
        }
    }

    /**
     * Gets feedback on document quality
     * Note: This is separate from backend LLM document processing
     */
    override suspend fun getDocumentQualityFeedback(document: Document): AIAssistantMessage = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("Getting document quality feedback for document type: ${document.documentType}")
            
            // Create context for document analysis
            val documentContext = mapOf(
                "document_type" to document.documentType.name,
                "document_name" to document.fileName,
                "document_mime_type" to document.fileType.name,
                "document_size" to document.fileSize,
                "ocr_success" to (document.processingResult != null),
                "extracted_text_available" to (document.processingResult?.extractedFields?.isNotEmpty() == true),
                "document_status" to (document.documentStatus ?: DocumentStatus.UPLOADED).name,
                "ocr_confidence_available" to (document.processingResult?.ocrConfidence != null)
            )
            
            // Build system prompt specific to document analysis
            val documentPrompt = """
                $baseSystemPrompt
                
                You are analyzing a ${document.documentType.name} document.
                
                Please provide feedback on the document quality based on the processing results.
                If OCR was successful, mention that the document was processed successfully.
                If OCR failed or has low confidence, suggest ways to improve document quality.
                
                Document information:
                ${formatContextForPrompt(documentContext)}
                
                Your response should be brief (2-3 sentences) and helpful for the user to understand
                if their document was processed correctly or needs to be reuploaded.
            """.trimIndent()
            
            // User query for document feedback
            val userQuery = "How is the quality of my ${document.documentType.name} document? Was it processed successfully?"
            
            // Get response from OpenAI
            val chatCompletion = getOpenAIResponse(documentPrompt, userQuery)
            
            // Create and return AI assistant message
            return@withContext AIAssistantMessage(
                id = UUID.randomUUID().toString(),
                applicationId = document.applicationId,
                timestamp = LocalDateTime.now(),
                messageType = MessageType.AI_RESPONSE,
                message = chatCompletion?.choices?.firstOrNull()?.message?.content
                    ?: getDefaultDocumentFeedback(document),
                screen = "document_upload"
            )
        } catch (e: Exception) {
            AppLogger.e("Error getting document quality feedback", e)
            
            // Return fallback message on error
            return@withContext createErrorMessage(
                "I couldn't analyze your document properly. Please ensure it's clear and properly oriented.",
                document.applicationId,
                "document_upload"
            )
        }
    }

    /**
     * Reviews the complete application and provides suggestions
     */
    override suspend fun reviewApplication(application: LoanApplication): List<String> = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("Reviewing application: ${application.id}")
            
            // Build enhanced context with all application data
            val enhancedContext = buildEnhancedContext("review_and_submit", emptyMap(), application)
            
            // Build system prompt for application review
            val reviewPrompt = """
                $baseSystemPrompt
                
                You are reviewing a complete loan application before submission.
                
                Please analyze the application data and provide a list of 3-5 suggestions or observations.
                Focus on potential issues, missing information, or areas that might need attention.
                Each suggestion should be a single sentence that is clear and actionable.
                
                Application information:
                ${formatContextForPrompt(enhancedContext)}
                
                Format your response as a numbered list with each item on a new line.
                Keep each suggestion concise and focused on one aspect of the application.
            """.trimIndent()
            
            // User query for application review
            val userQuery = "Please review my loan application and provide suggestions or observations."
            
            // Get response from OpenAI
            val chatCompletion = getOpenAIResponse(reviewPrompt, userQuery)
            
            // Parse suggestions from response
            val suggestionsText = chatCompletion?.choices?.firstOrNull()?.message?.content ?: ""
            
            // Process response into a list of suggestions
            // Remove numbers and bullet points, trim whitespace
            return@withContext suggestionsText
                .split("\n")
                .filter { it.isNotBlank() }
                .map { line ->
                    line.trim().replace(Regex("^[0-9]+[.)]\\s*|[-•]\\s*"), "")
                }
                .filter { it.isNotBlank() }
                .ifEmpty { 
                    getDefaultReviewSuggestions() 
                }
        } catch (e: Exception) {
            AppLogger.e("Error reviewing application", e)
            
            // Return default suggestions on error
            return@withContext getDefaultReviewSuggestions()
        }
    }

    /**
     * Gets assistance for a specific form field
     */
    override suspend fun getFieldAssistance(
        fieldName: String,
        fieldValue: String?,
        context: Map<String, Any>
    ): AIAssistantMessage = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("Getting field assistance for: $fieldName")
            
            // Application ID and screen from context
            val applicationId = context["application_id"] as? String
            val screen = context["screen"] as? String ?: "unknown"
            
            // Build field-specific context
            val fieldContext = mapOf(
                "field_name" to fieldName,
                "field_value" to (fieldValue ?: "empty"),
                "screen" to screen
            ) + context
            
            // Build system prompt for field assistance
            val fieldPrompt = """
                $baseSystemPrompt
                
                You are providing help with the "$fieldName" field on the $screen screen.
                
                Provide a short, helpful explanation about this field, including:
                - What information is expected
                - The correct format if applicable
                - Any tips or common mistakes to avoid
                
                Context information:
                ${formatContextForPrompt(fieldContext)}
                
                Keep your response concise (2-3 sentences) and directly applicable to this specific field.
            """.trimIndent()
            
            // User query for field assistance
            val userQuery = "What should I enter in the \"$fieldName\" field?"
            
            // Get response from OpenAI
            val chatCompletion = getOpenAIResponse(fieldPrompt, userQuery)
            
            // Create and return AI assistant message
            return@withContext AIAssistantMessage(
                id = UUID.randomUUID().toString(),
                applicationId = applicationId,
                timestamp = LocalDateTime.now(),
                messageType = MessageType.AI_RESPONSE,
                message = chatCompletion?.choices?.firstOrNull()?.message?.content
                    ?: getDefaultFieldMessage(fieldName),
                screen = screen
            )
        } catch (e: Exception) {
            AppLogger.e("Error getting field assistance", e)
            
            // Return fallback message based on the field name
            return@withContext createErrorMessage(
                getDefaultFieldMessage(fieldName),
                context["application_id"] as? String,
                context["screen"] as? String
            )
        }
    }
    
    /**
     * Processes a general user query with application context
     */
    suspend fun processUserQuery(
        query: String,
        screen: String?,
        applicationId: String?,
        applicationContext: Map<String, Any>?
    ): AIAssistantMessage = withContext(Dispatchers.IO) {
        try {
            AppLogger.d("Processing user query: $query on screen: $screen")
            
            // Get application data if available
            val application = (applicationContext?.get("application") as? LoanApplication)
            
            // Build enhanced context
            val enhancedContext = buildEnhancedContext(
                screen ?: "unknown", 
                applicationContext ?: emptyMap(),
                application
            )
            
            // Build system prompt
            val queryPrompt = """
                $baseSystemPrompt
                
                The user is currently on the ${screen ?: "unknown"} screen.
                
                Please answer their question based on the context provided.
                Be concise but thorough in your response.
                
                Context information:
                ${formatContextForPrompt(enhancedContext)}
            """.trimIndent()
            
            // Get response from OpenAI
            val chatCompletion = getOpenAIResponse(queryPrompt, query)
            
            // Create and return AI assistant message
            return@withContext AIAssistantMessage(
                id = UUID.randomUUID().toString(),
                applicationId = applicationId,
                timestamp = LocalDateTime.now(),
                messageType = MessageType.AI_RESPONSE,
                message = chatCompletion?.choices?.firstOrNull()?.message?.content
                    ?: "I'm not sure I understand. Could you please rephrase your question?",
                screen = screen
            )
        } catch (e: Exception) {
            AppLogger.e("Error processing user query", e)
            
            // Return fallback message on error
            return@withContext createErrorMessage(
                "I'm having trouble processing your query right now. Please try again later.",
                applicationId,
                screen
            )
        }
    }

    /**
     * Helper function to build enhanced context with all available information
     * Updated for v1.5.0 to add Bureau Confirmation context
     */
    private fun buildEnhancedContext(
        screen: String,
        context: Map<String, Any>,
        application: LoanApplication?
    ): Map<String, Any> {
        val enhancedContext = mutableMapOf<String, Any>()
        
        // Add basic context information
        enhancedContext["screen"] = screen
        enhancedContext["timestamp"] = System.currentTimeMillis()
        
        // Add all provided context
        enhancedContext.putAll(context)
        
        // Add application data if available
        application?.let { app ->
            enhancedContext["application_id"] = app.id
            enhancedContext["application_status"] = app.applicationStatus.name
            enhancedContext["application_created_date"] = app.createdAt.toString()
            enhancedContext["application_last_updated"] = app.lastUpdatedAt.toString()
            
            // Add PAN details
            app.panDetails?.let { pan ->
                enhancedContext["pan_verified"] = pan.isVerified ?: false
                
                // Don't include actual PAN number for privacy/security
                enhancedContext["has_pan_number"] = pan.panNumber != null && pan.panNumber.toString().trim() != ""
                
                // Add OCR status if available
                if (context.containsKey("ocr_scan_state")) {
                    val ocrState = context["ocr_scan_state"] as? OCRScanState
                    if (ocrState != null) {
                        enhancedContext["ocr_success"] = (ocrState is OCRScanState.Success)
                        enhancedContext["ocr_error"] = (ocrState is OCRScanState.Error)
                        enhancedContext["ocr_scanning"] = (ocrState is OCRScanState.Scanning)
                        
                        // If it's an error, include the error message
                        if (ocrState is OCRScanState.Error) {
                            enhancedContext["ocr_error_message"] = ocrState.message
                        }
                        
                        // If it's a success, include the extracted text
                        if (ocrState is OCRScanState.Success) {
                            enhancedContext["ocr_extracted_text"] = ocrState.text
                        }
                    }
                }
            }
            
            // Add personal info
            app.personalInfo?.let { personal ->
                enhancedContext["has_personal_info"] = true
                enhancedContext["name"] = personal.name ?: ""
                enhancedContext["email"] = personal.email ?: ""
                enhancedContext["has_address"] = personal.address != null && personal.address.toString().trim() != ""
                
                // Safe age calculation
                personal.dateOfBirth?.let { dob ->
                    enhancedContext["age"] = calculateAge(dob)
                }
                
                enhancedContext["personal_info_complete"] = isPersonalInfoComplete(personal)
            }
            
            // Add employment details
            app.employmentDetails?.let { employment ->
                enhancedContext["has_employment_details"] = true
                enhancedContext["employment_type"] = employment.employmentType.name
                enhancedContext["is_government_employee"] = (employment.employmentType == EmploymentType.GOVERNMENT)
                enhancedContext["employer_name"] = employment.employerName ?: ""
                
                // Handle nullable integers for Map<String, Any>
                val monthlySalary = employment.monthlySalary
                if (monthlySalary != null) {
                    enhancedContext["monthly_salary"] = monthlySalary.toDouble()
                } else {
                    enhancedContext["monthly_salary"] = 0.0
                }
                
                val monthlyEmi = employment.monthlyEmi
                if (monthlyEmi != null) {
                    enhancedContext["monthly_emi"] = monthlyEmi  // Already a Double, no need for conversion
                } else {
                    enhancedContext["monthly_emi"] = 0.0
                }
                
                // Check for verification
                if (employment.isVerified != null) {
                    enhancedContext["employment_verified"] = employment.isVerified
                }
            }
            
            // Add bureau information if available
            app.bureauReport?.let { bureauReport ->
                enhancedContext["has_bureau_data"] = true
                enhancedContext["bureau_score"] = bureauReport.creditScore ?: 0
                enhancedContext["bureau_accounts_total"] = bureauReport.accountSummary?.totalAccounts ?: 0
                enhancedContext["bureau_accounts_active"] = bureauReport.accountSummary?.activeAccounts ?: 0
                
                // Bureau Confirmation (v1.5.0) indicator
                enhancedContext["bureau_confirmation_needed"] = bureauReport.creditScore != null && 
                    bureauReport.creditScore!! in 1..1000
            }
            
            // Add documents information
            if (app.documents.isNotEmpty()) {
                enhancedContext["document_count"] = app.documents.size
                enhancedContext["document_types"] = app.documents.map { it.documentType.name }
                enhancedContext["all_required_documents_uploaded"] = hasAllRequiredDocuments(app)
                
                // Document verification statuses
                val verifiedCount = app.documents.count { 
                    it.documentStatus == DocumentStatus.VERIFIED 
                }
                enhancedContext["verified_document_count"] = verifiedCount
                enhancedContext["all_documents_verified"] = (verifiedCount == app.documents.size && app.documents.isNotEmpty())
            } else {
                enhancedContext["document_count"] = 0
                enhancedContext["all_required_documents_uploaded"] = false
            }
            
            // Add loan offer details
            app.loanOffer?.let { offer ->
                enhancedContext["has_loan_offer"] = true
                enhancedContext["loan_amount"] = offer.approvedLoanAmount
                
                // Handle nullable tenure
                val tenure = offer.selectedTenure
                if (tenure != null) {
                    enhancedContext["loan_tenure"] = tenure
                } else {
                    enhancedContext["loan_tenure"] = 0
                }
                
                enhancedContext["interest_rate"] = offer.interestRate
                
                // Safe EMI calculation
                val amount = offer.approvedLoanAmount
                val rate = offer.interestRate
                
                val calculatedEmi = if (amount > 0 && tenure != null && tenure > 0 && rate > 0) {
                    calculateEMI(amount, tenure, rate)
                } else {
                    0.0
                }
                
                enhancedContext["monthly_emi"] = calculatedEmi
                enhancedContext["loan_approved"] = isLoanApproved(offer)
            }
        }
        
        return enhancedContext
    }

    /**
     * Calculate EMI based on loan amount, tenure, and interest rate
     */
    private fun calculateEMI(principal: Double, tenure: Int, interestRate: Double): Double {
        // Simple EMI calculation formula
        val monthlyRate = interestRate / (12 * 100)
        val monthlyTenure = tenure
        return principal * monthlyRate * Math.pow(1 + monthlyRate, monthlyTenure.toDouble()) / 
               (Math.pow(1 + monthlyRate, monthlyTenure.toDouble()) - 1)
    }

    /**
     * Helper to create a default error message
     */
    private fun createErrorMessage(
        message: String,
        applicationId: String?,
        screen: String?
    ): AIAssistantMessage {
        return AIAssistantMessage(
            id = "error-${System.currentTimeMillis()}",
            applicationId = applicationId,
            timestamp = LocalDateTime.now(),
            messageType = MessageType.ERROR_MESSAGE,
            message = message,
            screen = screen
        )
    }

    /**
     * Format context map into a more readable string for the prompt
     */
    private fun formatContextForPrompt(context: Map<String, Any>): String {
        return context.entries.joinToString("\n") { (key, value) ->
            val formattedValue = when (value) {
                is Map<*, *> -> gson.toJson(value)
                is List<*> -> value.joinToString(", ")
                else -> value.toString()
            }
            "- $key: $formattedValue"
        }
    }

    /**
     * Helper function to get screen-specific prompt additions
     * Updated for v1.5.0 to include Bureau Confirmation screen
     */
    private fun getScreenSpecificPrompt(screen: String): String {
        return when (screen) {
            "pan_entry" -> """
                On the PAN Entry screen:
                - Users enter their PAN (Permanent Account Number) details
                - Users may scan their PAN card using the camera
                - The system verifies the PAN with government databases
                - Common issues include OCR reading errors and verification failures
            """.trimIndent()
            
            "personal_info" -> """
                On the Personal Information screen:
                - Users enter details like name, date of birth, email, and address
                - All fields must match their official documents
                - This information will be used for KYC verification
                - Discrepancies with PAN data may cause verification issues
            """.trimIndent()
            
            "employment_details" -> """
                On the Employment Details screen:
                - Users specify if they work for a government entity or private company
                - Users enter employer name, monthly income, and years of experience
                - Income details are used for loan eligibility calculation
                - Higher income and government employment typically result in better loan terms
            """.trimIndent()
            
            "document_upload" -> """
                On the Document Upload screen:
                - Users upload documents like bank statements, salary slips, ITR, and Form 26AS
                - Documents must be clear, complete, and in supported formats (PDF, JPG, PNG)
                - Document processing happens via backend AI services
                - Common issues include poor image quality and incomplete documents
            """.trimIndent()
            
            "bureau_confirmation" -> """
                On the Bureau Confirmation screen:
                - Users review their credit bureau data (tradelines/loans)
                - Users confirm or update EMI amounts for each loan
                - Users can provide comments to explain discrepancies
                - This information helps in accurately calculating loan eligibility
                - Backend AI services recalculate obligations based on user input
            """.trimIndent()
            
            "loan_offer" -> """
                On the Loan Offer screen:
                - Users can view their approved loan amount and terms
                - Details include loan amount, tenure options, EMI amounts, and interest rate
                - Users can adjust the loan amount and tenure within approved limits
                - The monthly EMI amount updates automatically based on selected options
            """.trimIndent()
            
            "employment_verification" -> """
                On the Employment Verification screen:
                - Users verify their employment through work email or ID card
                - Government employees may have different verification processes
                - Verification is crucial for loan approval
                - Delays or failures in verification may affect loan processing time
            """.trimIndent()
            
            "key_fact_sheet" -> """
                On the Key Fact Sheet screen:
                - Users review all their application details before final submission
                - All sections should be complete and accurate
                - Users can go back to edit any section if needed
                - This is the last opportunity to make changes before submission
            """.trimIndent()
            
            "application_complete" -> """
                On the Application Complete screen:
                - The application has been successfully submitted
                - Users can see their application number and status
                - Next steps and timeline information is displayed
                - Users may need to wait for final approval before disbursal
            """.trimIndent()
            
            else -> ""
        }
    }

    /**
     * Helper to get default review suggestions
     */
    private fun getDefaultReviewSuggestions(): List<String> {
        return listOf(
            "Ensure your name on the application matches exactly with your PAN card.",
            "Verify your employment details are accurate, especially your monthly income.",
            "Check that all uploaded documents are clearly visible and complete.",
            "Make sure your loan amount and tenure selection meets your financial needs.",
            "Confirm your contact information is correct for verification communications."
        )
    }

    /**
     * Helper to get default document feedback
     */
    private fun getDefaultDocumentFeedback(document: Document): String {
        return if (document.processingResult != null) {
            "Your ${document.documentType.name} was successfully processed. The document quality is good."
        } else {
            "We couldn't properly process your ${document.documentType.name}. Please ensure the image is clear, well-lit, and all text is visible."
        }
    }

    /**
     * Helper to get default field-specific messages
     */
    private fun getDefaultFieldMessage(fieldName: String): String {
        return when (fieldName.lowercase()) {
            "name", "full name" -> 
                "Enter your full name as it appears on your official documents like PAN card or Aadhaar."
                
            "date_of_birth", "dob" -> 
                "Enter your date of birth in DD/MM/YYYY format as it appears on your official documents."
                
            "email", "email address" -> 
                "Enter an active email address that you check regularly. Verification communications will be sent here."
                
            "address", "current address" -> 
                "Enter your complete current residential address including pin code. This should match your address proof documents."
                
            "pan", "pan number" -> 
                "Enter your 10-character PAN (Permanent Account Number) in the format ABCDE1234F. This is mandatory for loan processing."
                
            "employer_name", "employer" -> 
                "Enter the full official name of your current employer. Avoid abbreviations unless they are part of the official name."
                
            "monthly_income", "income", "salary" -> 
                "Enter your gross monthly income (before deductions) in rupees. Do not include bonuses or variable pay."
                
            "emi", "current_emi", "monthly_emi" -> 
                "Enter your total current monthly EMI payments for all existing loans. This helps us calculate your eligibility."
                
            "years_of_experience", "experience" -> 
                "Enter your total professional work experience in years. If less than a year, enter 0."
                
            else -> 
                "Please provide the correct information for this field. Make sure it matches your official documents."
        }
    }

    /**
     * Call OpenAI API and get chat completion
     */
    private suspend fun getOpenAIResponse(systemPrompt: String, userQuery: String): ChatCompletion? {
        return try {
            // Create list of chat messages
            val messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = systemPrompt
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = userQuery
                )
            )
            
            // Create chat completion request
            val completionRequest = ChatCompletionRequest(
                model = defaultModel,
                messages = messages,
                temperature = 0.7,
                maxTokens = 500  // Limit response length
            )
            
            // Log request for debugging
            if (BuildConfig.DEBUG) {
                AppLogger.d("OpenAI Request - System Prompt: ${systemPrompt.take(100)}...")
                AppLogger.d("OpenAI Request - User Query: $userQuery")
            }
            
            // Call OpenAI API
            val result = openAI.chatCompletion(completionRequest)
            
            // Log response for debugging
            if (BuildConfig.DEBUG) {
                AppLogger.d("OpenAI Response: ${result.choices.firstOrNull()?.message?.content?.take(100)}...")
            }
            
            result
        } catch (e: Exception) {
            AppLogger.e("Error calling OpenAI API", e)
            null
        }
    }
    
    /**
     * Calculate age from date of birth
     */
    private fun calculateAge(dateOfBirth: LocalDate): Int {
        val now = LocalDateTime.now()
        return now.year - dateOfBirth.year - 
            if (now.dayOfYear < dateOfBirth.dayOfYear) 1 else 0
    }
    
    /**
     * Check if personal information is complete
     */
    private fun isPersonalInfoComplete(personalInfo: com.loansai.unassisted.domain.model.PersonalInfo): Boolean {
        val nameComplete = personalInfo.name != null && personalInfo.name.toString().trim() != ""
        val emailComplete = personalInfo.email != null && personalInfo.email.toString().trim() != ""
        val dobComplete = personalInfo.dateOfBirth != null
        val addressComplete = personalInfo.address != null && personalInfo.address.toString().trim() != ""
        
        return nameComplete && emailComplete && dobComplete && addressComplete
    }
    
    /**
     * Check if loan is approved
     */
    private fun isLoanApproved(loanOffer: com.loansai.unassisted.domain.model.LoanOffer): Boolean {
        return loanOffer.approvedLoanAmount > 0 && 
               loanOffer.offerStatus?.toString()?.equals("APPROVED", ignoreCase = true) == true
    }
    
    /**
     * Check if application has all required documents
     */
    private fun hasAllRequiredDocuments(application: LoanApplication): Boolean {
        // Define the set of required document types - modify based on your requirements
        val requiredDocTypes = setOf(
            com.loansai.unassisted.domain.model.DocumentType.BANK_STATEMENT,
            com.loansai.unassisted.domain.model.DocumentType.SALARY_SLIP
        )
        
        // Check if all required document types are present
        val uploadedTypes = application.documents.map { it.documentType }.toSet()
        return uploadedTypes.containsAll(requiredDocTypes)
    }
}