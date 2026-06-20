package com.loansai.unassisted.domain.model

/**
 * Domain model for a tradeline (loan/credit) from credit bureau report
 * Simplified model for UI display in Bureau Confirmation screen
 */
data class TradelineItem(
    val id: String,
    val memberName: String,
    val accountType: String,
    val accountNumber: String,
    val currentBalance: Int,
    val emiAmount: Int,
    val dateOpened: String?,
    val dateClosed: String?
)
