package com.loansai.unassisted.data.repository

import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.data.model.LoanCalculationRequest
import com.loansai.unassisted.data.model.OfferAcceptanceRequest
import com.loansai.unassisted.data.model.toLoanApplication
import com.loansai.unassisted.data.model.toLoanOffer
import com.loansai.unassisted.data.model.toPersonalInfoDto
import com.loansai.unassisted.data.model.toEmploymentDetailsDto
import com.loansai.unassisted.data.remote.api.LoanApi
import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.DecisionStatus
import com.loansai.unassisted.domain.model.EmploymentDetails
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.LoanOffer
import com.loansai.unassisted.domain.model.LoanOfferUI
import com.loansai.unassisted.domain.model.PersonalInfo
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.MetadataRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import com.loansai.unassisted.BuildConfig
import kotlinx.coroutines.flow.first
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import com.loansai.unassisted.domain.model.AddressType
import com.loansai.unassisted.domain.model.ScreenVisit
import com.loansai.unassisted.domain.model.SectionTiming
import kotlinx.coroutines.flow.Flow
import com.loansai.unassisted.util.DateConverter
import java.util.UUID
import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.domain.model.*
import com.loansai.unassisted.domain.model.MetadataEventType
import java.time.LocalDate


/**
 * Implementation of LoanRepository that uses API and in-memory caching
 * Updated for v1.5.0 to support Bureau Confirmation and Obligation Refinement
 */
@Singleton
class LoanRepositoryImpl @Inject constructor(
    private val loanApi: LoanApi,
    private val preferencesDataSource: PreferencesDataSource,
    private val firestore: FirebaseFirestore,
    private val metadataRepository: MetadataRepository
) : LoanRepository {

    // In-memory cache of the current loan application
    private val _currentApplication = MutableStateFlow<LoanApplication?>(null)
    private val sectionTimingMap = mutableMapOf<String, SectionTiming>()

    /**
     * Create a new loan application
     */
    override suspend fun createApplication(userId: String): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())
        
        try {
            // Create application directly in Firestore
            val applicationId = "app-${System.currentTimeMillis()}"
            val newApplication = LoanApplication(
                id = applicationId,
                userId = userId,
                createdAt = LocalDateTime.now(),
                lastUpdatedAt = LocalDateTime.now(),
                applicationStatus = ApplicationStatus.CREATED,
                currentStep = ApplicationStep.PAN_VERIFICATION,
                completedSteps = emptyList()
            )
            
            
            // Convert to a Map for Firestore
            val applicationMap = mapOf(
                "id" to newApplication.id,
                "userId" to newApplication.userId,
                "mobileNumber" to userId, 
                "createdAt" to DateConverter.toTimestamp(newApplication.createdAt),
                "lastUpdatedAt" to DateConverter.toTimestamp(newApplication.lastUpdatedAt),
                "applicationStatus" to newApplication.applicationStatus.name,
                "currentStep" to newApplication.currentStep.name,
                "completedSteps" to newApplication.completedSteps.map { it.name },
                
                // Single-Pass BRE Fields (new)
                "breInputData" to mapOf(
                    "applicationId" to applicationId,
                    "timestamp" to DateConverter.toTimestamp(LocalDateTime.now())
                ),
                "breOutputData" to null,
                "loanOffer" to null
            )
            
            // Store in Firestore
            val result = suspendCoroutine<Boolean> { continuation ->
                firestore.collection("applications")
                    .document(applicationId)
                    .set(applicationMap)
                    .addOnSuccessListener {
                        continuation.resume(true)
                    }
                    .addOnFailureListener { e ->
                        AppLogger.e("Error creating application in Firestore: ${e.message}", e)
                        continuation.resume(false)
                    }
            }
            
            if (result) {
                // Cache the application locally
                _currentApplication.value = newApplication
                preferencesDataSource.cacheApplicationData(newApplication)
                preferencesDataSource.saveCurrentApplicationId(newApplication.id)
                
                emit(Resource.Success(newApplication))
            } else {
                // Create a fallback application if Firestore fails
                val fallbackApplication = createFallbackApplication(userId)
                _currentApplication.value = fallbackApplication
                preferencesDataSource.cacheApplicationData(fallbackApplication)
                preferencesDataSource.saveCurrentApplicationId(fallbackApplication.id)
                
                emit(Resource.Success(fallbackApplication))
            }
        } catch (e: Exception) {
            val errorMessage = "Error creating application: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // Create a fallback application as a last resort
            val fallbackApplication = createFallbackApplication(userId)
            _currentApplication.value = fallbackApplication
            preferencesDataSource.cacheApplicationData(fallbackApplication)
            preferencesDataSource.saveCurrentApplicationId(fallbackApplication.id)
            
            emit(Resource.Success(fallbackApplication))
        }
    }

    /**
    * Create a fallback application for development mode
    */
    private fun createFallbackApplication(userId: String): LoanApplication {
        return LoanApplication(
            id = "fallback-${System.currentTimeMillis()}",
            userId = userId,
            createdAt = LocalDateTime.now(),
            lastUpdatedAt = LocalDateTime.now(),
            applicationStatus = ApplicationStatus.CREATED,
            currentStep = ApplicationStep.PAN_VERIFICATION,
            completedSteps = emptyList()
        )
    }
    
    /**
     * Get all applications for a user
     */
    override suspend fun getUserApplications(userId: String): Flow<Resource<List<LoanApplication>>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try API first
            try {
                val response = loanApi.getUserApplications(userId)
                
                if (response.isSuccessful && response.body() != null) {
                    val applications = response.body()!!.map { it.toLoanApplication() }
                    emit(Resource.Success(applications))
                    
                    // Cache the most recent application
                    if (applications.isNotEmpty() && _currentApplication.value == null) {
                        val mostRecent = applications.maxByOrNull { it.lastUpdatedAt }
                        if (mostRecent != null) {
                            _currentApplication.value = mostRecent
                            preferencesDataSource.saveCurrentApplicationId(mostRecent.id)
                            preferencesDataSource.cacheApplicationData(mostRecent)
                        }
                    }
                    return@flow
                }
            } catch (e: Exception) {
                AppLogger.e("API error: ${e.message}", e)
                // Continue to Firestore fallback
            }
            
            // Firestore fallback
            AppLogger.d("Falling back to Firestore for user: $userId")
            val applications = mutableListOf<LoanApplication>()
            
            // Query Firestore directly
            val docs = firestore.collection("applications")
                .whereEqualTo("userId", userId)
                .get()
                .await()
                
            for (doc in docs.documents) {
                try {
                    // Convert Firestore document to application
                    val app = parseFirestoreDocumentToApplication(doc)
                    if (app != null) {
                        applications.add(app)
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error parsing document: ${e.message}", e)
                }
            }
            
            if (applications.isNotEmpty()) {
                emit(Resource.Success(applications))
                
                // Cache the most recent application
                val mostRecent = applications.maxByOrNull { it.lastUpdatedAt }
                if (mostRecent != null) {
                    _currentApplication.value = mostRecent
                    preferencesDataSource.saveCurrentApplicationId(mostRecent.id)
                    preferencesDataSource.cacheApplicationData(mostRecent)
                }
            } else {
                emit(Resource.Error("No applications found"))
            }
            
        } catch (e: Exception) {
            val errorMessage = "Error getting user applications: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Update the personal information for an application
     */
    override suspend fun updatePersonalInfo(
        applicationId: String,
        personalInfo: PersonalInfo
    ): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get the current application first
            val currentApplication = _currentApplication.value
            
            if (currentApplication == null) {
                // Try to load the application if it's not in memory
                val appResource = getApplication(applicationId).first()
                if (appResource is Resource.Error) {
                    emit(Resource.Error("Failed to get application: ${appResource.message}"))
                    return@flow
                }
            }
            
            // Update local model
            val updatedApplication = _currentApplication.value?.copy(
                personalInfo = personalInfo,
                lastUpdatedAt = LocalDateTime.now()
            ) ?: return@flow

            // Update in-memory cache
            _currentApplication.value = updatedApplication
            
            // Update cached application data
            preferencesDataSource.cacheApplicationData(updatedApplication)
            
            // Update in Firestore directly
            try {
                // Convert PersonalInfo to data for Firestore
                val personalInfoMap = mapOf(
                    "personalInfo" to mapOf(
                        "name" to personalInfo.name,
                        "dateOfBirth" to personalInfo.dateOfBirth?.toString(),
                        "email" to personalInfo.email,
                        "gender" to personalInfo.gender?.name,
                        "maritalStatus" to personalInfo.maritalStatus?.name,
                        "address" to mapOf(
                            "addressLine1" to personalInfo.address.addressLine1,
                            "addressLine2" to personalInfo.address.addressLine2,
                            "city" to personalInfo.address.city,
                            "state" to personalInfo.address.state,
                            "postalCode" to personalInfo.address.postalCode,
                            "country" to personalInfo.address.country,
                            "addressType" to personalInfo.address.addressType.name
                        ),
                        "alternatePhoneNumber" to personalInfo.alternatePhoneNumber
                    ),
                    "lastUpdatedAt" to DateConverter.toTimestamp(LocalDateTime.now())
                )
                
                // Update Firestore
                // Get mobile number from the current application (userId should be the phone number)
                val mobileNumber = _currentApplication.value?.userId ?: ""

                // Add mobile number to the map
                val updatedPersonalInfoMap = personalInfoMap + mapOf(
                    "mobileNumber" to mobileNumber // Add mobile number at root level
                )

                // Update in Firestore
                firestore.collection("applications")
                    .document(applicationId)
                    .update(updatedPersonalInfoMap)
                    .await()
                    
                AppLogger.d("Successfully updated personal info in Firestore for application: $applicationId")
                
                // If Firestore update successful, emit success and return early
                emit(Resource.Success(updatedApplication))
                return@flow
                
            } catch (e: Exception) {
                AppLogger.e("Error updating personal info in Firestore: ${e.message}", e)
                // Continue with API approach
            }
            
            // Try API as fallback
            val personalInfoDto = personalInfo.toPersonalInfoDto()
            val response = loanApi.updatePersonalInfo(applicationId, personalInfoDto)
            
            if (response.isSuccessful && response.body() != null) {
                val apiUpdatedApplication = response.body()!!.toLoanApplication()
                _currentApplication.value = apiUpdatedApplication
                preferencesDataSource.cacheApplicationData(apiUpdatedApplication)
                emit(Resource.Success(apiUpdatedApplication))
            } else {
                // We already updated the local cache, so consider it a success
                // but log the API error
                AppLogger.w("API update failed: ${response.message()}")
                emit(Resource.Success(updatedApplication))
            }
        } catch (e: Exception) {
            val errorMessage = "Error updating personal info: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Update the employment details for an application
     */
    override suspend fun updateEmploymentDetails(
        applicationId: String,
        employmentDetails: EmploymentDetails
    ): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get the current application first
            val currentApplication = _currentApplication.value
            
            if (currentApplication == null) {
                // Try to load the application if it's not in memory
                val appResource = getApplication(applicationId).first()
                if (appResource is Resource.Error) {
                    emit(Resource.Error("Failed to get application: ${appResource.message}"))
                    return@flow
                }
            }
            
            // Update local model
            val updatedApplication = _currentApplication.value?.copy(
                employmentDetails = employmentDetails,
                lastUpdatedAt = LocalDateTime.now()
            ) ?: return@flow
            
            // Update in-memory cache
            _currentApplication.value = updatedApplication
            
            // Update cached application data
            preferencesDataSource.cacheApplicationData(updatedApplication)
            
            // Update in Firestore directly
            try {
                // Convert EmploymentDetails to map for Firestore
                val employmentDetailsMap = mapOf(
                    "employmentDetails" to mapOf(
                        "employmentType" to employmentDetails.employmentType.name,
                        "employerName" to employmentDetails.employerName,
                        "employerId" to employmentDetails.employerId,
                        "designation" to employmentDetails.designation,
                        "department" to employmentDetails.department,
                        "employeeId" to employmentDetails.employeeId,
                        "workEmail" to employmentDetails.workEmail,
                        "monthlySalary" to employmentDetails.monthlySalary,
                        "monthlyEmi" to employmentDetails.monthlyEmi,
                        "officeAddress" to employmentDetails.officeAddress?.let { address ->
                            mapOf(
                                "addressLine1" to address.addressLine1,
                                "addressLine2" to address.addressLine2,
                                "city" to address.city,
                                "state" to address.state,
                                "postalCode" to address.postalCode,
                                "country" to address.country,
                                "addressType" to address.addressType.name
                            )
                        }
                    ),
                    "lastUpdatedAt" to DateConverter.toTimestamp(LocalDateTime.now())
                )
                
                // Update Firestore
                firestore.collection("applications")
                    .document(applicationId)
                    .update(employmentDetailsMap)
                    .await()
                    
                AppLogger.d("Successfully updated employment details in Firestore: $applicationId")
                
                // If Firestore update successful, emit success and return early
                emit(Resource.Success(updatedApplication))
                return@flow
                
            } catch (e: Exception) {
                AppLogger.e("Error updating employment details in Firestore: ${e.message}", e)
                // Continue with API approach as fallback
            }
            
            // Existing API approach as fallback
            val employmentDetailsDto = employmentDetails.toEmploymentDetailsDto()
            val response = loanApi.updateEmploymentDetails(applicationId, employmentDetailsDto)
            
            if (response.isSuccessful && response.body() != null) {
                val apiUpdatedApplication = response.body()!!.toLoanApplication()
                _currentApplication.value = apiUpdatedApplication
                preferencesDataSource.cacheApplicationData(apiUpdatedApplication)
                emit(Resource.Success(apiUpdatedApplication))
            } else {
                // We already updated the local cache and Firestore, so consider it a success
                // but log the API error
                AppLogger.w("API update failed: ${response.message()}")
                emit(Resource.Success(updatedApplication))
            }
        } catch (e: Exception) {
            val errorMessage = "Error updating employment details: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Update the application status
     */
    override suspend fun updateApplicationStatus(
        applicationId: String,
        status: ApplicationStatus
    ): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get the current application first
            val currentApplication = _currentApplication.value
            
            if (currentApplication == null) {
                // Try to load the application if it's not in memory
                val appResource = getApplication(applicationId).first()
                if (appResource is Resource.Error) {
                    emit(Resource.Error("Failed to get application: ${appResource.message}"))
                    return@flow
                }
            }
            
            // Try to update via Firestore first
            try {
                val updateData = mapOf(
                    "applicationStatus" to status.name,
                    "lastUpdatedAt" to DateConverter.toTimestamp(LocalDateTime.now())
                )
                
                firestore.collection("applications")
                    .document(applicationId)
                    .update(updateData)
                    .await()
                
                // Update local model
                val updatedApplication = _currentApplication.value?.copy(
                    applicationStatus = status,
                    lastUpdatedAt = LocalDateTime.now()
                )
                
                if (updatedApplication != null) {
                    // Update in-memory cache
                    _currentApplication.value = updatedApplication
                    
                    // Update cached application data
                    preferencesDataSource.cacheApplicationData(updatedApplication)
                    
                    emit(Resource.Success(updatedApplication))
                    return@flow
                }
            } catch (e: Exception) {
                AppLogger.e("Error updating status in Firestore: ${e.message}", e)
                // Continue with API approach
            }
            
            // API approach as fallback
            val response = loanApi.updateApplicationStatus(applicationId, status.name)
            
            if (response.isSuccessful && response.body() != null) {
                val updatedApplication = response.body()!!.toLoanApplication()
                
                // Update in-memory cache
                _currentApplication.value = updatedApplication
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(updatedApplication)
                
                emit(Resource.Success(updatedApplication))
            } else {
                // Even if API fails, update the local model
                val updatedApplication = _currentApplication.value?.copy(
                    applicationStatus = status,
                    lastUpdatedAt = java.time.LocalDateTime.now()
                )
                
                if (updatedApplication != null) {
                    // Update in-memory cache
                    _currentApplication.value = updatedApplication
                    
                    // Update cached application data
                    preferencesDataSource.cacheApplicationData(updatedApplication)
                    
                    emit(Resource.Success(updatedApplication))
                    
                    // Log the error
                    val errorMessage = "Failed to update application status on server: ${response.message()}"
                    AppLogger.w(errorMessage)
                } else {
                    emit(Resource.Error("Failed to update application status: No application loaded"))
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Error updating application status: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // Try to update just the local model
            val currentApplication = _currentApplication.value
            if (currentApplication != null) {
                val updatedApplication = currentApplication.copy(
                    applicationStatus = status,
                    lastUpdatedAt = java.time.LocalDateTime.now()
                )
                
                // Update in-memory cache
                _currentApplication.value = updatedApplication
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(updatedApplication)
                
                emit(Resource.Success(updatedApplication))
            } else {
                emit(Resource.Error(errorMessage))
            }
        }
    }

    /**
     * Update application step progress in Firestore
     */
    override suspend fun updateApplicationStepProgress(
        applicationId: String,
        completedSteps: List<ApplicationStep>,
        currentStep: ApplicationStep
    ): Boolean {
        try {
            // Convert steps to strings for Firestore
            val completedStepsStr = completedSteps.map { it.name }
            
            // Update in Firestore
            firestore.collection("applications")
                .document(applicationId)
                .update(
                    "completedSteps", completedStepsStr,
                    "currentStep", currentStep.name,
                    "lastUpdatedAt", DateConverter.toTimestamp(LocalDateTime.now())
                )
                .await()
            
            // Update in-memory cache if applicable
            val cachedApp = _currentApplication.value
            if (cachedApp?.id == applicationId) {
                _currentApplication.value = cachedApp.copy(
                    completedSteps = completedSteps,
                    currentStep = currentStep,
                    lastUpdatedAt = LocalDateTime.now()
                )
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(_currentApplication.value!!)
            }
            
            return true
        } catch (e: Exception) {
            AppLogger.e("Error updating application progress: ${e.message}", e)
            return false
        }
    }

    /**
     * Save application progress with Flow response
     */
    override suspend fun saveApplicationProgressWithFlow(
        applicationId: String,
        completedSteps: List<ApplicationStep>,
        currentStep: ApplicationStep
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Convert steps to strings for Firestore
            val completedStepsStr = completedSteps.map { it.name }
            
            // Update in Firestore
            firestore.collection("applications")
                .document(applicationId)
                .update(
                    "completedSteps", completedStepsStr,
                    "currentStep", currentStep.name,
                    "lastUpdatedAt", DateConverter.toTimestamp(LocalDateTime.now())  // Timestamp format
                )
                .await()
            
            // Update in-memory cache if applicable
            val cachedApp = _currentApplication.value
            if (cachedApp?.id == applicationId) {
                _currentApplication.value = cachedApp.copy(
                    completedSteps = completedSteps,
                    currentStep = currentStep,
                    lastUpdatedAt = LocalDateTime.now()
                )
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(_currentApplication.value!!)
            }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error saving application progress: ${e.message}", e)
            emit(Resource.Error("Failed to save application progress: ${e.message}"))
        }
    }

    /**
     * Submit the application for review (API version)
     */
    override suspend fun submitApplicationFlow(applicationId: String): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())
        
        try {
            val response = loanApi.submitApplication(applicationId)
            
            if (response.isSuccessful && response.body() != null) {
                val submittedApplication = response.body()!!.toLoanApplication()
                
                // Update in-memory cache
                _currentApplication.value = submittedApplication
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(submittedApplication)
                
                emit(Resource.Success(submittedApplication))
            } else {
                val errorMessage = "Failed to submit application: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error submitting application: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Accept a loan offer
     */
    override suspend fun acceptOffer(
        applicationId: String,
        offerId: String,
        loanAmount: Double,
        tenure: Int
    ): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())
        
        try {
            val request = OfferAcceptanceRequest(
                applicationId = applicationId,
                offerId = offerId,
                selectedLoanAmount = loanAmount,
                selectedTenure = tenure
            )
            
            val response = loanApi.acceptOffer(request)
            
            if (response.isSuccessful && response.body() != null) {
                val updatedApplication = response.body()!!.toLoanApplication()
                
                // Update in-memory cache
                _currentApplication.value = updatedApplication
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(updatedApplication)
                
                emit(Resource.Success(updatedApplication))
            } else {
                val errorMessage = "Failed to accept offer: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error accepting offer: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Get an application by ID
     */
    override suspend fun getApplication(applicationId: String): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try to get from Firestore first
            try {
                val document = firestore.collection("applications")
                    .document(applicationId)
                    .get()
                    .await()
                
                if (document.exists()) {
                    val application = parseFirestoreDocumentToApplication(document)
                    if (application != null) {
                        // Update in-memory cache
                        _currentApplication.value = application
                        
                        // Update cached application data
                        preferencesDataSource.cacheApplicationData(application)
                        
                        emit(Resource.Success(application))
                        return@flow
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error getting application from Firestore: ${e.message}", e)
                // Continue to API approach
            }
            
            // Fall back to API if Firestore fails
            val response = loanApi.getApplication(applicationId)
            
            if (response.isSuccessful && response.body() != null) {
                val application = response.body()!!.toLoanApplication()
                
                // Update in-memory cache
                _currentApplication.value = application
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(application)
                
                emit(Resource.Success(application))
            } else {
                val errorMessage = "Failed to get application: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting application: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Get the application decision
     */
    override suspend fun getApplicationDecision(applicationId: String): Flow<Resource<DecisionStatus>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try to get from Firestore first
            try {
                val document = firestore.collection("applications")
                    .document(applicationId)
                    .get()
                    .await()
                
                if (document.exists()) {
                    val decisionStatusStr = document.getString("decisionStatus")
                    if (decisionStatusStr != null) {
                        try {
                            val decisionStatus = DecisionStatus.valueOf(decisionStatusStr)
                            emit(Resource.Success(decisionStatus))
                            return@flow
                        } catch (e: Exception) {
                            AppLogger.e("Invalid decision status in Firestore: $decisionStatusStr", e)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Error getting decision from Firestore: ${e.message}", e)
                // Continue to API approach
            }
            
            // Fall back to API if Firestore fails
            val response = loanApi.getApplicationDecision(applicationId)
            
            if (response.isSuccessful && response.body() != null) {
                val decisionMap = response.body()!!
                val decisionStatus = try {
                    DecisionStatus.valueOf(decisionMap["status"] ?: "PENDING")
                } catch (e: Exception) {
                    DecisionStatus.PENDING
                }
                
                // If we have the current application, update it with the decision
                val currentApplication = _currentApplication.value
                if (currentApplication != null && currentApplication.id == applicationId) {
                    val updatedApplication = currentApplication.copy(
                        decisionStatus = decisionStatus,
                        decisionReason = decisionMap["reason"],
                        underwriterComments = decisionMap["comments"]
                    )
                    
                    // Update in-memory cache
                    _currentApplication.value = updatedApplication
                    
                    // Update cached application data
                    preferencesDataSource.cacheApplicationData(updatedApplication)
                }
                
                emit(Resource.Success(decisionStatus))
            } else {
                val errorMessage = "Failed to get application decision: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting application decision: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Get EMI details for a loan configuration
     */
    override suspend fun calculateEMI(
        amount: Double,
        tenure: Int,
        interestRate: Double
    ): Flow<Resource<LoanOffer>> = flow {
        emit(Resource.Loading())
        
        try {
            val currentApplication = _currentApplication.value
            if (currentApplication == null) {
                emit(Resource.Error("No current application loaded"))
                return@flow
            }
            
            val request = LoanCalculationRequest(
                applicationId = currentApplication.id,
                employmentType = currentApplication.employmentDetails?.employmentType?.name ?: "OTHER",
                monthlySalary = currentApplication.employmentDetails?.monthlySalary ?: 0.0,
                existingEmi = null,
                creditScore = null
            )
            
            val response = loanApi.calculateLoanOffer(request)
            
            if (response.isSuccessful && response.body() != null) {
                val loanOffer = response.body()!!.toLoanOffer()
                emit(Resource.Success(loanOffer))
            } else {
                val errorMessage = "Failed to calculate EMI: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error calculating EMI: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Get current application as StateFlow
     */
    override fun observeCurrentApplication(): StateFlow<LoanApplication?> {
        return _currentApplication.asStateFlow()
    }
    
    /**
     * Get the current application
     */
    override suspend fun getCurrentApplication(): LoanApplication? {
        AppLogger.d("Getting current application")
        
        // First check in-memory cache
        val inMemoryApp = _currentApplication.value
        if (inMemoryApp != null) {
            AppLogger.d("Using in-memory application: ${inMemoryApp.id}")
            return inMemoryApp
        }
        
        // Next try loading from local storage
        val cachedApp = preferencesDataSource.getCachedApplicationData()
        if (cachedApp != null) {
            AppLogger.d("Using cached application: ${cachedApp.id}")
            // Update in-memory cache
            _currentApplication.value = cachedApp
            return cachedApp
        }
        
        // Try to load from Firestore directly
        val auth = FirebaseAuth.getInstance().currentUser
        val userId = auth?.uid
        val phoneNumber = auth?.phoneNumber
        
        AppLogger.d("Looking for applications with userId: $userId or phoneNumber: $phoneNumber")
        
        if (userId != null) {
            try {
                // Try multiple queries to handle both formats
                var applications = emptyList<LoanApplication>()
                
                // First try query by uid
                applications = queryFirestoreApplications("userId", userId)
                
                // If no results and we have phone number, try with phone
                if (applications.isEmpty() && phoneNumber != null) {
                    applications = queryFirestoreApplications("userId", phoneNumber)
                }
                
                // Additionally check if the app is stored with id matching userId
                if (applications.isEmpty()) {
                    val singleApp = queryFirestoreApplicationById(userId)
                    if (singleApp != null) {
                        applications = listOf(singleApp)
                    }
                }
                
                // Use the most recent application if found
                val mostRecentApp = applications.maxByOrNull { it.lastUpdatedAt }
                if (mostRecentApp != null) {
                    AppLogger.d("Found application in Firestore: ${mostRecentApp.id}")
                    _currentApplication.value = mostRecentApp
                    preferencesDataSource.cacheApplicationData(mostRecentApp)
                    return mostRecentApp
                }
            } catch (e: Exception) {
                AppLogger.e("Error loading from Firestore: ${e.message}", e)
            }
        }
        
        // If all else fails, create a fallback application
        val fallbackApp = createFallbackApplication(userId ?: phoneNumber ?: "unknown-user")
        _currentApplication.value = fallbackApp
        preferencesDataSource.cacheApplicationData(fallbackApp)
        AppLogger.d("Created fallback application: ${fallbackApp.id}")
        
        return fallbackApp
    }

    /**
     * Query Firestore applications by field
     */
    private suspend fun queryFirestoreApplications(fieldName: String, fieldValue: String): List<LoanApplication> {
        return suspendCoroutine { continuation ->
            AppLogger.d("Querying Firestore with $fieldName = $fieldValue")
            firestore.collection("applications")
                .whereEqualTo(fieldName, fieldValue)
                .get()
                .addOnSuccessListener { documents ->
                    AppLogger.d("Query returned ${documents.size()} documents")
                    val apps = documents.mapNotNull { doc ->
                        try {
                            parseFirestoreDocumentToApplication(doc)
                        } catch (e: Exception) {
                            AppLogger.e("Error parsing application document: ${e.message}", e)
                            null
                        }
                    }
                    continuation.resume(apps)
                }
                .addOnFailureListener { e ->
                    AppLogger.e("Error querying Firestore: ${e.message}", e)
                    continuation.resume(emptyList())
                }
        }
    }

    /**
     * Query Firestore application by ID
     */
    private suspend fun queryFirestoreApplicationById(documentId: String): LoanApplication? {
        return suspendCoroutine { continuation ->
            AppLogger.d("Querying Firestore for document with id = $documentId")
            firestore.collection("applications")
                .document(documentId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        AppLogger.d("Found document with id $documentId")
                        try {
                            val app = parseFirestoreDocumentToApplication(doc)
                            continuation.resume(app)
                        } catch (e: Exception) {
                            AppLogger.e("Error parsing application document: ${e.message}", e)
                            continuation.resume(null)
                        }
                    } else {
                        AppLogger.d("No document found with id $documentId")
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    AppLogger.e("Error querying Firestore: ${e.message}", e)
                    continuation.resume(null)
                }
        }
    }



    /**
     * Parse Firestore document to LoanApplication model
     */
    private fun parseFirestoreDocumentToApplication(doc: DocumentSnapshot): LoanApplication? {
        try {
            AppLogger.d("Parsing Firestore document: ${doc.id}")
            // Basic fields
            val id = doc.getString("id") ?: doc.id
            val userId = doc.getString("userId") ?: ""
            // val mobileNumber = doc.getString("mobileNumber") ?: userId // Mobile number is now read from root

            // *** Read the top-level panNumber field ***
            val panNumberFromRoot = doc.getString("panNumber")

            // Date fields using the converter
            val createdAt = DateConverter.parseFirestoreValue(doc.get("createdAt")) ?: LocalDateTime.now()
            val lastUpdatedAt = DateConverter.parseFirestoreValue(doc.get("lastUpdatedAt")) ?: LocalDateTime.now()
            val submittedAt = DateConverter.parseFirestoreValue(doc.get("submittedAt"))

            // Enum fields with error handling
            val applicationStatus = try {
                ApplicationStatus.valueOf(doc.getString("applicationStatus") ?: "CREATED")
            } catch (e: Exception) { ApplicationStatus.CREATED }

            val currentStep = try {
                ApplicationStep.valueOf(doc.getString("currentStep") ?: "PAN_VERIFICATION")
            } catch (e: Exception) { ApplicationStep.PAN_VERIFICATION }

            val decisionStatus = try {
                doc.getString("decisionStatus")?.let { DecisionStatus.valueOf(it) }
            } catch (e: Exception) { null }

            // List fields
            val completedSteps = (doc.get("completedSteps") as? List<*>)
                ?.mapNotNull { step -> (step as? String)?.let { try { ApplicationStep.valueOf(it) } catch (e: Exception) { null } } }
                ?: emptyList()

            val documentIds = (doc.get("documentIds") as? List<*>)
                ?.mapNotNull { it as? String } ?: emptyList()

            // Optional string fields
            val applicationNumber = doc.getString("applicationNumber")
            val decisionReason = doc.getString("decisionReason")
            val underwriterComments = doc.getString("underwriterComments")

            // --- Parse Nested Objects ---

            // Parse PersonalInfo
            val personalInfoMap = doc.get("personalInfo") as? Map<String, Any?>
            val personalInfo = if (personalInfoMap != null) {
                try {
                    val addressMap = personalInfoMap["address"] as? Map<String, Any?>
                    val address = if (addressMap != null) {
                        Address(
                            addressLine1 = addressMap["addressLine1"] as? String ?: "", // Provide default empty string
                            addressLine2 = addressMap["addressLine2"] as? String, // Assuming addressLine2 IS nullable
                            city = addressMap["city"] as? String ?: "", // Provide default empty string
                            state = addressMap["state"] as? String ?: "", // Provide default empty string
                            postalCode = addressMap["postalCode"] as? String ?: "", // Provide default empty string
                            country = addressMap["country"] as? String ?: "India",
                            addressType = try { (addressMap["addressType"] as? String)?.let { AddressType.valueOf(it) } ?: AddressType.CURRENT } catch(e: Exception) { AddressType.CURRENT }
                        )
                    } else Address("", null, "", "", "", "India", AddressType.CURRENT) // Use appropriate defaults


                    PersonalInfo(
                        name = personalInfoMap["name"] as? String ?: "",
                        dateOfBirth = (personalInfoMap["dateOfBirth"] as? String)?.let { dobString ->
                            try { java.time.LocalDate.parse(dobString) } catch (e: Exception) { null }
                        },
                        email = personalInfoMap["email"] as? String ?: "",
                        gender = try { (personalInfoMap["gender"] as? String)?.let { Gender.valueOf(it) } } catch(e: Exception) { null },
                        maritalStatus = try { (personalInfoMap["maritalStatus"] as? String)?.let { MaritalStatus.valueOf(it) } } catch(e: Exception) { null },
                        address = address,
                        alternatePhoneNumber = personalInfoMap["alternatePhoneNumber"] as? String
                    )
                } catch (e: Exception) {
                    AppLogger.e("Error parsing personalInfo from Firestore: ${e.message}", e)
                    null
                }
            } else null

            // Parse EmploymentDetails
            val employmentDetailsMap = doc.get("employmentDetails") as? Map<String, Any?>
            val employmentDetails = if (employmentDetailsMap != null) {
                try {
                    val officeAddressMap = employmentDetailsMap["officeAddress"] as? Map<String, Any?>
                    val officeAddress = if (officeAddressMap != null) {
                         Address(
                            addressLine1 = officeAddressMap["addressLine1"] as? String ?: "",
                            addressLine2 = officeAddressMap["addressLine2"] as? String,
                            city = officeAddressMap["city"] as? String ?: "",
                            state = officeAddressMap["state"] as? String ?: "",
                            postalCode = officeAddressMap["postalCode"] as? String ?: "",
                            country = officeAddressMap["country"] as? String ?: "India",
                            addressType = AddressType.OFFICE // Assume office type
                        )
                    } else null

                    EmploymentDetails(
                        employmentType = try { (employmentDetailsMap["employmentType"] as? String)?.let { EmploymentType.valueOf(it) } ?: EmploymentType.OTHER } catch(e: Exception) { EmploymentType.OTHER },
                        employerName = (employmentDetailsMap["employerName"] as? String).toString(),
                        employerId = employmentDetailsMap["employerId"] as? String,
                        designation = employmentDetailsMap["designation"] as? String,
                        department = employmentDetailsMap["department"] as? String,
                        employeeId = employmentDetailsMap["employeeId"] as? String,
                        workEmail = employmentDetailsMap["workEmail"] as? String,
                        monthlySalary = (employmentDetailsMap["monthlySalary"] as? Number)?.toDouble() ?: 0.0,
                        // Convert to Double? if the data model expects Double?, handle Number from Firestore
                        monthlyEmi = (employmentDetailsMap["monthlyEmi"] as? Number)?.toDouble(), // Keep as Double?
                        officeAddress = officeAddress,
                        // Parse verification status and method if they exist
                        isVerified = employmentDetailsMap["isVerified"] as? Boolean ?: false,
                        verificationMethod = try { (employmentDetailsMap["verificationMethod"] as? String)?.let { VerificationMethod.valueOf(it) } } catch (e: Exception) { null }
                    )
                } catch (e: Exception) {
                    AppLogger.e("Error parsing employmentDetails from Firestore: ${e.message}", e)
                    null
                }
            } else null

            // Parse PANDetails (nested map)
             val panDetailsMap = doc.get("panDetails") as? Map<String, Any?>
             var panDetails = if (panDetailsMap != null) { // Make panDetails mutable temporarily
                 try {
                    PANDetails(
                        panNumber = panDetailsMap["panNumber"] as? String ?: "",
                        name = panDetailsMap["name"] as? String ?: "",
                        fatherName = panDetailsMap["fatherName"] as? String,
                        dateOfBirth = (panDetailsMap["dateOfBirth"] as? String)?.let {
                            try { java.time.LocalDate.parse(it) } catch(e: Exception) { null }
                        },
                        isVerified = (panDetailsMap["isVerified"] as? Boolean) ?: false,
                        verificationDate = (panDetailsMap["verificationDate"] as? String)?.let {
                            try { java.time.LocalDate.parse(it) } catch(e: Exception) { null }
                        }
                    )
                 } catch (e: Exception) {
                     AppLogger.e("Error parsing panDetails from Firestore: ${e.message}", e)
                     null
                 }
            } else null

            // *** ADDED: Populate panDetails.panNumber from root if nested one is missing ***
            if (panDetails != null && panDetails.panNumber.isBlank() && !panNumberFromRoot.isNullOrBlank()) {
                panDetails = panDetails.copy(panNumber = panNumberFromRoot)
                AppLogger.d("Populated panDetails.panNumber from root panNumber field.")
            } else if (panDetails == null && !panNumberFromRoot.isNullOrBlank()) {
                // If panDetails map didn't exist, create a minimal one with just the PAN
                panDetails = PANDetails(panNumber = panNumberFromRoot, name = personalInfo?.name ?: "", isVerified = false) // Add defaults
                 AppLogger.d("Created minimal panDetails from root panNumber field.")
            }


            // Parse BureauReport (Simplified example, adjust based on your actual BureauReport model)
            val bureauReportMap = doc.get("bureauReport") as? Map<String, Any?>
             val bureauReport = if (bureauReportMap != null) {
                 try {
                    BureauReport(
                        id = bureauReportMap["id"] as? String ?: "",
                        panNumber = bureauReportMap["panNumber"] as? String ?: panNumberFromRoot ?: "", // Use root PAN as fallback
                        creditScore = (bureauReportMap["creditScore"] as? Number)?.toInt(),
                        reportDate = bureauReportMap["reportDate"] as? String,
                        // Fill other BureauReport fields as needed, providing defaults or nulls
                        scoreDate = null,
                        bureauType = null,
                        controlNumber = bureauReportMap["controlNumber"] as? String,
                        customerName = bureauReportMap["customerName"] as? String,
                        dateOfBirth = (bureauReportMap["dateOfBirth"] as? String)?.let { try {LocalDate.parse(it)} catch(e: Exception){null} },
                        gender = bureauReportMap["gender"] as? String,
                        addresses = emptyList(), // Add logic to parse addresses if stored here
                        mobilePhones = emptyList(), // Add logic to parse phones if stored here
                        email = bureauReportMap["email"] as? String,
                        accountSummary = null, // Add logic to parse accountSummary if stored here
                        inquiryCountLast30Days = 0,
                        hasExistingDefaultOrWriteOff = false,
                        totalWrittenOffAmount = 0L,
                        delinquentStatus = "Standard",
                        createdAt = DateConverter.parseFirestoreValue(bureauReportMap["createdAt"]) ?: LocalDateTime.now()
                    )
                 } catch (e: Exception) {
                     AppLogger.e("Error parsing bureauReport from Firestore: ${e.message}", e)
                     null
                 }
             } else null

             // Parse LoanOffer (Simplified example, adjust based on your actual LoanOffer model)
             val loanOfferMap = doc.get("loanOffer") as? Map<String, Any?>
             val loanOffer = if (loanOfferMap != null) {
                 try {
                     LoanOffer(
                         offerId = loanOfferMap["offerId"] as? String ?: "",
                         applicationId = loanOfferMap["applicationId"] as? String ?: id,
                         generatedAt = DateConverter.parseFirestoreValue(loanOfferMap["generatedAt"]) ?: LocalDateTime.now(),
                         expiresAt = DateConverter.parseFirestoreValue(loanOfferMap["expiresAt"]),
                         approvedLoanAmount = (loanOfferMap["approvedLoanAmount"] as? Number)?.toDouble() ?: 0.0,
                         minLoanAmount = (loanOfferMap["minLoanAmount"] as? Number)?.toDouble() ?: 0.0,
                         maxLoanAmount = (loanOfferMap["maxLoanAmount"] as? Number)?.toDouble() ?: 0.0,
                         selectedLoanAmount = (loanOfferMap["selectedLoanAmount"] as? Number)?.toDouble(),
                         minTenure = (loanOfferMap["minTenure"] as? Number)?.toInt() ?: 0,
                         maxTenure = (loanOfferMap["maxTenure"] as? Number)?.toInt() ?: 0,
                         selectedTenure = (loanOfferMap["selectedTenure"] as? Number)?.toInt(),
                         interestRate = (loanOfferMap["interestRate"] as? Number)?.toDouble() ?: 0.0,
                         processingFeePercentage = (loanOfferMap["processingFeePercentage"] as? Number)?.toDouble() ?: 0.0,
                         processingFeeAmount = (loanOfferMap["processingFeeAmount"] as? Number)?.toDouble() ?: 0.0,
                         emiAmount = (loanOfferMap["emiAmount"] as? Number)?.toDouble(),
                         totalInterestAmount = (loanOfferMap["totalInterestAmount"] as? Number)?.toDouble(),
                         totalRepaymentAmount = (loanOfferMap["totalRepaymentAmount"] as? Number)?.toDouble(),
                         loanStartDate = DateConverter.parseFirestoreValue(loanOfferMap["loanStartDate"]),
                         loanEndDate = DateConverter.parseFirestoreValue(loanOfferMap["loanEndDate"]),
                         offerStatus = try { (loanOfferMap["offerStatus"] as? String)?.let { OfferStatus.valueOf(it) } ?: OfferStatus.GENERATED } catch (e: Exception) { OfferStatus.GENERATED },
                         offerAcceptedAt = DateConverter.parseFirestoreValue(loanOfferMap["offerAcceptedAt"])
                     )
                 } catch (e: Exception) {
                     AppLogger.e("Error parsing loanOffer from Firestore: ${e.message}", e)
                     null
                 }
             } else null


            // Construct the final LoanApplication object
            return LoanApplication(
                id = id,
                userId = userId,
                createdAt = createdAt,
                lastUpdatedAt = lastUpdatedAt,
                applicationStatus = applicationStatus,
                panNumber = panNumberFromRoot, // <-- Assign parsed top-level PAN
                panDetails = panDetails,       // Assign potentially updated panDetails
                bureauReport = bureauReport,   // Assign parsed object
                personalInfo = personalInfo, // Assign parsed object
                employmentDetails = employmentDetails, // Assign parsed object
                documents = emptyList(), // Documents are loaded separately
                documentIds = documentIds,
                loanOffer = loanOffer, // Assign parsed object
                currentStep = currentStep,
                completedSteps = completedSteps,
                submittedAt = submittedAt,
                applicationNumber = applicationNumber,
                decisionStatus = decisionStatus,
                decisionReason = decisionReason,
                underwriterComments = underwriterComments
                // Add additionalData parsing if needed
            )
        } catch (e: Exception) {
            AppLogger.e("Error parsing Firestore document ${doc.id} to LoanApplication: ${e.message}", e)
            return null // Return null on any failure
        }
    }

    /**
     * Ensure that application is loaded
     */
    override suspend fun ensureApplicationLoaded(applicationId: String): Boolean {
        // Check if already loaded in memory
        if (_currentApplication.value?.id == applicationId) {
            return true
        }
        
        try {
            // Try to load from Firestore first
            val document = firestore.collection("applications")
                .document(applicationId)
                .get()
                .await()
            
            if (document.exists()) {
                val application = parseFirestoreDocumentToApplication(document)
                if (application != null) {
                    _currentApplication.value = application
                    preferencesDataSource.cacheApplicationData(application)
                    return true
                }
            }
            
            // Try API as fallback
            val response = loanApi.getApplication(applicationId)
            
            if (response.isSuccessful && response.body() != null) {
                val application = response.body()!!.toLoanApplication()
                
                // Update in-memory cache
                _currentApplication.value = application
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(application)
                
                return true
            } else {
                // Try to load from local storage as fallback
                val cachedApp = preferencesDataSource.getCachedApplicationData()
                if (cachedApp?.id == applicationId) {
                    _currentApplication.value = cachedApp
                    return true
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Error ensuring application is loaded", e)
            
            // Try local storage as last resort
            val cachedApp = preferencesDataSource.getCachedApplicationData()
            if (cachedApp?.id == applicationId) {
                _currentApplication.value = cachedApp
                return true
            }
        }
        
        return false
    }
    
    /**
     * Get the loan offer for the current application
     */
    override suspend fun getLoanOffer(): LoanOfferUI? {
        val currentApp = _currentApplication.value ?: return null
        val loanOffer = currentApp.loanOffer ?: return null
        
        return LoanOfferUI(
            id = loanOffer.offerId,
            status = LoanOfferUI.Status.APPROVED, // This should be determined based on application state
            minAmount = loanOffer.minLoanAmount.toFloat(),
            maxAmount = loanOffer.maxLoanAmount.toFloat(),
            defaultAmount = loanOffer.approvedLoanAmount.toFloat(),
            minTenure = loanOffer.minTenure,
            maxTenure = loanOffer.maxTenure,
            defaultTenure = loanOffer.minTenure + ((loanOffer.maxTenure - loanOffer.minTenure) / 2),
            interestRate = loanOffer.interestRate.toFloat(),
            processingFeePercentage = loanOffer.processingFeePercentage.toFloat()
        )
    }
    
    /**
     * Save the user's loan offer selection
     */
    override suspend fun saveLoanOfferSelection(
        amount: Long,
        tenure: Int,
        interestRate: Float,
        emi: Long,
        processingFee: Long
    ): Boolean {
        try {
            val currentApp = _currentApplication.value ?: return false
            val loanOffer = currentApp.loanOffer ?: return false
            
            // Record in metadata first - for analytics
            try {
                val applicationId = currentApp.id
                
                // Create a metadata event for loan selection
                val eventData = mapOf(
                    "eventType" to "LOAN_OFFER_SELECTED",
                    "applicationId" to applicationId,
                    "timestamp" to LocalDateTime.now().toString(),
                    "selectedAmount" to amount,
                    "selectedTenure" to tenure,
                    "interestRate" to interestRate,
                    "calculatedEmi" to emi,
                    "processingFee" to processingFee
                )
                
                // Log metadata event
                // Log metadata event to application subcollection
                metadataRepository.sendEventToOrchestrator(
                    applicationId = applicationId,
                    eventType = MetadataEventType.SECTION_COMPLETED, // Use an existing event type
                    metadata = eventData
                ).collect { /* ignore result */ }
            } catch (e: Exception) {
                AppLogger.e("Error recording loan offer selection in metadata: ${e.message}", e)
                // Continue with saving regardless
            }
            
            // Update the loan offer with the selected values
            val updatedLoanOffer = loanOffer.copy(
                selectedLoanAmount = amount.toDouble(),
                selectedTenure = tenure,
                emiAmount = emi.toDouble(),
                processingFeeAmount = processingFee.toDouble()
            )
            
            // Update the application with the new loan offer
            val updatedApplication = currentApp.copy(
                loanOffer = updatedLoanOffer,
                lastUpdatedAt = LocalDateTime.now()
            )
            
            // Update in-memory cache
            _currentApplication.value = updatedApplication
            
            // Update cached application data
            preferencesDataSource.cacheApplicationData(updatedApplication)
            
            // Update directly in Firestore
            try {
                AppLogger.d("Saving loan offer to Firestore for app: ${currentApp.id}")
                
                // Create the loan offer data for Firestore
                val offerData = mapOf(
                    "loanOffer" to mapOf(
                        "applicationId" to currentApp.id,
                        "offerId" to loanOffer.offerId,
                        "selectedLoanAmount" to amount.toDouble(),
                        "selectedTenure" to tenure,
                        "emiAmount" to emi.toDouble(),
                        "processingFeeAmount" to processingFee.toDouble(),
                        "interestRate" to interestRate.toDouble(),
                        "approvedLoanAmount" to loanOffer.approvedLoanAmount,
                        "minLoanAmount" to loanOffer.minLoanAmount,
                        "maxLoanAmount" to loanOffer.maxLoanAmount
                    ),
                    "lastUpdatedAt" to DateConverter.toTimestamp(LocalDateTime.now())
                )
                
                // Update in Firestore
                firestore.collection("applications")
                    .document(currentApp.id)
                    .update(offerData)
                    .await()
                    
                AppLogger.d("Successfully saved loan offer data to Firestore")
            } catch (e: Exception) {
                AppLogger.e("Error saving loan offer to Firestore: ${e.message}", e)
                // Continue with API approach even if Firestore update fails
            }
            
            // Try to update via API if possible
            try {
                val request = OfferAcceptanceRequest(
                    applicationId = currentApp.id,
                    offerId = loanOffer.offerId,
                    selectedLoanAmount = amount.toDouble(),
                    selectedTenure = tenure
                )
                
                val response = loanApi.acceptOffer(request)
                
                if (response.isSuccessful && response.body() != null) {
                    val serverUpdatedApplication = response.body()!!.toLoanApplication()
                    
                    // Update in-memory cache with server response
                    _currentApplication.value = serverUpdatedApplication
                    
                    // Update cached application data
                    preferencesDataSource.cacheApplicationData(serverUpdatedApplication)
                }
            } catch (e: Exception) {
                // Log error but don't fail - we've already updated locally
                AppLogger.e("Error updating loan offer on server: ${e.message}", e)
            }
            
            return true
        } catch (e: Exception) {
            AppLogger.e("Error saving loan offer selection: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Submit the final application
     */
    override suspend fun submitApplication(applicationId: String): Boolean {
        try {
            // Update local application status
            val currentApp = _currentApplication.value
            if (currentApp != null && currentApp.id == applicationId) {
                val updatedApplication = currentApp.copy(
                    applicationStatus = ApplicationStatus.SUBMITTED,
                    submittedAt = LocalDateTime.now(),
                    lastUpdatedAt = LocalDateTime.now()
                )
                
                // Update in-memory cache
                _currentApplication.value = updatedApplication
                
                // Update cached application data
                preferencesDataSource.cacheApplicationData(updatedApplication)
                
                // Record in metadata
                try {
                    // Create metadata event for submission
                    val eventData = mapOf(
                        "eventType" to "APPLICATION_SUBMITTED",
                        "applicationId" to applicationId,
                        "timestamp" to LocalDateTime.now().toString()
                    )
                    
                    // Log metadata event
                    metadataRepository.sendEventToOrchestrator(
                        applicationId = applicationId,
                        eventType = MetadataEventType.APPLICATION_SUBMITTED,
                        metadata = eventData
                    ).collect { /* ignore result */ }
                } catch (e: Exception) {
                    AppLogger.e("Error recording application submission in metadata: ${e.message}", e)
                    // Continue with submission regardless
                }
            }
            
            // Update in Firestore
            try {
                val updateData = mapOf(
                    "applicationStatus" to ApplicationStatus.SUBMITTED.name,
                    "submittedAt" to DateConverter.toTimestamp(LocalDateTime.now()),
                    "lastUpdatedAt" to DateConverter.toTimestamp(LocalDateTime.now())
                )
                
                firestore.collection("applications")
                    .document(applicationId)
                    .update(updateData)
                    .await()
                
                AppLogger.d("Successfully updated application status to SUBMITTED in Firestore")
            } catch (e: Exception) {
                AppLogger.e("Error updating application status in Firestore: ${e.message}", e)
                // Continue with API approach
            }
            
            // Submit via API
            try {
                val response = loanApi.submitApplication(applicationId)
                
                if (response.isSuccessful && response.body() != null) {
                    val submittedApplication = response.body()!!.toLoanApplication()
                    
                    // Update in-memory cache with server response
                    _currentApplication.value = submittedApplication
                    
                    // Update cached application data
                    preferencesDataSource.cacheApplicationData(submittedApplication)
                    
                    return true
                } else {
                    // We've already updated locally, so consider it a success
                    // but log the error
                    val errorMessage = "Failed to submit application on server: ${response.message()}"
                    AppLogger.w(errorMessage)
                    return true
                }
            } catch (e: Exception) {
                // We've already updated locally, so consider it a success
                // but log the error
                val errorMessage = "Error submitting application on server: ${e.message}"
                AppLogger.e(errorMessage, e)
                return true
            }
        } catch (e: Exception) {
            val errorMessage = "Error submitting application: ${e.message}"
            AppLogger.e(errorMessage, e)
            return false
        }
    }

    /**
     * Add a document to an application
     */
    override suspend fun addDocumentToApplication(
        applicationId: String,
        documentId: String
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Update in Firestore
            firestore.collection("applications")
                .document(applicationId)
                .update("documentIds", FieldValue.arrayUnion(documentId))
                .await()
                
            // Update in local cache if needed
            val cachedApp = observeCurrentApplication().value
            if (cachedApp?.id == applicationId) {
                val updatedDocIds = cachedApp.documentIds.toMutableList()
                if (!updatedDocIds.contains(documentId)) {
                    updatedDocIds.add(documentId)
                    
                    val updatedApp = cachedApp.copy(
                        documentIds = updatedDocIds,
                        lastUpdatedAt = LocalDateTime.now()
                    )
                    
                    // Update the cached application
                    _currentApplication.value = updatedApp
                    preferencesDataSource.cacheApplicationData(updatedApp)
                }
            }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error adding document to application: ${e.message}", e)
            emit(Resource.Error("Failed to update application: ${e.message}"))
        }
    }

    /**
     * Update application metadata field
     */
    override suspend fun updateApplicationMetadata(
        applicationId: String,
        metadataField: String,
        data: Any
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Create a Firestore field path based on the metadata field
            val fieldPath = "metadata.$metadataField"
            
            // Use arrayUnion to add to the array field
            firestore.collection("applications")
                .document(applicationId)
                .update(fieldPath, FieldValue.arrayUnion(data))
                .await()
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error updating application metadata: ${e.message}", e)
            emit(Resource.Error("Failed to update metadata: ${e.message}"))
        }
    }

    /**
     * Add a screen visit to the metadata
     */
    override suspend fun addScreenVisit(
        applicationId: String,
        screenVisit: ScreenVisit
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Convert screenVisit to a map for Firestore
            val screenVisitMap = mapOf(
                "screenName" to screenVisit.screenName,
                "startTime" to DateConverter.toTimestamp(screenVisit.startTime),
                "endTime" to screenVisit.endTime?.let { DateConverter.toTimestamp(it) },
                "durationSeconds" to screenVisit.durationSeconds
            )
            
            // Add to metadata.screenVisits array in Firestore
            firestore.collection("applications")
                .document(applicationId)
                .update("metadata.screenVisits", FieldValue.arrayUnion(screenVisitMap))
                .await()
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error adding screen visit: ${e.message}", e)
            emit(Resource.Error("Failed to add screen visit: ${e.message}"))
        }
    }

    /**
     * Add a section timing to the metadata
     */
    override suspend fun addSectionTiming(
        applicationId: String,
        sectionTiming: SectionTiming
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Convert sectionTiming to a map for Firestore
            val sectionTimingMap = mapOf(
                "screenName" to sectionTiming.screenName,
                "sectionName" to sectionTiming.sectionName,
                "startTime" to DateConverter.toTimestamp(sectionTiming.startTime),
                "endTime" to sectionTiming.endTime?.let { DateConverter.toTimestamp(it) },
                "durationSeconds" to sectionTiming.durationSeconds
            )
            
            // Add to metadata.sectionTimings array in Firestore
            firestore.collection("applications")
                .document(applicationId)
                .update("metadata.sectionTimings", FieldValue.arrayUnion(sectionTimingMap))
                .await()
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error adding section timing: ${e.message}", e)
            emit(Resource.Error("Failed to add section timing: ${e.message}"))
        }
    }

    /**
     * Start tracking a form section
     */
    override suspend fun startSectionTiming(
        applicationId: String,
        screenName: String,
        sectionName: String
    ): String {
        // Generate a unique ID for this section timing
        val sectionTimingId = "$applicationId-$screenName-$sectionName-${System.currentTimeMillis()}"
        
        // Create a new section timing with start time
        val sectionTiming = SectionTiming(
            screenName = screenName,
            sectionName = sectionName,
            startTime = LocalDateTime.now()
        )
        
        // Store in memory map
        sectionTimingMap[sectionTimingId] = sectionTiming
        
        return sectionTimingId
    }

    /**
     * Complete a form section timing
     */
    override suspend fun completeSectionTiming(
        applicationId: String,
        sectionTimingId: String
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get the section timing from memory
            val sectionTiming = sectionTimingMap[sectionTimingId]
            
            if (sectionTiming != null) {
                // Calculate endTime and duration
                val endTime = LocalDateTime.now()
                val durationSeconds = java.time.Duration.between(sectionTiming.startTime, endTime).seconds
                
                // Create completed section timing
                val completedTiming = sectionTiming.copy(
                    endTime = endTime,
                    durationSeconds = durationSeconds
                )
                
                // Add to Firestore
                addSectionTiming(applicationId, completedTiming).collect { result ->
                    emit(result)
                }
                
                // Remove from memory map
                sectionTimingMap.remove(sectionTimingId)
            } else {
                emit(Resource.Error("Section timing not found: $sectionTimingId"))
            }
        } catch (e: Exception) {
            AppLogger.e("Error completing section timing: ${e.message}", e)
            emit(Resource.Error("Failed to complete section timing: ${e.message}"))
        }
    }

    /**
     * Log a metadata event to the application's metadata subcollection
     */
    override suspend fun logMetadataEvent(
        applicationId: String,
        eventType: String,
        eventData: Map<String, Any>
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Generate a unique event ID
            val eventId = UUID.randomUUID().toString()
            
            // Create the metadata document with required fields
            val metadataDoc = hashMapOf<String, Any>(
                "eventId" to eventId,
                "applicationId" to applicationId,
                "eventTimestamp" to DateConverter.toTimestamp(LocalDateTime.now()),
                "eventType" to eventType
            )
            
            // Add the event-specific data by merging maps
            metadataDoc.putAll(eventData)
            
            // Save to Firestore metadata subcollection
            firestore.collection("applications")
                .document(applicationId)
                .collection("metadata")
                .document(eventId)
                .set(metadataDoc)
                .await()
                
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error logging metadata event: ${e.message}", e)
            emit(Resource.Error("Failed to log metadata event: ${e.message}"))
        }
    }

    /**
     * Add device info to metadata
     */
    override suspend fun addDeviceInfo(applicationId: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get device info
            val deviceInfo = mapOf(
                "make" to android.os.Build.MANUFACTURER,
                "model" to android.os.Build.MODEL,
                "osVersion" to android.os.Build.VERSION.RELEASE,
                "appVersion" to BuildConfig.VERSION_NAME
            )
            
            // Log as metadata event
            logMetadataEvent(
                applicationId,
                "APP_SESSION_START",
                mapOf("deviceInfo" to deviceInfo)
            ).collect { result ->
                emit(result)
            }
        } catch (e: Exception) {
            AppLogger.e("Error adding device info: ${e.message}", e)
            emit(Resource.Error("Failed to add device info: ${e.message}"))
        }
    }
    
    /**
     * Get bureau score from application or bureau report
     * Added in v1.5.0 to support conditional Bureau Confirmation screen
     */
    override suspend fun getBureauScore(applicationId: String): Flow<Resource<Int?>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try to get application first
            val appResource = getApplication(applicationId).first()
            if (appResource is Resource.Success) {
                val application = appResource.data
                
                // Get bureau score from bureau report in application if available
                val bureauScore = application.bureauReport?.creditScore
                
                if (bureauScore != null) {
                    AppLogger.d("Found bureau score in application: $bureauScore")
                    emit(Resource.Success(bureauScore))
                    return@flow
                }
            }
            
            // If not found in application, try Firestore directly
            val document = firestore.collection("applications")
                .document(applicationId)
                .get()
                .await()
            
            if (document.exists()) {
                // Try to get bureau score from root level first (may have been cached there)
                val bureauScore = document.getLong("bureauScore")?.toInt()
                
                if (bureauScore != null) {
                    AppLogger.d("Found bureau score in Firestore root: $bureauScore")
                    emit(Resource.Success(bureauScore))
                    return@flow
                }
                
                // Try to get from bureauReport if present
                val bureauReport = document.get("bureauReport") as? Map<*, *>
                if (bureauReport != null) {
                    val score = (bureauReport["creditScore"] as? Number)?.toInt()
                    
                    if (score != null) {
                        AppLogger.d("Found bureau score in Firestore bureauReport: $score")
                        emit(Resource.Success(score))
                        return@flow
                    }
                }
            }
            
            // Try Appwrite as a last resort (this would be implemented if needed)
            // For now, we'll just emit null as this would need coordination with the Appwrite service
            AppLogger.d("No bureau score found for application: $applicationId")
            emit(Resource.Success(null))
            
        } catch (e: Exception) {
            val errorMessage = "Error getting bureau score: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
}