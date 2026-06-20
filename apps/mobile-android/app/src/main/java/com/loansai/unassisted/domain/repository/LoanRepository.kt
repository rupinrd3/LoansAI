package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.DecisionStatus
import com.loansai.unassisted.domain.model.EmploymentDetails
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.LoanOffer
import com.loansai.unassisted.domain.model.LoanOfferUI
import com.loansai.unassisted.domain.model.PersonalInfo
import com.loansai.unassisted.domain.model.ScreenVisit
import com.loansai.unassisted.domain.model.SectionTiming
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/**
 * Repository interface for loan application operations
 * Updated for v1.5.0 to support Bureau Confirmation and Obligation Refinement
 */
interface LoanRepository {
    
    /**
     * Create a new loan application
     *
     * @param userId The ID of the user creating the application
     * @return Flow of Resource<LoanApplication> with the created application
     */
    suspend fun createApplication(userId: String): Flow<Resource<LoanApplication>>
    
    /**
     * Get an existing loan application by ID
     *
     * @param applicationId The ID of the application to retrieve
     * @return Flow of Resource<LoanApplication> with the application details
     */
    suspend fun getApplication(applicationId: String): Flow<Resource<LoanApplication>>
    
    /**
     * Get all applications for a user
     *
     * @param userId The ID of the user
     * @return Flow of Resource<List<LoanApplication>> with all user applications
     */
    suspend fun getUserApplications(userId: String): Flow<Resource<List<LoanApplication>>>
    
    /**
     * Update the personal information for an application
     *
     * @param applicationId The ID of the application
     * @param personalInfo The personal information to update
     * @return Flow of Resource<LoanApplication> with the updated application
     */
    suspend fun updatePersonalInfo(
        applicationId: String,
        personalInfo: PersonalInfo
    ): Flow<Resource<LoanApplication>>
    
    /**
     * Update the employment details for an application
     *
     * @param applicationId The ID of the application
     * @param employmentDetails The employment details to update
     * @return Flow of Resource<LoanApplication> with the updated application
     */
    suspend fun updateEmploymentDetails(
        applicationId: String,
        employmentDetails: EmploymentDetails
    ): Flow<Resource<LoanApplication>>
    
    /**
     * Update the application status
     *
     * @param applicationId The ID of the application
     * @param status The new application status
     * @return Flow of Resource<LoanApplication> with the updated application
     */
    suspend fun updateApplicationStatus(
        applicationId: String,
        status: ApplicationStatus
    ): Flow<Resource<LoanApplication>>

    /**
     * Update application step progress in Firestore
     *
     * @param applicationId The ID of the application
     * @param completedSteps List of completed application steps
     * @param currentStep The current application step
     * @return Boolean indicating success or failure
     */
    suspend fun updateApplicationStepProgress(
        applicationId: String,
        completedSteps: List<ApplicationStep>,
        currentStep: ApplicationStep
    ): Boolean

    /**
     * Save application progress with Flow response
     *
     * @param applicationId The ID of the application
     * @param completedSteps List of completed application steps
     * @param currentStep The current application step
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun saveApplicationProgressWithFlow(
        applicationId: String,
        completedSteps: List<ApplicationStep>,
        currentStep: ApplicationStep
    ): Flow<Resource<Boolean>>

    /**
     * Submit the application for review (API version)
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<LoanApplication> with the submitted application
     */
    suspend fun submitApplicationFlow(applicationId: String): Flow<Resource<LoanApplication>>
    
    /**
     * Accept a loan offer
     *
     * @param applicationId The ID of the application
     * @param offerId The ID of the offer
     * @param loanAmount The selected loan amount
     * @param tenure The selected tenure in months
     * @return Flow of Resource<LoanApplication> with the updated application
     */
    suspend fun acceptOffer(
        applicationId: String,
        offerId: String,
        loanAmount: Double,
        tenure: Int
    ): Flow<Resource<LoanApplication>>
    
    /**
     * Get the application decision
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<DecisionStatus> with the decision status
     */
    suspend fun getApplicationDecision(applicationId: String): Flow<Resource<DecisionStatus>>
    
    /**
     * Get EMI details for a loan configuration
     *
     * @param amount The loan amount
     * @param tenure The tenure in months
     * @param interestRate The annual interest rate
     * @return Flow of Resource<LoanOffer> with the EMI details
     */
    suspend fun calculateEMI(
        amount: Double,
        tenure: Int,
        interestRate: Double
    ): Flow<Resource<LoanOffer>>
    
    /**
     * Observe the current application
     * 
     * @return StateFlow of the current LoanApplication (null if none loaded)
     */
    fun observeCurrentApplication(): StateFlow<LoanApplication?>
    
    /**
     * Get the current application
     * 
     * @return The current LoanApplication or null if none is loaded
     */
    suspend fun getCurrentApplication(): LoanApplication?

    /**
     * Ensure that application is loaded in memory
     *
     * @param applicationId The ID of the application to load
     * @return Boolean indicating if application was successfully loaded
     */
    suspend fun ensureApplicationLoaded(applicationId: String): Boolean
    
    /**
     * Get the loan offer for the current application
     * 
     * @return A UI model of the loan offer or null if not available
     */
    suspend fun getLoanOffer(): LoanOfferUI?
    
    /**
     * Save the user's loan offer selection
     * 
     * @param amount The selected loan amount
     * @param tenure The selected tenure in months
     * @param interestRate The interest rate
     * @param emi The calculated EMI amount
     * @param processingFee The processing fee amount
     * @return True if the selection was saved successfully
     */
    suspend fun saveLoanOfferSelection(
        amount: Long,
        tenure: Int,
        interestRate: Float,
        emi: Long,
        processingFee: Long
    ): Boolean
    
    /**
     * Submit the final application
     * 
     * @param applicationId The ID of the application to submit
     * @return True if the application was submitted successfully
     */
    suspend fun submitApplication(applicationId: String): Boolean

    /**
     * Update document associations for an application
     *
     * @param applicationId The ID of the application
     * @param documentId The ID of the document to associate
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun addDocumentToApplication(
        applicationId: String,
        documentId: String
    ): Flow<Resource<Boolean>>    

    /**
     * Update application metadata with new information
     *
     * @param applicationId The ID of the application to update
     * @param metadataField The field to update (e.g., "screenVisits", "sectionTimings")
     * @param data The data to add to the metadata field
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun updateApplicationMetadata(
        applicationId: String,
        metadataField: String,
        data: Any
    ): Flow<Resource<Boolean>>

    /**
     * Start tracking a form section
     *
     * @param applicationId The ID of the application
     * @param screenName The screen containing the section
     * @param sectionName The logical name of the section
     * @return The ID of the section timing record
     */
    suspend fun startSectionTiming(
        applicationId: String,
        screenName: String,
        sectionName: String
    ): String

    /**
     * Complete a form section timing
     *
     * @param applicationId The ID of the application
     * @param sectionTimingId The ID of the section timing record
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun completeSectionTiming(
        applicationId: String,
        sectionTimingId: String
    ): Flow<Resource<Boolean>>

    /**
     * Add a screen visit to the application metadata
     *
     * @param applicationId The ID of the application
     * @param screenVisit The screen visit data to add
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun addScreenVisit(
        applicationId: String,
        screenVisit: ScreenVisit
    ): Flow<Resource<Boolean>>

    /**
     * Add section timing to the application metadata
     *
     * @param applicationId The ID of the application
     * @param sectionTiming The section timing data to add
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun addSectionTiming(
        applicationId: String,
        sectionTiming: SectionTiming
    ): Flow<Resource<Boolean>>

    /**
     * Log a metadata event to the application's metadata subcollection
     *
     * @param applicationId The ID of the application
     * @param eventType The type of event to log
     * @param eventData Additional data for the event
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun logMetadataEvent(
        applicationId: String,
        eventType: String,
        eventData: Map<String, Any>
    ): Flow<Resource<Boolean>>

    /**
     * Add device info to metadata
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun addDeviceInfo(applicationId: String): Flow<Resource<Boolean>>
    
    /**
     * Get bureau score from application or bureau report
     * Added in v1.5.0 to support conditional Bureau Confirmation screen
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<Int?> with the bureau score or null if not available
     */
    suspend fun getBureauScore(applicationId: String): Flow<Resource<Int?>>
}