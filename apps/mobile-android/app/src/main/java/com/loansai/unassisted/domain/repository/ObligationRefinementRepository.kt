package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.ObligationRefinement
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow
import com.loansai.unassisted.domain.model.TradelineItem

/**
 * Repository interface for Obligation Refinement operations
 */
interface ObligationRefinementRepository {
    
    /**
     * Save an obligation refinement record
     *
     * @param obligationRefinement The obligation refinement data to save
     * @return Flow of Resource<ObligationRefinement> with the saved data
     */
    suspend fun saveObligationRefinement(
        obligationRefinement: ObligationRefinement
    ): Flow<Resource<ObligationRefinement>>
    
    /**
     * Get an obligation refinement record
     *
     * @param refinementId The ID of the refinement record to retrieve
     * @return Flow of Resource<ObligationRefinement> with the refinement data
     */
    suspend fun getObligationRefinement(
        refinementId: String
    ): Flow<Resource<ObligationRefinement>>
    
    /**
     * Get all obligation refinement records for an application
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<List<ObligationRefinement>> with all refinement records
     */
    suspend fun getObligationRefinementsForApplication(
        applicationId: String
    ): Flow<Resource<List<ObligationRefinement>>>
    
    /**
     * Get the most recent obligation refinement record for an application
     *
     * @param applicationId The ID of the application
     * @return Flow of Resource<ObligationRefinement> with the most recent refinement
     */
    suspend fun getLatestObligationRefinement(
        applicationId: String
    ): Flow<Resource<ObligationRefinement>>
    
    /**
     * Trigger the recalculation of obligations via Gemini service
     *
     * @param obligationRefinement The obligation refinement data record (contains ID and user input)
     * @return Flow of Resource<Boolean> indicating success or failure
     */
    suspend fun triggerObligationRecalculation(
        obligationRefinement: ObligationRefinement,
        tradelineItems: List<TradelineItem>
    ): Flow<Resource<ObligationRefinement>>
    
    /**
     * Check the status of an ongoing obligation recalculation
     *
     * @param refinementId The ID of the refinement record to check
     * @return Flow of Resource<ObligationRefinement> with updated status
     */
    suspend fun checkRecalculationStatus(
        refinementId: String
    ): Flow<Resource<ObligationRefinement>>
}
