package com.loansai.unassisted.domain.model

/**
 * Domain model for Borrower Summary from Appwrite
 */
data class BorrowerSummary(
    val panNumber: String,
    val controlNumber: String?,
    val customerName: String?,
    val creditScore: Int?,
    val reportDate: String?,
    val dateOfBirth: String?,
    val gender: String?,
    val addresses: String?,
    val contacts: String?,
    val email: String?,
    val totalAccounts: Int,
    val openAccounts: Int,
    val closedAccounts: Int,
    val totalLoanAmount: Double,
    val currentBalance: Double,
    val totalOverdueAmount: Double,
    val suitFiled: Boolean,
    val wilfulDefault: Boolean,
    val writtenOffStatus: Boolean,
    val delinquencyStatus: String?
)

/**
 * Domain model for Enquiry from Appwrite
 */
data class Enquiry(
    val id: String,
    val panNumber: String,
    val enquiryDate: String?,
    val memberName: String?,
    val purpose: String?,
    val type: String?,
    val amount: Double?
)

/**
 * Domain model for Tradeline from Appwrite
 */
data class Tradeline(
    val id: String,
    val panNumber: String,
    val memberName: String,
    val accountType: String,
    val accountNumber: String,
    val ownership: String?,
    val creditLimit: Double?,
    val highCredit: Double?,
    val currentBalance: Double?,
    val amountOverdue: Double?,
    val rateOfInterest: Double?,
    val repaymentTenure: Int?,
    val emiAmount: Double?,
    val dateOpened: String?,
    val dateClosed: String?,
    val lastPaymentDate: String?,
    val dateReported: String?,
    val facilityStatus: String?,
    val suitFiled: String?,
    val paymentFrequency: String?,
    val paymentHistory: String?,
    val writtenOffTotal: Double?,
    val writtenOffPrincipal: Double?,
    val controlNumber: String?
)
