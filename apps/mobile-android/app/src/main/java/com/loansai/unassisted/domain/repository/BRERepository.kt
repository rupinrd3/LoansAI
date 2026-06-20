package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.BREInput
import com.loansai.unassisted.domain.model.DecisionStatus
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.LoanOffer
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Business Rules Engine (BRE) operations
 */
interface BRERepository {
    suspend fun startBREProcess(applicationId: String): Flow<Resource<String>>
    suspend fun getLatestDecision(applicationId: String): Flow<Resource<DecisionStatus>>
    suspend fun getLoanOffer(applicationId: String): Flow<Resource<LoanOffer>>
    suspend fun updateApplicationWithDecision(applicationId: String): Flow<Resource<LoanApplication>>
    suspend fun prepareBREInput(applicationId: String): Flow<Resource<BREInput>>
    
    // Either keep this method (and implement it) or remove it from the interface
    suspend fun submitBREInput(breInput: BREInput): Flow<Resource<String>>
}