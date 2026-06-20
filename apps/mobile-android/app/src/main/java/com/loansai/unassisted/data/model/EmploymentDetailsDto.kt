package com.loansai.unassisted.data.model

import com.google.gson.annotations.SerializedName
import com.loansai.unassisted.domain.model.EmployerCategory
import com.loansai.unassisted.domain.model.Employer
import com.loansai.unassisted.domain.model.EmploymentDetails
import com.loansai.unassisted.domain.model.EmploymentType
import com.loansai.unassisted.domain.model.VerificationMethod
import java.time.LocalDate

/**
 * Data Transfer Object for Employment Details
 */
data class EmploymentDetailsDto(
    @SerializedName("employment_type")
    val employmentType: String,
    
    @SerializedName("employer_name")
    val employerName: String,
    
    @SerializedName("employer_id")
    val employerId: String? = null,
    
    @SerializedName("designation")
    val designation: String? = null,
    
    @SerializedName("department")
    val department: String? = null,
    
    @SerializedName("employee_id")
    val employeeId: String? = null,
    
    @SerializedName("work_email")
    val workEmail: String? = null,
    
    @SerializedName("office_address")
    val officeAddress: AddressDto? = null,
    
    @SerializedName("monthly_salary")
    val monthlySalary: Double,
    
    @SerializedName("annual_income")
    val annualIncome: Double? = null,
    
    @SerializedName("monthly_emi")
    val monthlyEmi: Double? = null,
    
    @SerializedName("joining_date")
    val joiningDate: String? = null,
    
    @SerializedName("is_verified")
    val isVerified: Boolean = false,
    
    @SerializedName("verification_method")
    val verificationMethod: String? = null
)

/**
 * Data Transfer Object for Employer
 */
data class EmployerDto(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("category")
    val category: String? = null,
    
    @SerializedName("industry")
    val industry: String? = null,
    
    @SerializedName("is_verified")
    val isVerified: Boolean = false,
    
    @SerializedName("email_domains")
    val emailDomains: List<String>? = null
)

/**
 * Mapping extension function to convert DTO to domain model
 */
fun EmploymentDetailsDto.toEmploymentDetails(): EmploymentDetails {
    return EmploymentDetails(
        employmentType = when (employmentType.uppercase()) {
            "PRIVATE_SECTOR" -> EmploymentType.PRIVATE_SECTOR
            "GOVERNMENT" -> EmploymentType.GOVERNMENT
            "SELF_EMPLOYED" -> EmploymentType.SELF_EMPLOYED
            "BUSINESS_OWNER" -> EmploymentType.BUSINESS_OWNER
            "RETIRED" -> EmploymentType.RETIRED
            else -> EmploymentType.OTHER
        },
        employerName = employerName,
        employerId = employerId,
        designation = designation,
        department = department,
        employeeId = employeeId,
        workEmail = workEmail,
        officeAddress = officeAddress?.toAddress(),
        monthlySalary = monthlySalary,
        annualIncome = annualIncome,
        monthlyEmi = monthlyEmi,
        joiningDate = joiningDate?.let { LocalDate.parse(it) },
        isVerified = isVerified,
        verificationMethod = verificationMethod?.let {
            when (it.uppercase()) {
                "WORK_EMAIL" -> VerificationMethod.WORK_EMAIL
                "ID_CARD" -> VerificationMethod.ID_CARD
                "SALARY_SLIP" -> VerificationMethod.SALARY_SLIP
                "HR_VERIFICATION" -> VerificationMethod.HR_VERIFICATION
                else -> VerificationMethod.NOT_VERIFIED
            }
        }
    )
}

/**
 * Mapping extension function to convert domain model to DTO
 */
fun EmploymentDetails.toEmploymentDetailsDto(): EmploymentDetailsDto {
    return EmploymentDetailsDto(
        employmentType = employmentType.name,
        employerName = employerName,
        employerId = employerId,
        designation = designation,
        department = department,
        employeeId = employeeId,
        workEmail = workEmail,
        officeAddress = officeAddress?.toAddressDto(),
        monthlySalary = monthlySalary,
        annualIncome = annualIncome,
        monthlyEmi = monthlyEmi,
        joiningDate = joiningDate?.toString(),
        isVerified = isVerified,
        verificationMethod = verificationMethod?.name
    )
}

/**
 * Mapping extension function to convert DTO to domain model
 */
fun EmployerDto.toEmployer(): Employer {
    return Employer(
        id = id,
        name = name,
        category = category?.let {
            when (it.uppercase()) {
                "PRIVATE_LIMITED" -> EmployerCategory.PRIVATE_LIMITED
                "PUBLIC_LIMITED" -> EmployerCategory.PUBLIC_LIMITED
                "GOVERNMENT" -> EmployerCategory.GOVERNMENT
                "MNC" -> EmployerCategory.MNC
                "STARTUP" -> EmployerCategory.STARTUP
                "SELF_EMPLOYED" -> EmployerCategory.SELF_EMPLOYED
                else -> EmployerCategory.OTHER
            }
        },
        industry = industry,
        isVerified = isVerified,
        emailDomains = emailDomains
    )
}