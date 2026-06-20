package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.domain.model.PANDetails
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for PAN verification and bureau report operations
 */
interface PANRepository {
    
    /**
     * Verify a PAN number with NSDL
     *
     * @param panNumber The PAN number to verify
     * @return Flow of Resource<PANDetails> with verification details
     */
    suspend fun verifyPAN(panNumber: String): Flow<Resource<PANDetails>>

    /**
    * Check if bureau data exists in Appwrite for a PAN number
    * 
    * @param panNumber The PAN number to check
    * @return true if data exists, false otherwise
    */
    suspend fun checkBureauDataExists(panNumber: String): Boolean

    /**
     * Fetch bureau report for a PAN number
     *
     * @param panNumber The PAN number
     * @return Flow of Resource<BureauReport> with bureau report details
     */
    suspend fun fetchBureauReport(panNumber: String): Flow<Resource<BureauReport>>
    
    /**
     * Save bureau report to the application
     *
     * @param applicationId The ID of the application
     * @param bureauReport The bureau report to save
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun saveBureauReport(
        applicationId: String,
        bureauReport: BureauReport
    ): Flow<Resource<Boolean>>
    
    /**
     * Get bureau report for an application
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<BureauReport> with bureau report details
     */
    fun getBureauReport(applicationId: String): Flow<Resource<BureauReport>>
}