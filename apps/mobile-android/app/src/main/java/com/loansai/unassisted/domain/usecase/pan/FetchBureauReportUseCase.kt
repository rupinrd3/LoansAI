package com.loansai.unassisted.domain.usecase.pan

import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.domain.repository.PANRepository
import com.loansai.unassisted.util.extensions.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for fetching a bureau report using a PAN number
 */
class FetchBureauReportUseCase @Inject constructor(
    private val panRepository: PANRepository
) {
    /**
     * Fetches a bureau report using a PAN number
     *
     * @param panNumber The PAN number to use for fetching the bureau report
     * @return Bureau report data
     */
    suspend operator fun invoke(panNumber: String): BureauReport {
        val resource = panRepository.fetchBureauReport(panNumber).first()
        return when (resource) {
            is Resource.Success -> resource.data
            else -> throw Exception(resource.errorOrNull() ?: "Failed to fetch bureau report")
        }
    }
}