package com.loansai.unassisted.domain.model

import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Represents the type of metadata event being logged
 * Updated for v1.5.0 with new LLM and Obligation events
 */
enum class MetadataEventType {
    APP_SESSION_START,
    APP_SESSION_END,
    APP_BACKGROUNDED,
    APP_FOREGROUNDED,
    SCREEN_VISIT,
    SECTION_COMPLETED,
    DOCUMENT_EVENT,
    VERIFICATION_EVENT,
    ERROR_OCCURRED,
    APPLICATION_SUBMITTED,
    APPLICATION_CANCELLED,
    
    // New event types for v1.5.0
    OBLIGATION_REFINEMENT_SUBMITTED,  // When user submits Bureau Confirmation screen
    LLM_CALL_INITIATED,              // When backend LLM API call is initiated
    LLM_CALL_COMPLETED               // When backend LLM API call completes
}

/**
 * Data class for screen visit metadata
 */
data class ScreenVisit(
    val screenName: String,
    val startTime: LocalDateTime = LocalDateTime.now(),
    val endTime: LocalDateTime? = null,
    val durationSeconds: Long = 0
)

/**
 * Data class for section timing metadata
 */
data class SectionTiming(
    val screenName: String,
    val sectionName: String,
    val startTime: LocalDateTime = LocalDateTime.now(),
    val endTime: LocalDateTime? = null,
    val durationSeconds: Long = 0
)

/**
 * Data class for device information
 */
data class DeviceInfo(
    val make: String,
    val model: String,
    val osVersion: String,
    val appVersion: String
)

/**
 * Data class for document events
 */
data class DocumentEvent(
    val documentType: String,
    val documentSourceType: String,
    val action: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: String? = null,
    val failureReason: String? = null,
    val extractionStatus: String? = null,
    val fileMetadata: Map<String, Any>? = null
)

/**
 * Data class for verification events
 */
data class VerificationEvent(
    val verificationType: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: String,
    val failureReason: String? = null
)

/**
 * Data class for error events
 */
data class ErrorEvent(
    val screenName: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val errorCode: String? = null,
    val errorMessage: String,
    val stackTrace: String? = null
)

/**
 * Data class for LLM interaction events (new for v1.5.0)
 */
data class LlmInteractionEvent(
    val llmCallType: String, // DOC_PROCESSING, OBLIGATION_RECALC, or DROPOFF_ANALYSIS
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: String, // INITIATED, COMPLETED, FAILED
    val modelUsed: String? = null,
    val failureReason: String? = null
)

/**
 * Data class for obligation refinement events (new for v1.5.0)
 */
data class ObligationRefinementEvent(
    val refinementRecordId: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: String // SUBMITTED, LLM_SUCCESS, LLM_FAILED
)

/**
 * Data class for application metadata
 */
data class AppMetadata(
    val deviceInfo: DeviceInfo? = null,
    val screenVisits: List<ScreenVisit> = emptyList(),
    val sectionTimings: List<SectionTiming> = emptyList(),
    val documentEvents: List<DocumentEvent> = emptyList(),
    val verificationEvents: List<VerificationEvent> = emptyList(),
    val errorEvents: List<ErrorEvent> = emptyList(),
    val llmInteractions: List<LlmInteractionEvent> = emptyList(), // New for v1.5.0
    val obligationRefinements: List<ObligationRefinementEvent> = emptyList(), // New for v1.5.0
    val appSessionStart: LocalDateTime? = null,
    val appSessionEnd: LocalDateTime? = null
)

/**
 * Repository interface for metadata operations
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
    
    /**
     * Record an LLM interaction event (new for v1.5.0)
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
     * Record an obligation refinement event (new for v1.5.0)
     * 
     * @param applicationId The application ID
     * @param obligationEvent The obligation refinement event to record
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun recordObligationRefinementEvent(
        applicationId: String,
        obligationEvent: ObligationRefinementEvent
    ): Flow<Resource<Boolean>>
}   