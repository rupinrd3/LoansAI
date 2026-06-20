package com.loansai.unassisted.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.gson.Gson
import com.loansai.unassisted.BuildConfig
import com.loansai.unassisted.domain.model.AppMetadata
import com.loansai.unassisted.domain.model.DeviceInfo
import com.loansai.unassisted.domain.model.DocumentEvent
import com.loansai.unassisted.domain.model.ErrorEvent
import com.loansai.unassisted.domain.model.LlmInteractionEvent
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.model.ObligationRefinementEvent
import com.loansai.unassisted.domain.model.ScreenVisit
import com.loansai.unassisted.domain.model.SectionTiming
import com.loansai.unassisted.domain.model.VerificationEvent
import com.loansai.unassisted.util.DateConverter
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of MetadataRepository
 * Updated for v1.5.0 to support LLM interaction events and obligation refinement events
 */
@Singleton
class MetadataRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val firebaseFunctions: FirebaseFunctions,
    private val gson: Gson
) : MetadataRepository {

    // Keep track of current screens and sections for timing
    private val currentScreens = mutableMapOf<String, ScreenVisit>()
    private val currentSections = mutableMapOf<String, SectionTiming>()
    
    /**
     * Initialize device info in Firestore and send event to orchestrator
     */
    override suspend fun initializeDeviceInfo(applicationId: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val deviceInfo = DeviceInfo(
                make = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
                osVersion = android.os.Build.VERSION.RELEASE,
                appVersion = BuildConfig.VERSION_NAME
            )
            
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(LocalDateTime.now()),
                "eventType" to MetadataEventType.APP_SESSION_START.name,
                "deviceInfo" to mapOf(
                    "make" to deviceInfo.make,
                    "model" to deviceInfo.model,
                    "osVersion" to deviceInfo.osVersion,
                    "appVersion" to deviceInfo.appVersion
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
            
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.APP_SESSION_START,
                mapOf(
                    "deviceInfo" to mapOf(
                        "make" to deviceInfo.make,
                        "model" to deviceInfo.model,
                        "osVersion" to deviceInfo.osVersion,
                        "appVersion" to deviceInfo.appVersion
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error initializing device info: ${e.message}", e)
            emit(Resource.Error("Failed to initialize device info: ${e.message}"))
        }
    }
    
    /**
     * Start app session in Firestore and send event to orchestrator
     */
    override suspend fun startAppSession(applicationId: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val eventId = UUID.randomUUID().toString()
            val now = LocalDateTime.now()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(now),
                "eventType" to MetadataEventType.APP_SESSION_START.name,
                "appSession" to mapOf(
                    "sessionId" to eventId,
                    "startTime" to DateConverter.toTimestamp(now),
                    "droppedOff" to false
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.APP_SESSION_START,
                mapOf(
                    "appSession" to mapOf(
                        "sessionId" to eventId,
                        "startTime" to now.toString(),
                        "droppedOff" to false
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error starting app session: ${e.message}", e)
            emit(Resource.Error("Failed to start app session: ${e.message}"))
        }
    }
    
    /**
     * End app session in Firestore and send event to orchestrator
     */
    override suspend fun endAppSession(applicationId: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val eventId = UUID.randomUUID().toString()
            val endTime = LocalDateTime.now()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(endTime),
                "eventType" to MetadataEventType.APP_SESSION_END.name,
                "appSession" to mapOf(
                    "endTime" to DateConverter.toTimestamp(endTime)
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.APP_SESSION_END,
                mapOf(
                    "appSession" to mapOf(
                        "endTime" to endTime.toString()
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error ending app session: ${e.message}", e)
            emit(Resource.Error("Failed to end app session: ${e.message}"))
        }
    }
    
    /**
     * Record screen visit in Firestore and send event to orchestrator
     */
    override suspend fun recordScreenVisit(applicationId: String, screenName: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val screenVisit = ScreenVisit(
                screenName = screenName,
                startTime = LocalDateTime.now()
            )
            
            // Store in memory for tracking duration
            currentScreens["$applicationId-$screenName"] = screenVisit
            
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(screenVisit.startTime),
                "eventType" to MetadataEventType.SCREEN_VISIT.name,
                "screenVisit" to mapOf(
                    "screenName" to screenVisit.screenName,
                    "startTime" to DateConverter.toTimestamp(screenVisit.startTime)
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.SCREEN_VISIT,
                mapOf(
                    "screenVisit" to mapOf(
                        "screenName" to screenVisit.screenName,
                        "startTime" to screenVisit.startTime.toString()
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error recording screen visit: ${e.message}", e)
            emit(Resource.Error("Failed to record screen visit: ${e.message}"))
        }
    }
    
    /**
     * End screen visit in Firestore and send event to orchestrator
     */
    override suspend fun endScreenVisit(applicationId: String, screenName: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val screenKey = "$applicationId-$screenName"
            val startScreenVisit = currentScreens[screenKey]
            
            if (startScreenVisit != null) {
                val endTime = LocalDateTime.now()
                val durationSeconds = java.time.Duration.between(startScreenVisit.startTime, endTime).seconds
                
                val eventId = UUID.randomUUID().toString()
                
                // Prepare data for Firestore
                val metadataDoc = hashMapOf(
                    "eventId" to eventId,
                    "applicationId" to applicationId,
                    "eventTimestamp" to DateConverter.toTimestamp(endTime),
                    "eventType" to MetadataEventType.SCREEN_VISIT.name,
                    "screenVisit" to mapOf(
                        "screenName" to screenName,
                        "startTime" to DateConverter.toTimestamp(startScreenVisit.startTime),
                        "endTime" to DateConverter.toTimestamp(endTime),
                        "durationSeconds" to durationSeconds
                    )
                )
                
                // Save to Firestore
                firestore.collection("applications")
                    .document(applicationId)
                    .collection("metadata")
                    .document(eventId)
                    .set(metadataDoc)
                    .await()
                    
                // Send to metadata orchestrator
                sendEventToOrchestratorAsync(
                    applicationId,
                    MetadataEventType.SCREEN_VISIT,
                    mapOf(
                        "screenVisit" to mapOf(
                            "screenName" to screenName,
                            "startTime" to startScreenVisit.startTime.toString(),
                            "endTime" to endTime.toString(),
                            "durationSeconds" to durationSeconds
                        )
                    )
                )
                    
                // Remove from tracking
                currentScreens.remove(screenKey)
                
                emit(Resource.Success(true))
            } else {
                emit(Resource.Error("No matching screen visit start found"))
            }
        } catch (e: Exception) {
            AppLogger.e("Error ending screen visit: ${e.message}", e)
            emit(Resource.Error("Failed to end screen visit: ${e.message}"))
        }
    }

    /**
     * Start section timing in Firestore and send event to orchestrator
     */
    override suspend fun startSectionTiming(
        applicationId: String,
        screenName: String,
        sectionName: String
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val sectionTiming = SectionTiming(
                screenName = screenName,
                sectionName = sectionName,
                startTime = LocalDateTime.now()
            )
            
            // Store in memory for tracking duration
            val sectionKey = "$applicationId-$screenName-$sectionName"
            currentSections[sectionKey] = sectionTiming
            
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(sectionTiming.startTime),
                "eventType" to MetadataEventType.SECTION_COMPLETED.name,
                "sectionTiming" to mapOf(
                    "screenName" to sectionTiming.screenName,
                    "sectionName" to sectionTiming.sectionName,
                    "startTime" to DateConverter.toTimestamp(sectionTiming.startTime)
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.SECTION_COMPLETED,
                mapOf(
                    "sectionTiming" to mapOf(
                        "screenName" to sectionTiming.screenName,
                        "sectionName" to sectionTiming.sectionName,
                        "startTime" to sectionTiming.startTime.toString()
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error starting section timing: ${e.message}", e)
            emit(Resource.Error("Failed to start section timing: ${e.message}"))
        }
    }
    
    /**
     * Complete section timing in Firestore and send event to orchestrator
     */
    override suspend fun completeSectionTiming(
        applicationId: String,
        screenName: String,
        sectionName: String
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val sectionKey = "$applicationId-$screenName-$sectionName"
            val startSectionTiming = currentSections[sectionKey]
            
            if (startSectionTiming != null) {
                val endTime = LocalDateTime.now()
                val durationSeconds = java.time.Duration.between(startSectionTiming.startTime, endTime).seconds
                
                val eventId = UUID.randomUUID().toString()
                
                // Prepare data for Firestore
                val metadataDoc = hashMapOf(
                    "eventId" to eventId,
                    "applicationId" to applicationId,
                    "eventTimestamp" to DateConverter.toTimestamp(endTime),
                    "eventType" to MetadataEventType.SECTION_COMPLETED.name,
                    "sectionTiming" to mapOf(
                        "screenName" to screenName,
                        "sectionName" to sectionName,
                        "startTime" to DateConverter.toTimestamp(startSectionTiming.startTime),
                        "endTime" to DateConverter.toTimestamp(endTime),
                        "durationSeconds" to durationSeconds
                    )
                )
                
                // Save to Firestore
                firestore.collection("applications")
                    .document(applicationId)
                    .collection("metadata")
                    .document(eventId)
                    .set(metadataDoc)
                    .await()
                    
                // Send to metadata orchestrator
                sendEventToOrchestratorAsync(
                    applicationId,
                    MetadataEventType.SECTION_COMPLETED,
                    mapOf(
                        "sectionTiming" to mapOf(
                            "screenName" to screenName,
                            "sectionName" to sectionName,
                            "startTime" to startSectionTiming.startTime.toString(),
                            "endTime" to endTime.toString(),
                            "durationSeconds" to durationSeconds
                        )
                    )
                )
                    
                // Remove from tracking
                currentSections.remove(sectionKey)
                
                emit(Resource.Success(true))
            } else {
                emit(Resource.Error("No matching section timing start found"))
            }
        } catch (e: Exception) {
            AppLogger.e("Error completing section timing: ${e.message}", e)
            emit(Resource.Error("Failed to complete section timing: ${e.message}"))
        }
    }
    
    /**
     * Record document event in Firestore and send event to orchestrator
     */
    override suspend fun recordDocumentEvent(
        applicationId: String,
        documentEvent: DocumentEvent
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(documentEvent.timestamp),
                "eventType" to MetadataEventType.DOCUMENT_EVENT.name,
                "documentEvent" to mapOf(
                    "documentType" to documentEvent.documentType,
                    "documentSourceType" to documentEvent.documentSourceType,
                    "action" to documentEvent.action,
                    "timestamp" to DateConverter.toTimestamp(documentEvent.timestamp),
                    "status" to documentEvent.status,
                    "failureReason" to documentEvent.failureReason,
                    "extractionStatus" to documentEvent.extractionStatus,
                    "fileMetadata" to documentEvent.fileMetadata
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            val orchestratorMetadata = mutableMapOf<String, Any>()
            orchestratorMetadata["documentEvent"] = mapOf(
                "documentType" to documentEvent.documentType,
                "documentSourceType" to documentEvent.documentSourceType,
                "action" to documentEvent.action,
                "timestamp" to documentEvent.timestamp.toString(),
                "status" to (documentEvent.status ?: ""),
                "failureReason" to (documentEvent.failureReason ?: ""),
                "extractionStatus" to (documentEvent.extractionStatus ?: "")
            )
            
            if (documentEvent.fileMetadata != null) {
                orchestratorMetadata["fileMetadata"] = documentEvent.fileMetadata
            }
            
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.DOCUMENT_EVENT,
                orchestratorMetadata
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error recording document event: ${e.message}", e)
            emit(Resource.Error("Failed to record document event: ${e.message}"))
        }
    }
    
    /**
     * Record verification event in Firestore and send event to orchestrator
     */
    override suspend fun recordVerificationEvent(
        applicationId: String,
        verificationEvent: VerificationEvent
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(verificationEvent.timestamp),
                "eventType" to MetadataEventType.VERIFICATION_EVENT.name,
                "verificationEvent" to mapOf(
                    "verificationType" to verificationEvent.verificationType,
                    "timestamp" to DateConverter.toTimestamp(verificationEvent.timestamp),
                    "status" to verificationEvent.status,
                    "failureReason" to verificationEvent.failureReason
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.VERIFICATION_EVENT,
                mapOf(
                    "verificationEvent" to mapOf(
                        "verificationType" to verificationEvent.verificationType,
                        "timestamp" to verificationEvent.timestamp.toString(),
                        "status" to verificationEvent.status,
                        "failureReason" to (verificationEvent.failureReason ?: "")
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error recording verification event: ${e.message}", e)
            emit(Resource.Error("Failed to record verification event: ${e.message}"))
        }
    }
    
    /**
     * Record error event in Firestore and send event to orchestrator
     */
    override suspend fun recordErrorEvent(
        applicationId: String,
        errorEvent: ErrorEvent
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(errorEvent.timestamp),
                "eventType" to MetadataEventType.ERROR_OCCURRED.name,
                "errorEvent" to mapOf(
                    "screenName" to errorEvent.screenName,
                    "timestamp" to DateConverter.toTimestamp(errorEvent.timestamp),
                    "errorCode" to errorEvent.errorCode,
                    "errorMessage" to errorEvent.errorMessage,
                    "stackTrace" to errorEvent.stackTrace
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.ERROR_OCCURRED,
                mapOf(
                    "errorEvent" to mapOf(
                        "screenName" to errorEvent.screenName,
                        "timestamp" to errorEvent.timestamp.toString(),
                        "errorCode" to (errorEvent.errorCode ?: ""),
                        "errorMessage" to errorEvent.errorMessage,
                        "stackTrace" to (errorEvent.stackTrace ?: "")
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error recording error event: ${e.message}", e)
            emit(Resource.Error("Failed to record error event: ${e.message}"))
        }
    }
    
    /**
     * Record LLM interaction event in Firestore and send event to orchestrator (new for v1.5.0)
     */
    override suspend fun recordLlmInteractionEvent(
        applicationId: String,
        llmInteraction: LlmInteractionEvent
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(llmInteraction.timestamp),
                "eventType" to if (llmInteraction.status == "INITIATED") {
                    MetadataEventType.LLM_CALL_INITIATED.name
                } else {
                    MetadataEventType.LLM_CALL_COMPLETED.name
                },
                "llmInteraction" to mapOf(
                    "llmCallType" to llmInteraction.llmCallType,
                    "timestamp" to DateConverter.toTimestamp(llmInteraction.timestamp),
                    "status" to llmInteraction.status,
                    "modelUsed" to llmInteraction.modelUsed,
                    "failureReason" to llmInteraction.failureReason
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            val eventType = if (llmInteraction.status == "INITIATED") {
                MetadataEventType.LLM_CALL_INITIATED
            } else {
                MetadataEventType.LLM_CALL_COMPLETED
            }
            
            sendEventToOrchestratorAsync(
                applicationId,
                eventType,
                mapOf(
                    "llmInteraction" to mapOf(
                        "llmCallType" to llmInteraction.llmCallType,
                        "timestamp" to llmInteraction.timestamp.toString(),
                        "status" to llmInteraction.status,
                        "modelUsed" to (llmInteraction.modelUsed ?: ""),
                        "failureReason" to (llmInteraction.failureReason ?: "")
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error recording LLM interaction event: ${e.message}", e)
            emit(Resource.Error("Failed to record LLM interaction event: ${e.message}"))
        }
    }
    
    /**
     * Record obligation refinement event in Firestore and send event to orchestrator (new for v1.5.0)
     */
    override suspend fun recordObligationRefinementEvent(
        applicationId: String,
        obligationEvent: ObligationRefinementEvent
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val eventId = UUID.randomUUID().toString()
            
            // Prepare data for Firestore
            val metadataDoc = hashMapOf(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(obligationEvent.timestamp),
                "eventType" to MetadataEventType.OBLIGATION_REFINEMENT_SUBMITTED.name,
                "obligationEvent" to mapOf(
                    "refinementRecordId" to obligationEvent.refinementRecordId,
                    "timestamp" to DateConverter.toTimestamp(obligationEvent.timestamp),
                    "status" to obligationEvent.status
                )
            )
            
            // Save to Firestore
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            // Send to metadata orchestrator
            sendEventToOrchestratorAsync(
                applicationId,
                MetadataEventType.OBLIGATION_REFINEMENT_SUBMITTED,
                mapOf(
                    "obligationEvent" to mapOf(
                        "refinementRecordId" to obligationEvent.refinementRecordId,
                        "timestamp" to obligationEvent.timestamp.toString(),
                        "status" to obligationEvent.status
                    )
                )
            )
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error recording obligation refinement event: ${e.message}", e)
            emit(Resource.Error("Failed to record obligation refinement event: ${e.message}"))
        }
    }
    
    /**
     * Get metadata for an application
     */
    override suspend fun getMetadata(applicationId: String): Flow<Resource<AppMetadata>> = flow {
        emit(Resource.Loading())
        
        try {
            // Query metadata subcollection
            val metadataSnapshot = firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .get()
                .await()
            
            // Process the metadata documents
            val deviceInfo = mutableListOf<DeviceInfo>()
            val screenVisits = mutableListOf<ScreenVisit>()
            val sectionTimings = mutableListOf<SectionTiming>()
            val documentEvents = mutableListOf<DocumentEvent>()
            val verificationEvents = mutableListOf<VerificationEvent>()
            val errorEvents = mutableListOf<ErrorEvent>()
            val llmInteractions = mutableListOf<LlmInteractionEvent>() // New for v1.5.0
            val obligationRefinements = mutableListOf<ObligationRefinementEvent>() // New for v1.5.0
            var appSessionStart: LocalDateTime? = null
            var appSessionEnd: LocalDateTime? = null
            
            for (doc in metadataSnapshot.documents) {
                val data = doc.data ?: continue
                
                when (data["eventType"] as? String) {
                    MetadataEventType.APP_SESSION_START.name -> {
                        // Handle app session start
                        val sessionMap = data["appSession"] as? Map<*, *>
                        if (sessionMap != null) {
                            val startTime = (sessionMap["startTime"] as? com.google.firebase.Timestamp)?.toDate()
                            if (startTime != null) {
                                appSessionStart = LocalDateTime.ofInstant(startTime.toInstant(), java.time.ZoneId.systemDefault())
                            }
                        }
                        
                        // Extract device info
                        val deviceInfoMap = data["deviceInfo"] as? Map<*, *>
                        if (deviceInfoMap != null) {
                            deviceInfo.add(
                                DeviceInfo(
                                    make = deviceInfoMap["make"] as? String ?: "Unknown",
                                    model = deviceInfoMap["model"] as? String ?: "Unknown",
                                    osVersion = deviceInfoMap["osVersion"] as? String ?: "Unknown",
                                    appVersion = deviceInfoMap["appVersion"] as? String ?: "Unknown"
                                )
                            )
                        }
                    }
                    MetadataEventType.APP_SESSION_END.name -> {
                        // Handle app session end
                        val sessionMap = data["appSession"] as? Map<*, *>
                        if (sessionMap != null) {
                            val endTime = (sessionMap["endTime"] as? com.google.firebase.Timestamp)?.toDate()
                            if (endTime != null) {
                                appSessionEnd = LocalDateTime.ofInstant(endTime.toInstant(), java.time.ZoneId.systemDefault())
                            }
                        }
                    }
                    MetadataEventType.SCREEN_VISIT.name -> {
                        // Handle screen visit
                        val screenVisitMap = data["screenVisit"] as? Map<*, *>
                        if (screenVisitMap != null) {
                            val screenName = screenVisitMap["screenName"] as? String ?: "Unknown"
                            val startTime = (screenVisitMap["startTime"] as? com.google.firebase.Timestamp)?.toDate()
                            val endTime = (screenVisitMap["endTime"] as? com.google.firebase.Timestamp)?.toDate()
                            val durationSeconds = (screenVisitMap["durationSeconds"] as? Number)?.toLong() ?: 0L
                            
                            if (startTime != null) {
                                screenVisits.add(
                                    ScreenVisit(
                                        screenName = screenName,
                                        startTime = LocalDateTime.ofInstant(startTime.toInstant(), java.time.ZoneId.systemDefault()),
                                        endTime = endTime?.let { LocalDateTime.ofInstant(it.toInstant(), java.time.ZoneId.systemDefault()) },
                                        durationSeconds = durationSeconds
                                    )
                                )
                            }
                        }
                    }
                    MetadataEventType.SECTION_COMPLETED.name -> {
                        // Handle section timing
                        val sectionTimingMap = data["sectionTiming"] as? Map<*, *>
                        if (sectionTimingMap != null) {
                            val screenName = sectionTimingMap["screenName"] as? String ?: "Unknown"
                            val sectionName = sectionTimingMap["sectionName"] as? String ?: "Unknown"
                            val startTime = (sectionTimingMap["startTime"] as? com.google.firebase.Timestamp)?.toDate()
                            val endTime = (sectionTimingMap["endTime"] as? com.google.firebase.Timestamp)?.toDate()
                            val durationSeconds = (sectionTimingMap["durationSeconds"] as? Number)?.toLong() ?: 0L
                            
                            if (startTime != null) {
                                sectionTimings.add(
                                    SectionTiming(
                                        screenName = screenName,
                                        sectionName = sectionName,
                                        startTime = LocalDateTime.ofInstant(startTime.toInstant(), java.time.ZoneId.systemDefault()),
                                        endTime = endTime?.let { LocalDateTime.ofInstant(it.toInstant(), java.time.ZoneId.systemDefault()) },
                                        durationSeconds = durationSeconds
                                    )
                                )
                            }
                        }
                    }
                    MetadataEventType.DOCUMENT_EVENT.name -> {
                        // Handle document event
                        val documentEventMap = data["documentEvent"] as? Map<*, *>
                        if (documentEventMap != null) {
                            val documentType = documentEventMap["documentType"] as? String ?: "Unknown"
                            val documentSourceType = documentEventMap["documentSourceType"] as? String ?: "Unknown"
                            val action = documentEventMap["action"] as? String ?: "Unknown"
                            val timestamp = (documentEventMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                            val status = documentEventMap["status"] as? String
                            val failureReason = documentEventMap["failureReason"] as? String
                            val extractionStatus = documentEventMap["extractionStatus"] as? String
                            val fileMetadata = documentEventMap["fileMetadata"] as? Map<String, Any>
                            
                            if (timestamp != null) {
                                documentEvents.add(
                                    DocumentEvent(
                                        documentType = documentType,
                                        documentSourceType = documentSourceType,
                                        action = action,
                                        timestamp = LocalDateTime.ofInstant(timestamp.toInstant(), java.time.ZoneId.systemDefault()),
                                        status = status,
                                        failureReason = failureReason,
                                        extractionStatus = extractionStatus,
                                        fileMetadata = fileMetadata
                                    )
                                )
                            }
                        }
                    }
                    MetadataEventType.VERIFICATION_EVENT.name -> {
                        // Handle verification event
                        val verificationEventMap = data["verificationEvent"] as? Map<*, *>
                        if (verificationEventMap != null) {
                            val verificationType = verificationEventMap["verificationType"] as? String ?: "Unknown"
                            val timestamp = (verificationEventMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                            val status = verificationEventMap["status"] as? String ?: "Unknown"
                            val failureReason = verificationEventMap["failureReason"] as? String
                            
                            if (timestamp != null) {
                                verificationEvents.add(
                                    VerificationEvent(
                                        verificationType = verificationType,
                                        timestamp = LocalDateTime.ofInstant(timestamp.toInstant(), java.time.ZoneId.systemDefault()),
                                        status = status,
                                        failureReason = failureReason
                                    )
                                )
                            }
                        }
                    }
                    MetadataEventType.ERROR_OCCURRED.name -> {
                        // Handle error event
                        val errorEventMap = data["errorEvent"] as? Map<*, *>
                        if (errorEventMap != null) {
                            val screenName = errorEventMap["screenName"] as? String ?: "Unknown"
                            val timestamp = (errorEventMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                            val errorCode = errorEventMap["errorCode"] as? String
                            val errorMessage = errorEventMap["errorMessage"] as? String ?: "Unknown Error"
                            val stackTrace = errorEventMap["stackTrace"] as? String
                            
                            if (timestamp != null) {
                                errorEvents.add(
                                    ErrorEvent(
                                        screenName = screenName,
                                        timestamp = LocalDateTime.ofInstant(timestamp.toInstant(), java.time.ZoneId.systemDefault()),
                                        errorCode = errorCode,
                                        errorMessage = errorMessage,
                                        stackTrace = stackTrace
                                    )
                                )
                            }
                        }
                    }
                    // New event types for v1.5.0
                    MetadataEventType.LLM_CALL_INITIATED.name, 
                    MetadataEventType.LLM_CALL_COMPLETED.name -> {
                        // Handle LLM interaction event
                        val llmInteractionMap = data["llmInteraction"] as? Map<*, *>
                        if (llmInteractionMap != null) {
                            val llmCallType = llmInteractionMap["llmCallType"] as? String ?: "Unknown"
                            val timestamp = (llmInteractionMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                            val status = llmInteractionMap["status"] as? String ?: "Unknown"
                            val modelUsed = llmInteractionMap["modelUsed"] as? String
                            val failureReason = llmInteractionMap["failureReason"] as? String
                            
                            if (timestamp != null) {
                                llmInteractions.add(
                                    LlmInteractionEvent(
                                        llmCallType = llmCallType,
                                        timestamp = LocalDateTime.ofInstant(timestamp.toInstant(), java.time.ZoneId.systemDefault()),
                                        status = status,
                                        modelUsed = modelUsed,
                                        failureReason = failureReason
                                    )
                                )
                            }
                        }
                    }
                    MetadataEventType.OBLIGATION_REFINEMENT_SUBMITTED.name -> {
                        // Handle obligation refinement event
                        val obligationEventMap = data["obligationEvent"] as? Map<*, *>
                        if (obligationEventMap != null) {
                            val refinementRecordId = obligationEventMap["refinementRecordId"] as? String ?: "Unknown"
                            val timestamp = (obligationEventMap["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                            val status = obligationEventMap["status"] as? String ?: "Unknown"
                            
                            if (timestamp != null) {
                                obligationRefinements.add(
                                    ObligationRefinementEvent(
                                        refinementRecordId = refinementRecordId,
                                        timestamp = LocalDateTime.ofInstant(timestamp.toInstant(), java.time.ZoneId.systemDefault()),
                                        status = status
                                    )
                                )
                            }
                        }
                    }
                }
            }
            
            // Create and return consolidated metadata
            val metadata = AppMetadata(
                deviceInfo = deviceInfo.firstOrNull(),
                screenVisits = screenVisits,
                sectionTimings = sectionTimings,
                documentEvents = documentEvents,
                verificationEvents = verificationEvents,
                errorEvents = errorEvents,
                llmInteractions = llmInteractions, // New for v1.5.0
                obligationRefinements = obligationRefinements, // New for v1.5.0
                appSessionStart = appSessionStart,
                appSessionEnd = appSessionEnd
            )
            
            emit(Resource.Success(metadata))
        } catch (e: Exception) {
            AppLogger.e("Error getting metadata: ${e.message}", e)
            emit(Resource.Error("Failed to get metadata: ${e.message}"))
        }
    }
    
    /**
     * Send event to metadata orchestrator (Cloud Function)
     */
    override suspend fun sendEventToOrchestrator(
        applicationId: String,
        eventType: MetadataEventType,
        metadata: Map<String, Any>?
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Prepare data for orchestrator (Cloud Function)
            val eventData = hashMapOf<String, Any>(
                "applicationId" to applicationId,
                "eventType" to eventType.name,
                "timestamp" to LocalDateTime.now().toString()
            )
            
            // Add additional metadata if provided
            if (metadata != null) {
                eventData.putAll(metadata)
            }
            
            // Call the Cloud Function
            val result = firebaseFunctions
                .getHttpsCallable("processEvent")
                .call(eventData)
                .await()
            
            val responseData = result.data as? Map<*, *>
            val success = responseData?.get("success") as? Boolean ?: false
            
            if (success) {
                emit(Resource.Success(true))
            } else {
                val errorMsg = responseData?.get("error") as? String ?: "Unknown error from processEvent function"
                emit(Resource.Error(errorMsg))
            }
        } catch (e: Exception) {
            AppLogger.e("Error sending event to orchestrator: ${e.message}", e)
            
            // Even if the Cloud Function call fails, continue without failing
            // This ensures app functionality isn't blocked by metadata events
            emit(Resource.Success(true))
        }
    }
    
    /**
     * Send event to metadata orchestrator asynchronously
     * This method doesn't return a result and is used when we don't want to wait for the response
     */
    private fun sendEventToOrchestratorAsync(
        applicationId: String,
        eventType: MetadataEventType,
        metadata: Map<String, Any>? = null
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Prepare data for orchestrator (Cloud Function)
                val eventData = hashMapOf<String, Any>(
                    "applicationId" to applicationId,
                    "eventType" to eventType.name,
                    "timestamp" to LocalDateTime.now().toString()
                )
                
                // Add additional metadata if provided
                if (metadata != null) {
                    eventData.putAll(metadata)
                }
                
                // Call the Cloud Function
                firebaseFunctions
                    .getHttpsCallable("processEvent")
                    .call(eventData)
                    .addOnSuccessListener {
                        // Log success
                        AppLogger.d("Event ${eventType.name} sent to orchestrator successfully")
                    }
                    .addOnFailureListener { e ->
                        // Log failure but don't block the app
                        AppLogger.e("Error sending event to orchestrator: ${e.message}", e)
                    }
            } catch (e: Exception) {
                // Log error but don't block the app
                AppLogger.e("Error sending event to orchestrator async: ${e.message}", e)
            }
        }
    }
}