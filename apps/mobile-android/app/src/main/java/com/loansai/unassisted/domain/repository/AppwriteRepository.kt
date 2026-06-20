package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.BorrowerSummary
import com.loansai.unassisted.domain.model.Enquiry
import com.loansai.unassisted.domain.model.Tradeline
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Appwrite bureau data operations
 */
interface AppwriteRepository {
    
    /**
     * Get a borrower summary by PAN number
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<BorrowerSummary> with the borrower's summary data
     */
    suspend fun getBorrowerSummary(
        panNumber: String
    ): Flow<Resource<BorrowerSummary>>
    
    /**
     * Get all enquiries for a PAN number
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<List<Enquiry>> with the borrower's enquiries
     */
    suspend fun getEnquiries(
        panNumber: String
    ): Flow<Resource<List<Enquiry>>>
    
    /**
     * Get all tradelines (credit accounts) for a PAN number
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<List<Tradeline>> with the borrower's tradelines
     */
    suspend fun getTradelines(
        panNumber: String
    ): Flow<Resource<List<Tradeline>>>
    
    /**
     * Get only active tradelines for a PAN number
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<List<Tradeline>> with the borrower's active tradelines
     */
    suspend fun getActiveTradelines(
        panNumber: String
    ): Flow<Resource<List<Tradeline>>>
    
    /**
     * Fetch all bureau data for a PAN number (summary, enquiries, tradelines)
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun fetchAllBureauData(
        panNumber: String
    ): Flow<Resource<Boolean>>
}
