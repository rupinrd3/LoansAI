package com.loansai.unassisted.domain.model

import java.time.LocalDate

/**
 * Domain model for Personal Information
 */
data class PersonalInfo(
    val name: String,
    val dateOfBirth: LocalDate? = null,
    val email: String,
    val gender: Gender? = null,
    val maritalStatus: MaritalStatus? = null,
    val address: Address,
    val alternatePhoneNumber: String? = null
)

/**
 * Address model
 */
data class Address(
    val addressLine1: String,
    val addressLine2: String? = null,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String = "India",
    val addressType: AddressType = AddressType.CURRENT,
    val yearsAtAddress: Float? = null
)

/**
 * Marital status enum
 */
enum class MaritalStatus {
    SINGLE,
    MARRIED,
    DIVORCED,
    WIDOWED,
    OTHER
}

/**
 * Address type enum
 */
enum class AddressType {
    CURRENT,
    PERMANENT,
    OFFICE
}