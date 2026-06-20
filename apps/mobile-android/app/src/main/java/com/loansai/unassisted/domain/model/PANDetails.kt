package com.loansai.unassisted.domain.model

import java.time.LocalDate

/**
 * Domain model for PAN verification details
 */
data class PANDetails(
    val panNumber: String,
    val name: String,
    val fatherName: String? = null,
    val dateOfBirth: LocalDate? = null,
    val isVerified: Boolean = false,
    val verificationDate: LocalDate? = null
)


/**
 * Payment history details
 */
data class PaymentHistory(
    val onTimePayments: Int = 0,
    val latePayments: Int = 0,
    val missedPayments: Int = 0,
    val totalPayments: Int = 0
) {
    val onTimePercentage: Float
        get() = if (totalPayments > 0) {
            (onTimePayments.toFloat() / totalPayments) * 100
        } else {
            0f
        }
}

/**
 * Gender enum
 */
enum class Gender {
    MALE,
    FEMALE,
    OTHER
}