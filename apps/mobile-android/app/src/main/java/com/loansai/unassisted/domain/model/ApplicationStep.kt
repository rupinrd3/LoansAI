package com.loansai.unassisted.domain.model

/**
 * Enum representing different steps in the loan application process
 * Updated for v1.5.0 to include BUREAU_CONFIRMATION step
 */
enum class ApplicationStep {
    LOGIN,
    PRIVACY_POLICY,
    HOME,
    PAN_VERIFICATION,
    PERSONAL_INFO,
    EMPLOYMENT_DETAILS,
    DOCUMENT_UPLOAD,
    BUREAU_CONFIRMATION, 
    LOAN_OFFER,
    EMPLOYMENT_VERIFICATION,
    KEY_FACT_SHEET,
    REVIEW_AND_SUBMIT,
    APPLICATION_SUBMITTED;
    
    override fun toString(): String {
        return name.replace("_", " ")
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }
}