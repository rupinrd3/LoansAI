package com.loansai.unassisted.util.constants

/**
 * Constants related to API URLs and endpoints
 * Updated for v1.5.0 to include Backend LLM API endpoints
 */
object ApiConstants {
    // Single base URL for all API endpoints with trailing slash
    const val BASE_URL = "https://get-application-2vhe2adiwa-uc.a.run.app/"
    const val BASE_URL_DEV = "https://get-application-2vhe2adiwa-uc.a.run.app/"
    
    // Backend LLM API URLs - New for v1.5.0
    const val BACKEND_LLM_API_URL = "https://llm-backend-api-2vhe2adiwa-uc.a.run.app/"
    const val BACKEND_LLM_API_URL_DEV = "https://llm-backend-api-2vhe2adiwa-uc.a.run.app/"
    
    // Authentication Endpoints
    const val ENDPOINT_VERIFY_OTP = "auth/verify-otp"
    const val ENDPOINT_GENERATE_OTP = "auth/generate-otp"
    
    // Application Endpoints - with proper path parameters
    const val ENDPOINT_CREATE_APPLICATION = "create_application"
    const val ENDPOINT_GET_APPLICATION = "{id}"
    const val ENDPOINT_GET_USER_APPLICATIONS = "user/{userId}"
    const val ENDPOINT_UPDATE_APPLICATION = "update_application/{id}"
    const val ENDPOINT_SUBMIT_APPLICATION = "submit_application/{id}"
    
    // PAN Verification Endpoints
    const val ENDPOINT_VERIFY_PAN = "verify_pan"
    const val ENDPOINT_GET_BUREAU_REPORT = "get_bureau_report"
    
    // Employer Search Endpoints
    const val ENDPOINT_SEARCH_EMPLOYERS = "search_employers"
    const val ENDPOINT_GET_EMPLOYER_DETAILS = "get_employer_details/{id}"
    
    // Document Endpoints
    const val ENDPOINT_UPLOAD_DOCUMENT = "upload_document"
    const val ENDPOINT_PROCESS_DOCUMENT = "process_document"
    
    // Backend LLM API Endpoints - New for v1.5.0
    const val ENDPOINT_PROCESS_DOCUMENT_LLM = "processDocument"
    const val ENDPOINT_RECALCULATE_OBLIGATION = "recalculateObligation"
    
    // Loan Offer Endpoints
    const val ENDPOINT_CALCULATE_LOAN_OFFER = "calculate_loan_offer"
    const val ENDPOINT_ADJUST_LOAN_PARAMETERS = "adjust_loan_parameters"
    const val ENDPOINT_ACCEPT_LOAN_OFFER = "accept_loan_offer"
    
    // Timeouts
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // AI Assistant Endpoints
    const val ENDPOINT_AI_ASSISTANT = "ai_assistant"

    // Headers
    const val HEADER_AUTHORIZATION = "Authorization"
    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_ACCEPT = "Accept"

    const val APPWRITE_ENDPOINT = "https://fra.cloud.appwrite.io/v1"
    const val APPWRITE_DATABASE_ID = "67fb572700006c9fc191"
}