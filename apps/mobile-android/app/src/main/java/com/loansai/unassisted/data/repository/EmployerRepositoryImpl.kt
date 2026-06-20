package com.loansai.unassisted.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.loansai.unassisted.domain.model.Employer
import com.loansai.unassisted.domain.model.EmployerCategory
import com.loansai.unassisted.domain.repository.EmployerRepository
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmployerRepositoryImpl @Inject constructor(
    private val database: FirebaseDatabase
) : EmployerRepository {
    
    private val employersRef = database.getReference("employers")
    
    // Cache for search results to reduce Firebase calls
    private val searchCache = ConcurrentHashMap<String, List<Employer>>()
    
    /**
     * Search for employers by name
     */
    override suspend fun searchEmployers(query: String): Flow<Resource<List<Employer>>> = flow {
        emit(Resource.Loading())
        
        try {
            // Validate query
            if (query.length < 3) {
                emit(Resource.Success(emptyList()))
                return@flow
            }
            
            val trimmedQuery = query.trim().lowercase()
            
            // Check cache first for faster response
            if (searchCache.containsKey(trimmedQuery)) {
                AppLogger.d("Using cached results for query: $trimmedQuery")
                emit(Resource.Success(searchCache[trimmedQuery] ?: emptyList()))
                return@flow
            }
            
            // Log the database URL for verification
            AppLogger.d("Firebase Database URL: ${database.reference.toString()}")
            
            // Query Firebase for employers matching the search term (prefix search)
            val querySnapshot = employersRef
                .orderByChild("nameLowercase")
                .startAt(trimmedQuery)
                .endAt(trimmedQuery + "\uf8ff")
                .limitToFirst(10)
                .get()
                .await()
            
            val employers = querySnapshot.children.mapNotNull { snapshot ->
                try {
                    val id = snapshot.key ?: return@mapNotNull null
                    val name = snapshot.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val cin = snapshot.child("cin").getValue(String::class.java) ?: ""
                    val status = snapshot.child("status").getValue(String::class.java) ?: ""
                    val domain = snapshot.child("domain").getValue(String::class.java) ?: ""
                    val alias = snapshot.child("alias").getValue(String::class.java) ?: ""
                    
                    Employer(
                        id = id,
                        name = name,
                        category = determineCategory(cin),
                        industry = null, // Could be added later if needed
                        isVerified = status.equals("Active", ignoreCase = true),
                        emailDomains = if (domain.isNotBlank()) listOf(domain) else null
                    )
                } catch (e: Exception) {
                    AppLogger.e("Error parsing employer data: ${e.message}")
                    null
                }
            }
            
            // Cache the results
            searchCache[trimmedQuery] = employers
            
            // Limit cache size to prevent memory issues
            if (searchCache.size > 50) {
                // Remove random entries to keep size manageable
                val keysToRemove = searchCache.keys.shuffled().take(10)
                keysToRemove.forEach { searchCache.remove(it) }
            }
            
            emit(Resource.Success(employers))
            
        } catch (e: Exception) {
            val errorMessage = "Error searching employers: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Determine employer category from CIN
     */
    private fun determineCategory(cin: String): EmployerCategory {
        return when {
            cin.contains("PTC") -> EmployerCategory.PRIVATE_LIMITED
            cin.contains("PLC") -> EmployerCategory.PUBLIC_LIMITED
            cin.contains("GOV") -> EmployerCategory.GOVERNMENT
            cin.startsWith("L") -> EmployerCategory.PUBLIC_LIMITED // Listed companies
            cin.startsWith("U") -> EmployerCategory.PRIVATE_LIMITED // Unlisted companies
            else -> EmployerCategory.OTHER
        }
    }
    
    /**
     * Get employer details by ID
     */
    override suspend fun getEmployerDetails(employerId: String): Flow<Resource<Employer>> = flow {
        emit(Resource.Loading())
        
        try {
            val snapshot = employersRef.child(employerId).get().await()
            
            if (snapshot.exists()) {
                val name = snapshot.child("name").getValue(String::class.java) ?: return@flow
                val cin = snapshot.child("cin").getValue(String::class.java) ?: ""
                val status = snapshot.child("status").getValue(String::class.java) ?: ""
                val domain = snapshot.child("domain").getValue(String::class.java) ?: ""
                
                val employer = Employer(
                    id = employerId,
                    name = name,
                    category = determineCategory(cin),
                    industry = null,
                    isVerified = status.equals("Active", ignoreCase = true),
                    emailDomains = if (domain.isNotBlank()) listOf(domain) else null
                )
                
                emit(Resource.Success(employer))
            } else {
                emit(Resource.Error("Employer not found"))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting employer details: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Verify work email domain against employer
     */
    override suspend fun verifyWorkEmailDomain(
        employerId: String,
        emailDomain: String
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val snapshot = employersRef.child(employerId).child("domain").get().await()
            val domain = snapshot.getValue(String::class.java) ?: ""
            
            // If employer has no domain or email domain is empty, verification fails
            if (domain.isBlank() || emailDomain.isBlank()) {
                emit(Resource.Success(false))
                return@flow
            }
            
            // Check if domain matches
            val isValid = emailDomain.equals(domain, ignoreCase = true)
            emit(Resource.Success(isValid))
        } catch (e: Exception) {
            val errorMessage = "Error verifying email domain: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    /**
     * Get valid email domains for an employer
     */
    override suspend fun getEmployerEmailDomains(employerId: String): Flow<Resource<List<String>>> = flow {
        emit(Resource.Loading())
        
        try {
            val snapshot = employersRef.child(employerId).child("domain").get().await()
            val domain = snapshot.getValue(String::class.java)
            
            val domains = if (domain != null && domain.isNotBlank()) {
                listOf(domain)
            } else {
                emptyList()
            }
            
            emit(Resource.Success(domains))
        } catch (e: Exception) {
            val errorMessage = "Error getting employer email domains: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
}