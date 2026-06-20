package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName
import com.loansai.unassisted.domain.model.Address
import com.loansai.unassisted.domain.model.AddressType
import com.loansai.unassisted.domain.model.Gender
import com.loansai.unassisted.domain.model.MaritalStatus
import com.loansai.unassisted.domain.model.PersonalInfo
import java.time.LocalDate

/**
 * Data Transfer Object for Personal Information
 */
data class PersonalInfoDto(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("date_of_birth")
    val dateOfBirth: String? = null,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("gender")
    val gender: String? = null,
    
    @SerializedName("marital_status")
    val maritalStatus: String? = null,
    
    @SerializedName("address")
    val address: AddressDto,
    
    @SerializedName("alternate_phone_number")
    val alternatePhoneNumber: String? = null
)

/**
 * Data Transfer Object for Address
 */
data class AddressDto(
    @SerializedName("address_line_1")
    val addressLine1: String,
    
    @SerializedName("address_line_2")
    val addressLine2: String? = null,
    
    @SerializedName("city")
    val city: String,
    
    @SerializedName("state")
    val state: String,
    
    @SerializedName("postal_code")
    val postalCode: String,
    
    @SerializedName("country")
    val country: String = "India",
    
    @SerializedName("address_type")
    val addressType: String = "CURRENT",
    
    @SerializedName("years_at_address")
    val yearsAtAddress: Float? = null
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun PersonalInfoDto.toPersonalInfo(): PersonalInfo {
    return PersonalInfo(
        name = name,
        dateOfBirth = dateOfBirth?.let { LocalDate.parse(it) },
        email = email,
        gender = gender?.let { 
            when (it.uppercase()) {
                "MALE" -> Gender.MALE
                "FEMALE" -> Gender.FEMALE
                else -> Gender.OTHER
            }
        },
        maritalStatus = maritalStatus?.let { 
            when (it.uppercase()) {
                "SINGLE" -> MaritalStatus.SINGLE
                "MARRIED" -> MaritalStatus.MARRIED
                "DIVORCED" -> MaritalStatus.DIVORCED
                "WIDOWED" -> MaritalStatus.WIDOWED
                else -> MaritalStatus.OTHER
            }
        },
        address = address.toAddress(),
        alternatePhoneNumber = alternatePhoneNumber
    )
}

/**
 * Mapping extension function to convert domain model to DTO
 */
fun PersonalInfo.toPersonalInfoDto(): PersonalInfoDto {
    return PersonalInfoDto(
        name = name,
        dateOfBirth = dateOfBirth?.toString(),
        email = email,
        gender = gender?.name,
        maritalStatus = maritalStatus?.name,
        address = address.toAddressDto(),
        alternatePhoneNumber = alternatePhoneNumber
    )
}

/**
 * Mapping extension function to convert Address DTO to domain model
 */
fun AddressDto.toAddress(): Address {
    return Address(
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        city = city,
        state = state,
        postalCode = postalCode,
        country = country,
        addressType = when (addressType.uppercase()) {
            "CURRENT" -> AddressType.CURRENT
            "PERMANENT" -> AddressType.PERMANENT
            "OFFICE" -> AddressType.OFFICE
            else -> AddressType.CURRENT
        },
        yearsAtAddress = yearsAtAddress
    )
}

/**
 * Mapping extension function to convert domain model to DTO
 */
fun Address.toAddressDto(): AddressDto {
    return AddressDto(
        addressLine1 = addressLine1,
        addressLine2 = addressLine2,
        city = city,
        state = state,
        postalCode = postalCode,
        country = country,
        addressType = addressType.name,
        yearsAtAddress = yearsAtAddress
    )
}