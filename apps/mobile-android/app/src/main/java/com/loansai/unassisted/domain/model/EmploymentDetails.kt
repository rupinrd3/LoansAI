package com.loansai.unassisted.domain.model

import java.time.LocalDate

/**
 * Domain model for Employment Details
 */
data class EmploymentDetails(
    val employmentType: EmploymentType,
    val employerName: String,
    val employerId: String? = null, // Reference to the employer database entry (if available)
    val designation: String? = null,
    val department: String? = null,
    val employeeId: String? = null,
    val workEmail: String? = null,
    val officeAddress: Address? = null,
    val monthlySalary: Double,
    val annualIncome: Double? = null,
    val monthlyEmi: Double? = null, // New field for monthly EMI obligations
    val joiningDate: LocalDate? = null,
    val isVerified: Boolean = false,
    val verificationMethod: VerificationMethod? = null
)

/**
 * Employer model
 */
data class Employer(
    val id: String,
    val name: String,
    val category: EmployerCategory? = null,
    val industry: String? = null,
    val isVerified: Boolean = false,
    val emailDomains: List<String>? = null
)

/**
 * Employment type enum
 */
enum class EmploymentType {
    PRIVATE_SECTOR,
    GOVERNMENT,
    SELF_EMPLOYED,
    BUSINESS_OWNER,
    RETIRED,
    OTHER
}

/**
 * Employer category enum
 */
enum class EmployerCategory {
    PRIVATE_LIMITED,
    PUBLIC_LIMITED,
    GOVERNMENT,
    MNC,
    STARTUP,
    SELF_EMPLOYED,
    OTHER
}

/**
 * Verification method enum
 */
enum class VerificationMethod {
    WORK_EMAIL,
    ID_CARD,
    SALARY_SLIP,
    HR_VERIFICATION,
    NOT_VERIFIED
}