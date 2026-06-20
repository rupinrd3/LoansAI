package com.loansai.unassisted.domain.model

import java.time.LocalDateTime

/**
 * Domain model for BRE Input
 * Updated for v1.5.0 to include LLM-recalculated obligation
 */
data class BREInput(
    val id: String,
    val applicationId: String,
    val timestamp: LocalDateTime,
    val userId: String,
    val version: String,
    val panDetails: PANDetails? = null,
    val personalInfo: PersonalInfo? = null,
    val employmentDetails: EmploymentDetails? = null,
    val bureauData: BureauData? = null,
    val documentExtractions: DocumentExtractions? = null,
    val llmRecalculatedObligation: Int? = null, // LLM-recalculated monthly obligation from Bureau Confirmation screen
    val additionalData: Map<String, Any> = emptyMap()
)

/**
 * Domain model for BRE Output
 */
data class BREOutput(
    // Existing fields
    val id: String,
    val applicationId: String,
    val timestamp: LocalDateTime,
    val rulesVersion: String,
    val decisionStatus: DecisionStatus,
    
    // New fields for v2.0
    val verifiedIncome: Double? = null,
    val incomeVerificationConfidence: Int? = null,
    val incomeVerificationSource: String? = null,
    val approvedLoanAmount: Double? = null,
    val minLoanAmount: Double? = null,
    val maxLoanAmount: Double? = null,
    val interestRate: Double? = null,
    val minTenure: Int? = null,
    val maxTenure: Int? = null,
    val processingFeePercentage: Double? = null,
    val rejectionReasons: List<String> = emptyList(),
    val referralReasons: List<String> = emptyList(),
    val profileFlag: Boolean = true
)

/**
 * Domain model for Bureau Data
 */
data class BureauData(
    val panNumber: String,
    val fetchedAt: LocalDateTime,
    val bureauType: String,
    val reportDate: LocalDateTime,
    val customerName: String,
    val creditScore: Int,
    val totalAccounts: Int,
    val openAccounts: Int,
    val totalLoanAmount: Double,
    val currentBalance: Double,
    val totalOverdueAmount: Double,
    val delinquencyStatus: String? = null,
    val suitFiled: Boolean = false,
    val wilfulDefault: Boolean = false,
    val writtenOffStatus: Boolean = false
)

/**
 * Domain model for Document Extractions
 */
data class DocumentExtractions(
    val bankStatement: BankStatementData? = null,
    val salarySlip: SalarySlipData? = null,
    val incomeTaxReturn: IncomeTaxReturnData? = null
)



/**
 * Domain model for Salary Slip Data
 */
data class SalarySlipData(
    val employerName: String? = null,
    val employeeName: String? = null,
    val employeeId: String? = null,
    val salaryMonth: String? = null,
    val salaryYear: Int? = null,
    val basicSalary: Double? = null,
    val grossSalary: Double? = null,
    val netSalary: Double? = null
)

/**
 * Domain model for Income Tax Return Data
 */
data class IncomeTaxReturnData(
    val itrType: String? = null,
    val assessmentYear: String? = null,
    val panNumber: String? = null,
    val name: String? = null,
    val totalGrossIncome: Double? = null,
    val taxableIncome: Double? = null,
    val taxPaid: Double? = null
)


/**
 * Domain model for Bank Statement Data
 */
// data class BankStatementData(
//     val bankName: String? = null,
//     val accountNumber: String? = null,
//     val accountHolderName: String? = null,
//     val statementPeriodStart: LocalDateTime? = null,
//     val statementPeriodEnd: LocalDateTime? = null,
//     val openingBalance: Double? = null,
//     val closingBalance: Double? = null,
//     val averageBalance: Double? = null,
//     val totalCredits: Double? = null,
//     val totalDebits: Double? = null,
//     val transactionsCount: Int? = null
// )

