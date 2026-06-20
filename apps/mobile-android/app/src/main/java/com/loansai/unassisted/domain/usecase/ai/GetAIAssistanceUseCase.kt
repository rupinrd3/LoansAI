package com.loansai.unassisted.domain.usecase.ai

import com.loansai.unassisted.domain.model.AIAssistantMessage
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.MessageType
import com.loansai.unassisted.domain.repository.AIRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for getting AI assistance during the application process
 */
class GetAIAssistanceUseCase @Inject constructor(
    private val aiRepository: AIRepository
) {
    /**
     * Gets AI assistance for a specific context
     *
     * @param context The context to get assistance for (e.g., form field, document, etc.)
     * @param application The current loan application
     * @return Flow of Resource<AIAssistantMessage> with suggestions or assistance
     */
    suspend operator fun invoke(
        context: Map<String, Any>,
        application: LoanApplication? = null
    ): Flow<Resource<AIAssistantMessage>> {
        val queryText = context["query"] as? String ?: "How can I help you?"
        val screen = context["screen"] as? String
        
        return aiRepository.getAssistance(
            query = queryText,
            applicationId = application?.id,
            screen = screen,
            applicationContext = context
        )
    }
    
    /**
     * Gets final application review suggestions from AI
     *
     * @param application The complete loan application
     * @return List of suggestions or corrections
     */
    suspend fun getFinalReview(application: LoanApplication): Flow<Resource<List<String>>> {
        val screen = "review_and_submit"
        return aiRepository.getSuggestions(
            screen = screen,
            applicationId = application.id,
            applicationContext = mapOf("application" to application)
        ).map { result ->
            when(result) {
                is Resource.Success -> Resource.Success(result.data.map { it.message })
                is Resource.Error -> Resource.Error(result.message)
                is Resource.Loading -> Resource.Loading()
            }
        }
    }
}