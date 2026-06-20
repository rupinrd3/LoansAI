package com.loansai.unassisted.service.appwrite

import com.loansai.unassisted.domain.model.BorrowerSummary
import com.loansai.unassisted.domain.model.Enquiry
import com.loansai.unassisted.domain.model.Tradeline
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Interface for Appwrite service operations
 */
interface AppwriteService {
    
    /**
     * Get borrower summary by PAN number
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<BorrowerSummary>
     */
    suspend fun getBorrowerSummary(panNumber: String): Resource<BorrowerSummary>
    
    /**
     * Get enquiries by PAN number
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<List<Enquiry>>
     */
    suspend fun getEnquiries(panNumber: String): Resource<List<Enquiry>>
    
    /**
     * Get tradelines by PAN number
     *
     * @param panNumber The PAN number to query
     * @return Flow of Resource<List<Tradeline>>
     */
    suspend fun getTradelines(panNumber: String): Resource<List<Tradeline>>
    
    /**
     * Fetch and cache borrower summary
     *
     * @param panNumber The PAN number to query
     * @return Resource<BorrowerSummary>
     */
    suspend fun fetchAndCacheBorrowerSummary(panNumber: String): Resource<BorrowerSummary>
    
    /**
     * Fetch and cache enquiries
     *
     * @param panNumber The PAN number to query
     * @return Resource<List<Enquiry>>
     */
    suspend fun fetchAndCacheEnquiries(panNumber: String): Resource<List<Enquiry>>
    
    /**
     * Fetch and cache tradelines
     *
     * @param panNumber The PAN number to query
     * @return Resource<List<Tradeline>>
     */
    suspend fun fetchAndCacheTradelines(panNumber: String): Resource<List<Tradeline>>
    
    /**
     * Check if bureau data exists for a PAN number
     *
     * @param panNumber The PAN number to check
     * @return Boolean indicating whether data exists
     */
    suspend fun hasBureauData(panNumber: String): Boolean
    
    /**
     * Get bureau score for a PAN number
     *
     * @param panNumber The PAN number to query
     * @return Int? representing the credit score (null if not available)
     */
    suspend fun getBureauScore(panNumber: String): Int?
}