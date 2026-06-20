package com.loansai.unassisted.util.converter

import com.loansai.unassisted.data.model.BureauReportDto
import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.domain.model.BureauAccountSummary
import com.loansai.unassisted.domain.model.BureauAddress
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Utility object for converting between BureauReport and BureauReportDto
 */
object BureauConverter {
    
    /**
     * Convert DTO to domain model
     */
    fun fromDto(dto: BureauReportDto): BureauReport {
        return BureauReport(
            id = dto.customerID,
            panNumber = dto.panNumber,
            controlNumber = null, // This field doesn't exist in the DTO
            customerName = dto.name,
            creditScore = dto.creditScore,
            scoreDate = dto.dateOfBirth?.let { LocalDate.parse(it) },
            bureauType = null, // We'll set this as null since it's not in the DTO
            reportDate = dto.lastUpdated,
            dateOfBirth = dto.dateOfBirth?.let { LocalDate.parse(it) },
            gender = dto.gender,
            addresses = parseAddressFromString(dto.address),
            mobilePhones = emptyList(), // No phone numbers in DTO
            email = dto.email,
            accountSummary = BureauAccountSummary(
                totalAccounts = dto.activeLoanAccounts,
                activeAccounts = dto.activeLoanAccounts, // Using same value for now
                closedAccounts = 0,
                totalCreditLimit = 0L,
                totalOutstanding = dto.totalOutstandingAmount.toLong(),
                totalOverdue = 0L,
                delinquentAccountsCount = 0,
                suitFiledAccountsCount = 0,
                writtenOffAccountsCount = 0
            ),
            inquiryCountLast30Days = 0, // Not available in the DTO
            hasExistingDefaultOrWriteOff = false, // Not available in the DTO
            totalWrittenOffAmount = 0L, // Not available in the DTO
            delinquentStatus = "Standard", // Default value
            createdAt = LocalDateTime.now()
        )
    }
    
    /**
     * Convert domain model to DTO
     */
    fun toDto(bureauReport: BureauReport): BureauReportDto {
        // Convert address to string format
        val addressText = bureauReport.addresses.firstOrNull()?.let { address ->
            listOfNotNull(
                address.addressLine1,
                address.addressLine2,
                address.city,
                address.state,
                address.pincode
            ).joinToString(", ")
        }
        
        return BureauReportDto(
            customerID = bureauReport.id,
            panNumber = bureauReport.panNumber,
            name = bureauReport.customerName ?: "",
            dateOfBirth = bureauReport.dateOfBirth?.toString(),
            gender = bureauReport.gender,
            email = bureauReport.email,
            address = addressText,
            creditScore = bureauReport.creditScore,
            activeLoanAccounts = bureauReport.accountSummary?.totalAccounts ?: 0,
            totalOutstandingAmount = bureauReport.accountSummary?.totalOutstanding?.toDouble() ?: 0.0,
            totalEMIAmount = 0.0, // No direct mapping in new model
            creditCardUtilization = 0.0, // No direct mapping in new model
            paymentHistory = null, // No direct mapping in new model
            lastUpdated = bureauReport.reportDate ?: LocalDate.now().toString()
        )
    }
    
    /**
     * Parse address from string representation
     */
    private fun parseAddressFromString(addressString: String?): List<BureauAddress> {
        if (addressString.isNullOrBlank()) return emptyList()
        
        // Basic parsing logic - this can be enhanced based on actual address format
        val parts = addressString.split(",").map { it.trim() }
        
        // Try to extract pincode (assuming it's a 6-digit number)
        val pincode = parts.lastOrNull { it.matches(Regex("\\d{6}")) } ?: ""
        
        // Try to extract state (after pincode)
        val state = if (pincode.isNotEmpty() && parts.size > 1) {
            parts[parts.indexOf(pincode) - 1]
        } else ""
        
        // Rest is combined as address lines
        val addressLines = if (pincode.isNotEmpty() && state.isNotEmpty()) {
            parts.subList(0, parts.indexOf(state))
        } else {
            parts
        }
        
        return listOf(
            BureauAddress(
                addressLine1 = addressLines.getOrNull(0) ?: "Unknown Address",
                addressLine2 = addressLines.getOrNull(1),
                city = addressLines.getOrNull(addressLines.size - 1) ?: "Unknown City",
                state = state.ifEmpty { "Unknown State" },
                pincode = pincode.ifEmpty { "000000" },
                addressType = "Residential",
                residenceType = null
            )
        )
    }
}