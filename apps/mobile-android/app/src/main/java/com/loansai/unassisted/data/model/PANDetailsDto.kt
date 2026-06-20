package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName
import com.loansai.unassisted.domain.model.Gender
import com.loansai.unassisted.domain.model.PANDetails
import com.loansai.unassisted.domain.model.PaymentHistory
import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.util.converter.BureauConverter
import java.time.LocalDate

/**
 * Data Transfer Object for PAN verification details
 */
data class PANDetailsDto(
    @SerializedName("pan_number")
    val panNumber: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("father_name")
    val fatherName: String? = null,
    
    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null,
    
    @SerializedName("is_verified")
    val isVerified: Boolean = false,
    
    @SerializedName("verification_date")
    val verificationDate: String? = null
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun PANDetailsDto.toPANDetails(): PANDetails {
    return PANDetails(
        panNumber = panNumber,
        name = name,
        fatherName = fatherName,
        dateOfBirth = dateOfBirth?.let { LocalDate.parse(it) },
        isVerified = isVerified,
        verificationDate = verificationDate?.let { LocalDate.parse(it) }
    )
}

/**
 * PAN verification request model
 */
data class PANVerificationRequest(
    @SerializedName("pan_number")
    val panNumber: String
)

/**
 * Data Transfer Object for Credit Bureau Report
 */
data class BureauReportDto(
    @SerializedName("customer_id")
    val customerID: String,
    
    @SerializedName("pan_number")
    val panNumber: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null,
    
    @SerializedName("gender")
    val gender: String? = null,
    
    @SerializedName("email")
    val email: String? = null,
    
    @SerializedName("address")
    val address: String? = null,
    
    @SerializedName("credit_score")
    val creditScore: Int? = null,
    
    @SerializedName("active_loan_accounts")
    val activeLoanAccounts: Int = 0,
    
    @SerializedName("total_outstanding_amount")
    val totalOutstandingAmount: Double = 0.0,
    
    @SerializedName("total_emi_amount")
    val totalEMIAmount: Double = 0.0,
    
    @SerializedName("credit_card_utilization")
    val creditCardUtilization: Double? = null,
    
    @SerializedName("payment_history")
    val paymentHistory: PaymentHistoryDto? = null,
    
    @SerializedName("last_updated")
    val lastUpdated: String? = null
)

/**
 * Data Transfer Object for Payment History
 */
data class PaymentHistoryDto(
    @SerializedName("on_time_payments")
    val onTimePayments: Int = 0,
    
    @SerializedName("late_payments")
    val latePayments: Int = 0,
    
    @SerializedName("missed_payments")
    val missedPayments: Int = 0,
    
    @SerializedName("total_payments")
    val totalPayments: Int = 0
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun BureauReportDto.toBureauReport(): BureauReport {
    return BureauConverter.fromDto(this)
}


/**
 * Mapping extension function to convert Payment History DTO to domain model
 */
fun PaymentHistoryDto.toPaymentHistory(): PaymentHistory {
    return PaymentHistory(
        onTimePayments = onTimePayments,
        latePayments = latePayments,
        missedPayments = missedPayments,
        totalPayments = totalPayments
    )
}