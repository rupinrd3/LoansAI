package com.loansai.unassisted.service.ai

import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.Document
import com.loansai.unassisted.domain.model.LoanApplication

/**
 * Interface for AI assistant services
 */
interface AIAssistantService {
    /**
     * Gets contextual assistance for a specific screen or task
     *
     * @param screen The current screen (e.g., "pan_entry", "employment_details")
     * @param context Additional context information
     * @param application The current loan application
     * @return AI assistant message with assistance
     */
    suspend fun getContextualAssistance(
        screen: String,
        context: Map<String, Any>,
        application: LoanApplication?
    ): AIAssistantMessage
    
    /**
     * Gets document quality feedback
     *
     * @param document The document to analyze
     * @return AI feedback on document quality
     */
    suspend fun getDocumentQualityFeedback(document: Document): AIAssistantMessage
    
    /**
     * Analyzes the full application for potential issues
     *
     * @param application The complete loan application
     * @return List of suggestions or corrections
     */
    suspend fun reviewApplication(application: LoanApplication): List<String>
    
    /**
     * Gets assistance for a specific form field
     *
     * @param fieldName The name of the field
     * @param fieldValue The current value of the field
     * @param context Additional context information
     * @return AI assistant message with field-specific assistance
     */
    suspend fun getFieldAssistance(
        fieldName: String,
        fieldValue: String?,
        context: Map<String, Any>
    ): AIAssistantMessage
}