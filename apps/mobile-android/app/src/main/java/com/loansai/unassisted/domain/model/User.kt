package com.loansai.unassisted.domain.model

/**
 * Domain model for User information
 */
data class User(
    val id: String = "",
    val phoneNumber: String = "",
    val token: String = "",
    val isPrivacyPolicyAccepted: Boolean = false,
    val currentApplicationId: String? = null
)