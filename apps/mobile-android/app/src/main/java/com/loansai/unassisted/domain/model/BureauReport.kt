package com.loansai.unassisted.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Domain model for Bureau Report
 */
data class BureauReport(
    val id: String,
    val panNumber: String,
    val controlNumber: String? = null,
    val customerName: String? = null,
    val creditScore: Int? = null,
    val scoreDate: LocalDate? = null,
    val bureauType: BureauType? = null,
    val reportDate: String? = null,
    val dateOfBirth: LocalDate? = null,
    val gender: String? = null,
    val addresses: List<BureauAddress> = emptyList(),
    val mobilePhones: List<String> = emptyList(),
    val email: String? = null,
    val accountSummary: BureauAccountSummary? = null,
    val inquiryCountLast30Days: Int = 0,
    val hasExistingDefaultOrWriteOff: Boolean = false,
    val totalWrittenOffAmount: Long = 0L,
    val delinquentStatus: String = "Standard",
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Bureau report type enum
 */
enum class BureauType {
    CIBIL,
    EXPERIAN,
    EQUIFAX,
    CRIF_HIGHMARK
}

/**
 * Data class for bureau address information
 * Note: Moved to separate file for better organization
 */

/**
 * Data class for bureau account summary
 * Note: Moved to separate file for better organization
 */

/**
 * Data class for bureau login credentials
 */
data class BureauLoginCredential(
    val panNumber: String,
    val memberCode: String
)

/**
 * Data class for bureau login response
 */
data class BureauLoginResponse(
    val token: String,
    val expiresAt: Long,
    val refreshToken: String? = null
)