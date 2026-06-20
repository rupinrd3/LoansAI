package com.loansai.unassisted.domain.usecase.application

import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for creating a new loan application
 */
class CreateApplicationUseCase @Inject constructor(
    private val loanRepository: LoanRepository,
    private val userRepository: UserRepository
) {
    /**
     * Creates a new loan application
     *
     * @return The created loan application
     */
    suspend operator fun invoke(): LoanApplication {
        // Get the current user ID
        val userResource = userRepository.getCurrentUser().first()
        if (userResource is Resource.Success) {
            val userId = userResource.data.id
            
            // Create application with the user ID
            val applicationResource = loanRepository.createApplication(userId).first()
            if (applicationResource is Resource.Success) {
                return applicationResource.data
            } else {
                throw Exception(applicationResource.errorOrNull() ?: "Failed to create application")
            }
        } else {
            throw Exception(userResource.errorOrNull() ?: "User not authenticated")
        }
    }
}