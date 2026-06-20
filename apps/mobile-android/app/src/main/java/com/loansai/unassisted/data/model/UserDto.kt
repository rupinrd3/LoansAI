package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName
import com.loansai.unassisted.domain.model.User

/**
 * Data Transfer Object for User
 */
data class UserDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("phone_number")
    val phoneNumber: String,
    
    @SerializedName("auth_token")
    val token: String,
    
    @SerializedName("is_privacy_policy_accepted")
    val isPrivacyPolicyAccepted: Boolean,
    
    @SerializedName("current_application_id")
    val currentApplicationId: String? = null
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun UserDto.toUser(): User {
    return User(
        id = id,
        phoneNumber = phoneNumber,
        token = token,
        isPrivacyPolicyAccepted = isPrivacyPolicyAccepted,
        currentApplicationId = currentApplicationId
    )
}

/**
 * Authentication request models
 */
data class OTPRequest(
    @SerializedName("phone_number")
    val phoneNumber: String
)

data class OTPVerificationRequest(
    @SerializedName("phone_number")
    val phoneNumber: String,
    
    @SerializedName("otp")
    val otp: String
)

data class PrivacyPolicyAcceptanceRequest(
    @SerializedName("user_id")
    val userId: String,
    
    @SerializedName("is_accepted")
    val isAccepted: Boolean,
    
    @SerializedName("version")
    val version: String = "1.0"
)