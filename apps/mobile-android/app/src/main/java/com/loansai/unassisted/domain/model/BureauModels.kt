package com.loansai.unassisted.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Data class for bureau address information
 */
data class BureauAddress(
    val addressLine1: String,
    val addressLine2: String? = null,
    val city: String,
    val state: String,
    val pincode: String,
    val addressType: String = "Residential",
    val residenceType: String? = null
)

/**
 * Data class for bureau account summary
 */
data class BureauAccountSummary(
    val totalAccounts: Int,
    val activeAccounts: Int,
    val closedAccounts: Int,
    val totalCreditLimit: Long,
    val totalOutstanding: Long,
    val totalOverdue: Long,
    val delinquentAccountsCount: Int,
    val suitFiledAccountsCount: Int,
    val writtenOffAccountsCount: Int
)