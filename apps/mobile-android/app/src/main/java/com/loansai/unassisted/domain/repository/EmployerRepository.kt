package com.loansai.unassisted.domain.repository

import com.loansai.unassisted.domain.model.Employer
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for employer data lookup
 */
interface EmployerRepository {
    
    /**
     * Search for employers by name
     *
     * @param query The search query (employer name)
     * @return Flow of Resource<List<Employer>> with matching employers
     */
    suspend fun searchEmployers(query: String): Flow<Resource<List<Employer>>>
    
    /**
     * Get employer details by ID
     *
     * @param employerId The ID of the employer
     * @return Flow of Resource<Employer> with employer details
     */
    suspend fun getEmployerDetails(employerId: String): Flow<Resource<Employer>>
    
    /**
     * Verify work email domain against employer
     *
     * @param employerId The ID of the employer
     * @param emailDomain The email domain to verify
     * @return Flow of Resource<Boolean> indicating if the domain matches the employer
     */
    suspend fun verifyWorkEmailDomain(
        employerId: String,
        emailDomain: String
    ): Flow<Resource<Boolean>>
    
    /**
     * Get valid email domains for an employer
     *
     * @param employerId The ID of the employer
     * @return Flow of Resource<List<String>> with valid email domains
     */
    suspend fun getEmployerEmailDomains(employerId: String): Flow<Resource<List<String>>>
}