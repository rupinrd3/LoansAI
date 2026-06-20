package com.loansai.unassisted.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.loansai.unassisted.data.remote.api.LlmProcessingApi // Assuming this might be used in future, keep import
import com.loansai.unassisted.domain.model.ExcludedLoan
import com.loansai.unassisted.domain.model.LlmInteractionEvent
import com.loansai.unassisted.domain.model.LlmProcessingStatus
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.MetadataEventType
import com.loansai.unassisted.domain.model.ObligationRefinement
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.domain.repository.ObligationRefinementRepository
import com.loansai.unassisted.util.DateConverter
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import com.loansai.unassisted.service.ai.impl.GeminiDocumentService
import com.loansai.unassisted.domain.repository.AppwriteRepository
import com.loansai.unassisted.domain.model.TradelineItem
import com.loansai.unassisted.service.appwrite.AppwriteService
import com.loansai.unassisted.domain.repository.LoanRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.concurrent.TimeoutException
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.google.firebase.firestore.SetOptions
import com.loansai.unassisted.data.remote.api.BREApi
import com.loansai.unassisted.domain.model.BREInput
import com.loansai.unassisted.util.validation.BREDataStateChecker
import com.loansai.unassisted.util.constants.BREErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import java.time.Duration



@Singleton
class ObligationRefinementRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    // private val llmProcessingApi: LlmProcessingApi, // Keep if needed for future backend API calls
    private val metadataRepository: MetadataRepository,
    private val geminiDocumentService: GeminiDocumentService,
    private val appwriteRepository: AppwriteRepository,
    private val loanRepository: LoanRepository,
    private val preferencesDataSource:  PreferencesDataSource
) : ObligationRefinementRepository {

    // --- saveObligationRefinement, getObligationRefinement, etc. remain the same ---
     /**
    * Save an obligation refinement record to Firestore
    */
    override suspend fun saveObligationRefinement(
        obligationRefinement: ObligationRefinement
    ): Flow<Resource<ObligationRefinement>> = flow {
        emit(Resource.Loading())

        try {
            AppLogger.d("Saving obligation refinement with ID: ${obligationRefinement.recordId}")

            // Convert ObligationRefinement to Firestore map
            val refinementMap = mapOf(
                "recordId" to obligationRefinement.recordId,
                "applicationId" to obligationRefinement.applicationId,
                "createdAt" to DateConverter.toTimestamp(obligationRefinement.createdAt),
                "userProvidedEmis" to obligationRefinement.userProvidedEmis,
                "userComments" to obligationRefinement.userComments,
                "llmRecalculatedObligation" to obligationRefinement.llmRecalculatedObligation,
                "llmExcludedLoans" to obligationRefinement.llmExcludedLoans.map { loan ->
                    mapOf(
                        "tradelineId" to loan.tradelineId,
                        "reason" to loan.reason
                    )
                },
                "llmProcessingStatus" to obligationRefinement.llmProcessingStatus.name,
                "llmProcessedAt" to obligationRefinement.llmProcessedAt?.let { DateConverter.toTimestamp(it) }
            )

            // Save to Firestore with proper error handling
            firestore.collection("applications")
                .document(obligationRefinement.applicationId)
                .collection("obligationRefinement")
                .document(obligationRefinement.recordId)
                .set(refinementMap)
                .await()

            AppLogger.d("Successfully saved obligation refinement to Firestore")
            emit(Resource.Success(obligationRefinement))
        } catch (e: Exception) {
            val errorMessage = "Error saving obligation refinement: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }


    /**
     * Get a specific obligation refinement record
     */
    override suspend fun getObligationRefinement(
        refinementId: String
    ): Flow<Resource<ObligationRefinement>> = flow {
        emit(Resource.Loading())

        try {
            AppLogger.d("Getting obligation refinement with ID: $refinementId")

            // Query needs to search across all applications - not ideal, but workable for prototype
            // In production, we'd have a more efficient index or query strategy
            val querySnapshot = firestore.collectionGroup("obligationRefinement")
                .whereEqualTo("recordId", refinementId)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                val errorMessage = "Obligation refinement not found with ID: $refinementId"
                AppLogger.w(errorMessage)
                emit(Resource.Error(errorMessage))
                return@flow
            }

            val document = querySnapshot.documents[0]
            val obligationRefinement = parseDocumentToObligationRefinement(document)

            if (obligationRefinement != null) {
                emit(Resource.Success(obligationRefinement))
            } else {
                emit(Resource.Error("Failed to parse obligation refinement document"))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting obligation refinement: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Get all obligation refinement records for an application
     */
    override suspend fun getObligationRefinementsForApplication(
        applicationId: String
    ): Flow<Resource<List<ObligationRefinement>>> = flow {
        emit(Resource.Loading())

        try {
            AppLogger.d("Getting obligation refinements for application: $applicationId")

            val querySnapshot = firestore.collection("applications")
                .document(applicationId)
                .collection("obligationRefinement")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val refinements = querySnapshot.documents.mapNotNull { document ->
                parseDocumentToObligationRefinement(document)
            }

            AppLogger.d("Found ${refinements.size} obligation refinements")
            emit(Resource.Success(refinements))
        } catch (e: Exception) {
            val errorMessage = "Error getting obligation refinements: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }



    /**
     * Get the most recent obligation refinement record for an application
     */
    /**
    * Get the most recent obligation refinement record for an application
    */
    override suspend fun getLatestObligationRefinement(
        applicationId: String
    ): Flow<Resource<ObligationRefinement>> = flow {
        val queryId = UUID.randomUUID().toString().substring(0, 8)
        AppLogger.d("[OBL_QUERY:$queryId] Getting latest obligation refinement for application: $applicationId")
        emit(Resource.Loading())

        // Define retry delays
        val retryDelays = listOf(1000L, 2000L, 3000L) // Adjusted delays: 1s, 2s, 3s
        
        // --- Start Retry Loop ---
        for (attempt in 1..retryDelays.size) {
            AppLogger.d("[OBL_QUERY:$queryId] Attempt $attempt: Querying Firestore")
            try {
                val querySnapshot = firestore.collection("applications")
                    .document(applicationId)
                    .collection("obligationRefinement")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await() // Await the result directly here

                AppLogger.d("[OBL_QUERY:$queryId] Query completed - isEmpty=${querySnapshot.isEmpty}, documents=${querySnapshot.documents.size}")

                if (!querySnapshot.isEmpty) {
                    val document = querySnapshot.documents[0]
                    logDocumentState(queryId, document) // Keep logging helper

                    val obligationRefinement = parseDocumentToObligationRefinement(document)

                    if (obligationRefinement != null) {
                        AppLogger.d("[OBL_QUERY:$queryId] Successfully parsed refinement: ${obligationRefinement.recordId}")
                        emit(Resource.Success(obligationRefinement)) // Emit success
                        return@flow // Exit flow on success
                    } else {
                        val parseErrorMsg = "BRE_103: Failed to parse document: ${document.id}"
                        AppLogger.e("[OBL_QUERY:$queryId] $parseErrorMsg")
                        
                        if (attempt == retryDelays.size) {
                            emit(Resource.Error(parseErrorMsg))
                            return@flow
                        }
                    }
                } else {
                    AppLogger.d("[OBL_QUERY:$queryId] No documents found - will retry in ${retryDelays[attempt - 1]}ms")
                    
                    if (attempt == retryDelays.size) {
                        val errorMsg = "BRE_102: No obligation refinements found after $attempt attempts"
                        AppLogger.e("[OBL_QUERY:$queryId] $errorMsg")
                        emit(Resource.Error(errorMsg))
                        return@flow
                    }
                }

            } catch (e: Exception) {
                AppLogger.e("[OBL_QUERY:$queryId] Error in attempt $attempt: ${e.message}", e)
                
                if (attempt == retryDelays.size) {
                    val errorCode = getFirestoreErrorCode(e)
                    val finalErrorMessage = "$errorCode: ${e.message ?: "Unknown error"}"
                    AppLogger.e("[OBL_QUERY:$queryId] Final error after all retries: $finalErrorMessage", e)
                    emit(Resource.Error(finalErrorMessage, e))
                    return@flow
                }
            }

            // Wait before the next retry (if not the last attempt)
            if (attempt < retryDelays.size) {
                delay(retryDelays[attempt - 1])
            }
        }
    }.flowOn(Dispatchers.IO) // Apply IO dispatcher to the flow


    /**
    * Convert BREInput to request map for API
    */
    private fun mapBREInputToRequestMap(breInput: BREInput): Map<String, Any?> {
        return mapOf(
            "applicationId" to breInput.applicationId,
            "timestamp" to breInput.timestamp.toString(),
            "userId" to breInput.userId,
            "version" to breInput.version,
            "personalInfo" to mapOf(
                "dateOfBirth" to breInput.personalInfo?.dateOfBirth?.toString(),
                "name" to breInput.personalInfo?.name,
                "email" to breInput.personalInfo?.email,
                "phone" to breInput.personalInfo?.alternatePhoneNumber,
                "address" to breInput.personalInfo?.address?.let { address ->
                    mapOf(
                        "state" to address.state,
                        "city" to address.city,
                        "postalCode" to address.postalCode,
                        "addressLine1" to address.addressLine1,
                        "addressLine2" to address.addressLine2,
                        "country" to address.country
                    )
                }
            ),
            "employmentDetails" to mapOf(
                "employmentType" to breInput.employmentDetails?.employmentType?.name,
                "monthlySalary" to breInput.employmentDetails?.monthlySalary,
                "monthlyEmi" to breInput.employmentDetails?.monthlyEmi,
                "employerName" to breInput.employmentDetails?.employerName,
                "workEmail" to breInput.employmentDetails?.workEmail,
                "department" to breInput.employmentDetails?.department,
                "designation" to breInput.employmentDetails?.designation
            ),
            "bureauData" to breInput.bureauData?.let { bureau ->
                mapOf(
                    "creditScore" to bureau.creditScore,
                    "writtenOffStatus" to bureau.writtenOffStatus,
                    "suitFiled" to bureau.suitFiled,
                    "delinquencyStatus" to bureau.delinquencyStatus,
                    "openAccounts" to bureau.openAccounts,
                    "totalAccounts" to bureau.totalAccounts,
                    "currentBalance" to bureau.currentBalance,
                    "totalLoanAmount" to bureau.totalLoanAmount,
                    "totalOverdueAmount" to bureau.totalOverdueAmount
                )
            },
            "panDetails" to breInput.panDetails?.let { pan ->
                mapOf(
                    "panNumber" to pan.panNumber,
                    "name" to pan.name,
                    "dateOfBirth" to pan.dateOfBirth?.toString(),
                    "isVerified" to pan.isVerified,
                    "verificationDate" to pan.verificationDate?.toString()
                )
            },
            "documentExtractions" to breInput.documentExtractions?.let { docs ->
                mapOf(
                    "salarySlip" to docs.salarySlip?.let { salary ->
                        mapOf(
                            "employerName" to salary.employerName,
                            "netSalary" to salary.netSalary,
                            "grossSalary" to salary.grossSalary,
                            "employeeName" to salary.employeeName,
                            "employeeId" to salary.employeeId
                        )
                    },
                    "bankStatement" to docs.bankStatement?.let { bank ->
                        mapOf(
                            "bankName" to bank.bankName,
                            "accountNumber" to bank.accountNumber,
                            "totalCredits" to bank.totalCredits,
                            "accountHolderName" to bank.accountHolderName
                        )
                    },
                    "incomeTaxReturn" to docs.incomeTaxReturn?.let { itr ->
                        mapOf(
                            "panNumber" to itr.panNumber,
                            "totalGrossIncome" to itr.totalGrossIncome,
                            "taxableIncome" to itr.taxableIncome,
                            "taxPaid" to itr.taxPaid
                        )
                    }
                )
            },
            "llmRecalculatedObligation" to breInput.llmRecalculatedObligation
        )
    }


    /**
     * Trigger the recalculation of obligations via Gemini (Updated Error Handling & Correct Metadata Call)
     */
    override suspend fun triggerObligationRecalculation(
        obligationRefinement: ObligationRefinement,
        tradelineItems: List<TradelineItem>
    ): Flow<Resource<ObligationRefinement>> = flow {
        emit(Resource.Loading())
        val refinementId = obligationRefinement.recordId
        val applicationId = obligationRefinement.applicationId

        try {
            AppLogger.d("Triggering obligation recalculation for refinement: $refinementId")
            
            // Record LLM interaction initiated event
            metadataRepository.recordLlmInteractionEvent(
                applicationId = applicationId,
                llmInteraction = LlmInteractionEvent(
                    llmCallType = "OBLIGATION_RECALC_GEMINI",
                    status = "INITIATED",
                    modelUsed = geminiDocumentService.modelName
                )
            ).collect()

            // Update refinement status to PENDING (best effort)
            updateRefinementStatus(refinementId, applicationId, LlmProcessingStatus.PENDING)

            // Call Gemini Service with timeout
            val geminiResult = withTimeoutOrNull(45000L) {
                geminiDocumentService.recalculateObligation(
                    tradelines = tradelineItems,
                    userProvidedEmis = obligationRefinement.userProvidedEmis,
                    userComments = obligationRefinement.userComments
                )
            }

            if (geminiResult == null) {
                throw TimeoutException("Gemini processing timed out after 45 seconds")
            }

            // Process result
            val success = geminiResult["success"] as? Boolean ?: false
            if (success) {
                val recalculatedObligation = (geminiResult["recalculatedObligation"] as? Number)?.toInt()
                val excludedLoansRaw = geminiResult["excludedLoans"] as? List<*> ?: emptyList<Map<String, String>>()

                if (recalculatedObligation == null) {
                    throw Exception("Invalid data in Gemini response: recalculatedObligation is null")
                }

                val excludedLoans = excludedLoansRaw.mapNotNull { loanMap ->
                    if (loanMap is Map<*, *>) {
                        ExcludedLoan(
                            tradelineId = loanMap["tradelineId"] as? String ?: "",
                            reason = loanMap["reason"] as? String ?: ""
                        )
                    } else null
                }

                // Update Firestore with SUCCESS results
                updateRefinementWithResults(
                    refinementId = refinementId,
                    applicationId = applicationId,
                    status = LlmProcessingStatus.SUCCESS,
                    recalculatedObligation = recalculatedObligation,
                    excludedLoans = excludedLoans
                )

                // Record LLM success event
                // Update the local object with results BEFORE emitting
                val updatedRefinement = obligationRefinement.copy(
                    llmRecalculatedObligation = recalculatedObligation,
                    llmExcludedLoans = excludedLoans,
                    llmProcessingStatus = LlmProcessingStatus.SUCCESS,
                    llmProcessedAt = LocalDateTime.now()
                )

                // Save the updated refinement to Firestore
                updateRefinementWithResults(
                    refinementId = refinementId,
                    applicationId = applicationId,
                    status = LlmProcessingStatus.SUCCESS,
                    recalculatedObligation = recalculatedObligation,
                    excludedLoans = excludedLoans
                )

                // Record LLM success event
                metadataRepository.recordLlmInteractionEvent(
                    applicationId = applicationId,
                    llmInteraction = LlmInteractionEvent(
                        llmCallType = "OBLIGATION_RECALC_GEMINI",
                        status = "COMPLETED",
                        modelUsed = geminiDocumentService.modelName
                    )
                ).collect()

                // Update application document with llmRecalculatedObligation
                if (recalculatedObligation != null) {
                    try {
                        firestore.collection("applications").document(applicationId)
                            .update("breInputData.llmRecalculatedObligation", recalculatedObligation)
                            .await()
                        AppLogger.d("Updated breInputData.llmRecalculatedObligation in main app doc.")
                    } catch (updateError: Exception) {
                        AppLogger.w("Failed to update llmRecalculatedObligation in main app doc: ${updateError.message}")
                    }
                }

                emit(Resource.Success(updatedRefinement))

            } else {
                val errorMessage = geminiResult["error"] as? String ?: "Unknown Gemini error during recalculation"
                throw Exception(errorMessage)
            }

        } catch (e: Exception) {
            val errorMessage = "Error triggering obligation recalculation: ${e.message}"
            AppLogger.e(errorMessage, e)

            // Attempt to update Firestore status to FAILED (best effort)
            try {
                updateRefinementStatus(refinementId, applicationId, LlmProcessingStatus.FAILED)
                metadataRepository.recordLlmInteractionEvent(
                    applicationId = applicationId,
                    llmInteraction = LlmInteractionEvent(
                        llmCallType = "OBLIGATION_RECALC_GEMINI",
                        status = "FAILED",
                        failureReason = errorMessage,
                        modelUsed = geminiDocumentService.modelName
                    )
                ).collect()
            } catch (updateError: Exception) {
                AppLogger.e("Failed to update refinement status/metadata to FAILED: ${updateError.message}", updateError)
            }
            emit(Resource.Error(errorMessage))
        }
    }

    // ... (keep other functions like getPanFromApplication, updateRefinementStatus, updateRefinementWithResults, parseDocumentToObligationRefinement, etc.) ...
     // --- NEW HELPER to get PAN ---
    // --- UPDATED HELPER to get PAN (Explicit Cache-First Logic) ---
    private suspend fun getPanFromApplication(applicationId: String): String? {
        AppLogger.d("[getPanFromApplication] Attempting to find PAN for app: $applicationId")
        try {
            // --- Step 1: Check PreferencesDataSource SPECIFIC PAN Cache FIRST ---
            AppLogger.d("[getPanFromApplication] Checking PreferencesDataSource specific PAN cache...")
            // Assuming preferencesDataSource is injected in the class constructor
            val cachedPan = preferencesDataSource.getPanNumberForApplication(applicationId)
            if (!cachedPan.isNullOrBlank()) {
                AppLogger.i("[getPanFromApplication] Found PAN in Preferences specific cache: $cachedPan")
                return cachedPan // Return cached PAN if found
            }
            AppLogger.d("[getPanFromApplication] PAN not found in Preferences specific cache.")

            // --- Step 2: Fetch LoanApplication object (uses its own internal caching/Firestore) ---
            AppLogger.d("[getPanFromApplication] Fetching full LoanApplication object via repository...")
            val appResource = loanRepository.getApplication(applicationId).first()

            // Check if the resource is Success before accessing data
            if (appResource is Resource.Success) {
                AppLogger.d("[getPanFromApplication] Successfully fetched LoanApplication object.")
                val applicationData = appResource.data // Get the LoanApplication data

                // --- Step 3: Check Direct `panNumber` Field in the Fetched Object ---
                val directPan = applicationData.panNumber // Check the top-level field
                if (!directPan.isNullOrBlank()) {
                    AppLogger.d("[getPanFromApplication] Found direct PAN in fetched application object: $directPan")
                    // Cache the found PAN back to preferences for next time
                    preferencesDataSource.savePanNumberForApplication(applicationId, directPan)
                    return directPan // Return if found directly
                }
                AppLogger.d("[getPanFromApplication] Direct panNumber field in fetched object is null or blank.")

                // --- Step 4: Fallback to Nested `panDetails.panNumber` Field ---
                val nestedPan = applicationData.panDetails?.panNumber?.takeIf { it.isNotBlank() }
                if (!nestedPan.isNullOrBlank()) {
                    AppLogger.d("[getPanFromApplication] Found PAN in nested panDetails: $nestedPan")
                    // Cache the found PAN back to preferences for next time
                    preferencesDataSource.savePanNumberForApplication(applicationId, nestedPan)
                    return nestedPan // Return if found in nested details
                }
                AppLogger.d("[getPanFromApplication] Nested panDetails.panNumber field is null or blank.")

                // If neither is found in the fetched application object
                AppLogger.w("[getPanFromApplication] PAN not found in direct field or nested panDetails within fetched application object for app $applicationId")

            } else if (appResource is Resource.Error) {
                AppLogger.w("[getPanFromApplication] Failed to get application object $applicationId to extract PAN: ${appResource.message}")
            }
        } catch (e: Exception) {
            AppLogger.e("[getPanFromApplication] Error getting PAN for application $applicationId: ${e.message}", e)
        }
        // Return null if not found or error occurred in any step
        AppLogger.e("[getPanFromApplication] Could not find PAN for application $applicationId from any source (Cache, Direct Field, Nested Field).")
        return null
    }


    // --- NEW HELPER to update status ---
    private suspend fun updateRefinementStatus(refinementId: String, applicationId: String, status: LlmProcessingStatus) {
        try {
            val statusUpdateMap = mapOf(
                "llmProcessingStatus" to status.name,
                "llmProcessedAt" to DateConverter.toTimestamp(LocalDateTime.now())
                // "lastUpdatedAt" can also be updated here if it's directly on the refinement document.
                // If "lastUpdatedAt" is only on the main application document, it's handled separately.
            )

            firestore.collection("applications")
                .document(applicationId)
                .collection("obligationRefinement")
                .document(refinementId)
                .set(statusUpdateMap, SetOptions.merge()) // USE SET WITH MERGE
                .await()
            
            // If you also need to update a timestamp on the main application document:
            firestore.collection("applications").document(applicationId)
                 .update("lastUpdatedAt", DateConverter.toTimestamp(LocalDateTime.now()))
                 .await()

            AppLogger.d("Updated refinement $refinementId status to $status and application lastUpdatedAt.")
        } catch (e: Exception) {
             if (e is FirebaseFirestoreException) {
                 AppLogger.e("Firestore error updating refinement $refinementId status to $status: Code=${e.code}, ${e.message}", e)
             } else {
                AppLogger.e("Failed to update refinement $refinementId status to $status: ${e.message}", e)
             }
             // Avoid rethrowing here to let the primary operation's error handling take precedence
        }
    }

    // --- NEW HELPER to update with results ---
    private suspend fun updateRefinementWithResults(
        refinementId: String,
        applicationId: String,
        status: LlmProcessingStatus,
        recalculatedObligation: Int?,
        excludedLoans: List<ExcludedLoan>
    ) {
        try {
            val updateData = mapOf(
                "recordId" to refinementId, // Add recordId to ensure document creation works
                "applicationId" to applicationId, // Add applicationId to ensure document creation works
                "llmProcessingStatus" to status.name,
                "llmProcessedAt" to DateConverter.toTimestamp(LocalDateTime.now()),
                "llmRecalculatedObligation" to recalculatedObligation,
                "llmExcludedLoans" to excludedLoans.map { mapOf("tradelineId" to it.tradelineId, "reason" to it.reason) },
                "lastUpdatedAt" to DateConverter.toTimestamp(LocalDateTime.now())
            )
            
            // Use set with merge option instead of update
            firestore.collection("applications")
                .document(applicationId)
                .collection("obligationRefinement")
                .document(refinementId)
                .set(updateData, SetOptions.merge())
                .await()

            // Update main application doc timestamp as well
            firestore.collection("applications").document(applicationId)
                .update("lastUpdatedAt", DateConverter.toTimestamp(LocalDateTime.now())).await()
                
            // Also update the monthlyEmi in employmentDetails for broader visibility of recalculated obligation
            if (recalculatedObligation != null) {
                firestore.collection("applications")
                    .document(applicationId)
                    .update("employmentDetails.monthlyEmi", recalculatedObligation.toDouble())
                    .await()
                AppLogger.d("Also updated employmentDetails.monthlyEmi with recalculated obligation: $recalculatedObligation")
            }

            AppLogger.d("Updated refinement $refinementId with results: Obligation=$recalculatedObligation")
        } catch (e: Exception) {
            // Log Firestore exceptions specifically
            if (e is FirebaseFirestoreException) {
                AppLogger.e("Firestore error updating refinement $refinementId with results: Code=${e.code}, ${e.message}", e)
            } else {
                AppLogger.e("Failed to update refinement $refinementId with results: ${e.message}", e)
            }
            // Do not rethrow here, allow main function to handle primary error
        }
    }



     /**
     * Check the status of an ongoing obligation recalculation
     */
    override suspend fun checkRecalculationStatus(
        refinementId: String
    ): Flow<Resource<ObligationRefinement>> = flow {
        emit(Resource.Loading())

        try {
            AppLogger.d("Checking recalculation status for refinement: $refinementId")

            // Get the latest state of the refinement
            var refinementResource: Resource<ObligationRefinement>? = null
            getObligationRefinement(refinementId).collect { result ->
                refinementResource = result
            }

            if (refinementResource is Resource.Error) {
                val errorMessage = "Failed to get refinement data: ${(refinementResource as Resource.Error).message}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
                return@flow
            }

            val refinement = (refinementResource as? Resource.Success<ObligationRefinement>)?.data
            if (refinement == null) {
                emit(Resource.Error("Failed to get refinement data"))
                return@flow
            }

            // Call the backend LLM API to check status
            // For this implementation, we'll simulate the API call
            try {
                // In a real implementation, this would be:
                // val response = llmProcessingApi.checkRecalculationStatus(refinementId).await()

                // Simulate API response with mock data
                // In a real implementation, this would parse the API response
                val mockSuccess = true
                val mockRecalculatedObligation = refinement.userProvidedEmis.values.sum() - 5000 // Example: slightly lower than user provided
                val mockExcludedLoans = listOf(
                    ExcludedLoan(
                        tradelineId = refinement.userProvidedEmis.keys.firstOrNull() ?: "unknown",
                        reason = "Loan appears to be closed based on recent transactions"
                    )
                )

                if (mockSuccess) {
                    // Update the refinement with recalculation results
                    val updatedRefinement = refinement.copy(
                        llmRecalculatedObligation = mockRecalculatedObligation,
                        llmExcludedLoans = mockExcludedLoans,
                        llmProcessingStatus = LlmProcessingStatus.SUCCESS,
                        llmProcessedAt = LocalDateTime.now()
                    )

                    // Save the updated refinement
                    saveObligationRefinement(updatedRefinement).collect()

                    // Record LLM interaction completed event
                    metadataRepository.recordLlmInteractionEvent(
                        applicationId = refinement.applicationId,
                        llmInteraction = LlmInteractionEvent(
                            llmCallType = "OBLIGATION_RECALC",
                            status = "COMPLETED",
                            modelUsed = "gemini-pro" // Or your actual model
                        )
                    ).collect()

                    // Also record obligation event
                    metadataRepository.recordObligationRefinementEvent(
                        applicationId = refinement.applicationId,
                        obligationEvent = com.loansai.unassisted.domain.model.ObligationRefinementEvent(
                            refinementRecordId = refinementId,
                            status = "LLM_SUCCESS"
                        )
                    ).collect()

                    emit(Resource.Success(updatedRefinement))
                } else {
                    // Update the refinement with failure status
                    val updatedRefinement = refinement.copy(
                        llmProcessingStatus = LlmProcessingStatus.FAILED,
                        llmProcessedAt = LocalDateTime.now()
                    )

                    // Save the updated refinement
                    saveObligationRefinement(updatedRefinement).collect()

                    // Record LLM interaction failed event
                    metadataRepository.recordLlmInteractionEvent(
                        applicationId = refinement.applicationId,
                        llmInteraction = LlmInteractionEvent(
                            llmCallType = "OBLIGATION_RECALC",
                            status = "FAILED",
                            failureReason = "LLM processing failed"
                        )
                    ).collect()

                    // Also record obligation event
                    metadataRepository.recordObligationRefinementEvent(
                        applicationId = refinement.applicationId,
                        obligationEvent = com.loansai.unassisted.domain.model.ObligationRefinementEvent(
                            refinementRecordId = refinementId,
                            status = "LLM_FAILED"
                        )
                    ).collect()

                    emit(Resource.Error("LLM processing failed"))
                }
            } catch (e: Exception) {
                val errorMessage = "Error checking recalculation status: ${e.message}"
                AppLogger.e(errorMessage, e)

                // Update the refinement with failure status
                val updatedRefinement = refinement.copy(
                    llmProcessingStatus = LlmProcessingStatus.FAILED,
                    llmProcessedAt = LocalDateTime.now()
                )

                // Save the updated refinement
                saveObligationRefinement(updatedRefinement).collect()

                // Record LLM interaction failed event
                metadataRepository.recordLlmInteractionEvent(
                    applicationId = refinement.applicationId,
                    llmInteraction = LlmInteractionEvent(
                        llmCallType = "OBLIGATION_RECALC",
                        status = "FAILED",
                        failureReason = e.message
                    )
                ).collect()

                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error checking recalculation status: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

     // --- parseDocumentToObligationRefinement remains the same ---
    private fun parseDocumentToObligationRefinement(document: com.google.firebase.firestore.DocumentSnapshot): ObligationRefinement? {
        val parseId = UUID.randomUUID().toString().substring(0, 8)
        try {
            AppLogger.d("[OBL_PARSE:$parseId] Parsing document: ${document.id}")
            
            // Parse recordId - use document id if recordId field is missing
            val recordId = document.getString("recordId") ?: document.id
            val applicationId = document.getString("applicationId") ?: ""
            
            AppLogger.d("[OBL_PARSE:$parseId] Basic fields: recordId=$recordId, applicationId=$applicationId")

            // Parse dates with proper error handling
            val createdAt = DateConverter.parseFirestoreValue(document.get("createdAt"))
                ?: LocalDateTime.now().also {
                    AppLogger.w("[OBL_PARSE:$parseId] Failed to parse createdAt, using current time")
                }
            
            val llmProcessedAt = DateConverter.parseFirestoreValue(document.get("llmProcessedAt"))

            // Parse user provided EMIs - fix the parsing to handle Number types properly
            val userProvidedEmisRaw = document.get("userProvidedEmis")
            AppLogger.d("[OBL_PARSE:$parseId] userProvidedEmisRaw: $userProvidedEmisRaw")
            
            val userProvidedEmis = when (userProvidedEmisRaw) {
                is Map<*, *> -> {
                    val result = mutableMapOf<String, Int>()
                    userProvidedEmisRaw.entries.forEach { entry ->
                        val key = entry.key as? String ?: ""
                        val value = when (val v = entry.value) {
                            is Number -> v.toInt()
                            is String -> v.toIntOrNull() ?: 0
                            else -> 0
                        }
                        if (key.isNotEmpty() && value > 0) {
                            result[key] = value
                        }
                    }
                    result
                }
                else -> emptyMap()
            }

            // Parse user comments
            val userComments = document.getString("userComments")

            // Parse LLM recalculated obligation - handle both Number and direct value
            val llmRecalculatedObligation = when (val value = document.get("llmRecalculatedObligation")) {
                is Number -> value.toInt()
                is Long -> value.toInt()
                else -> null
            }
            
            AppLogger.d("[OBL_PARSE:$parseId] Core data: llmRecalculatedObligation=$llmRecalculatedObligation")

            // Parse LLM excluded loans
            val llmExcludedLoansRaw = document.get("llmExcludedLoans")
            val llmExcludedLoans = when (llmExcludedLoansRaw) {
                is List<*> -> llmExcludedLoansRaw.mapNotNull { loanMap ->
                    if (loanMap is Map<*, *>) {
                        ExcludedLoan(
                            tradelineId = loanMap["tradelineId"] as? String ?: "",
                            reason = loanMap["reason"] as? String ?: ""
                        )
                    } else {
                        null
                    }
                }
                else -> emptyList()
            }

            // Parse LLM processing status
            val llmProcessingStatusString = document.getString("llmProcessingStatus") ?: LlmProcessingStatus.PENDING.name
            val llmProcessingStatus = try {
                LlmProcessingStatus.valueOf(llmProcessingStatusString)
            } catch (e: Exception) {
                AppLogger.e("[OBL_PARSE:$parseId] Invalid LLM processing status: $llmProcessingStatusString", e)
                LlmProcessingStatus.PENDING
            }

            AppLogger.d("[OBL_PARSE:$parseId] Status info: llmProcessingStatus=$llmProcessingStatus")

            // Create and validate the obligation refinement object
            val obligationRefinement = ObligationRefinement(
                recordId = recordId,
                applicationId = applicationId,
                createdAt = createdAt,
                userProvidedEmis = userProvidedEmis,
                userComments = userComments,
                llmRecalculatedObligation = llmRecalculatedObligation,
                llmExcludedLoans = llmExcludedLoans,
                llmProcessingStatus = llmProcessingStatus,
                llmProcessedAt = llmProcessedAt
            )
            
            AppLogger.d("[OBL_PARSE:$parseId] Successfully created obligation refinement object")
            return obligationRefinement
            
        } catch (e: Exception) {
            val errorCode = BREErrorCodes.OBLIGATION_REFINEMENT_PARSE_ERROR
            AppLogger.e("[OBL_PARSE:$parseId] $errorCode: ${e.message}", e)
            return null
        }
    }

    /**
    * Log document state for debugging
    */
    private fun logDocumentState(queryId: String, document: com.google.firebase.firestore.DocumentSnapshot) {
        val fields = document.data?.keys?.toList() ?: emptyList()
        AppLogger.d("[OBL_QUERY:$queryId] Document fields: $fields")
        
        // Log key fields
        val recordId = document.getString("recordId")
        val applicationId = document.getString("applicationId")
        val llmProcessingStatus = document.getString("llmProcessingStatus")
        val llmRecalculatedObligation = document.getLong("llmRecalculatedObligation")
        
        AppLogger.d("[OBL_QUERY:$queryId] Key values: recordId=$recordId, " +
                "applicationId=$applicationId, " +
                "llmProcessingStatus=$llmProcessingStatus, " +
                "llmRecalculatedObligation=$llmRecalculatedObligation")
    }

    /**
    * Get specific error code for Firestore errors
    */
    private fun getFirestoreErrorCode(e: Exception): String {
        return when {
            e is com.google.firebase.firestore.FirebaseFirestoreException -> {
                when (e.code) {
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND -> BREErrorCodes.OBLIGATION_REFINEMENT_NOT_FOUND
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> BREErrorCodes.BRE_API_AUTHENTICATION_FAILED
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> BREErrorCodes.FLOW_COLLECTION_TIMEOUT
                    else -> BREErrorCodes.FLOW_EXCEPTION
                }
            }
            e.message?.contains("timeout", true) == true -> BREErrorCodes.FLOW_COLLECTION_TIMEOUT
            else -> BREErrorCodes.FLOW_EXCEPTION
        }
    }

}