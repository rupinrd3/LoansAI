package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.AppMetadata
import com.loansai.unassisted.domain.model.DocumentEvent
import com.loansai.unassisted.domain.model.ErrorEvent
import com.loansai.unassisted.domain.model.LlmInteractionEvent
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.model.ObligationRefinementEvent
import com.loansai.unassisted.domain.model.VerificationEvent
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for metadata operations
 * Updated for v1.5.0 with LLM interaction events
 */
interface MetadataRepository {
    
    /**
     * Initialize device information
     *
     * @param applicationId The application ID
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun initializeDeviceInfo(applicationId: String): Flow<Resource<Boolean>>
    
    /**
     * Start an app session
     *
     * @param applicationId The application ID
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun startAppSession(applicationId: String): Flow<Resource<Boolean>>
    
    /**
     * End an app session
     *
     * @param applicationId The application ID
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun endAppSession(applicationId: String): Flow<Resource<Boolean>>
    
    /**
     * Record a screen visit
     *
     * @param applicationId The application ID
     * @param screenName The name of the screen being visited
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun recordScreenVisit(applicationId: String, screenName: String): Flow<Resource<Boolean>>
    
    /**
     * End a screen visit
     *
     * @param applicationId The application ID
     * @param screenName The name of the screen being ended
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun endScreenVisit(applicationId: String, screenName: String): Flow<Resource<Boolean>>
    
    /**
     * Start a section timing
     *
     * @param applicationId The application ID
     * @param screenName The name of the screen containing the section
     * @param sectionName The name of the section
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun startSectionTiming(
        applicationId: String, 
        screenName: String, 
        sectionName: String
    ): Flow<Resource<Boolean>>
    
    /**
     * Complete a section timing
     *
     * @param applicationId The application ID
     * @param screenName The name of the screen containing the section
     * @param sectionName The name of the section
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun completeSectionTiming(
        applicationId: String, 
        screenName: String, 
        sectionName: String
    ): Flow<Resource<Boolean>>
    
    /**
     * Record a document event
     *
     * @param applicationId The application ID
     * @param documentEvent The document event to record
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun recordDocumentEvent(
        applicationId: String, 
        documentEvent: DocumentEvent
    ): Flow<Resource<Boolean>>
    
    /**
     * Record a verification event
     *
     * @param applicationId The application ID
     * @param verificationEvent The verification event to record
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun recordVerificationEvent(
        applicationId: String, 
        verificationEvent: VerificationEvent
    ): Flow<Resource<Boolean>>
    
    /**
     * Record an error event
     *
     * @param applicationId The application ID
     * @param errorEvent The error event to record
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun recordErrorEvent(
        applicationId: String, 
        errorEvent: ErrorEvent
    ): Flow<Resource<Boolean>>
    
    /**
     * Record an LLM interaction event (NEW for v1.5.0)
     * 
     * @param applicationId The application ID
     * @param llmInteraction The LLM interaction event to record
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun recordLlmInteractionEvent(
        applicationId: String,
        llmInteraction: LlmInteractionEvent
    ): Flow<Resource<Boolean>>
    
    /**
     * Record an obligation refinement event (NEW for v1.5.0)
     * 
     * @param applicationId The application ID
     * @param obligationEvent The obligation refinement event to record
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun recordObligationRefinementEvent(
        applicationId: String,
        obligationEvent: ObligationRefinementEvent
    ): Flow<Resource<Boolean>>
    
    /**
     * Get all metadata for an application
     *
     * @param applicationId The application ID
     * @return Flow of Resource<AppMetadata> with all metadata
     */
    suspend fun getMetadata(applicationId: String): Flow<Resource<AppMetadata>>
    
    /**
     * Send event to metadata orchestrator
     *
     * @param applicationId The application ID
     * @param eventType The type of event
     * @param metadata Additional metadata to include
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun sendEventToOrchestrator(
        applicationId: String,
        eventType: MetadataEventType,
        metadata: Map<String, Any>? = null
    ): Flow<Resource<Boolean>>
}