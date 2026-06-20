package com.loansai.unassisted.util.constants

/**
 * Constants for preferences and DataStore
 */
object PreferenceConstants {
    // DataStore name
    // const val DATA_STORE_NAME = "loansai_prefs"
    const val DATA_STORE_NAME = "loansai_preferences"
    
    // Authentication
    const val PREF_AUTH_TOKEN = "auth_token"
    const val PREF_USER_ID = "user_id"
    const val PREF_USER_PHONE = "user_phone"
    const val PREF_VERIFICATION_ID = "verification_id"
    const val PREF_RESENDING_TOKEN = "resending_token"
    const val PREF_AUTO_VERIFIED_CREDENTIAL = "auto_verified_credential"
    
    // Settings
    const val PREF_PRIVACY_ACCEPTED = "privacy_accepted"
    const val PREF_OCR_SERVICE_TYPE = "ocr_service_type"
    const val OCR_SERVICE_ML_KIT = "ml_kit"
    
    // Application data
    const val PREF_CURRENT_APPLICATION_ID = "current_application_id"
    const val PREF_APPLICATION_CACHE = "application_cache"
    
    // Feature flags
    const val PREF_ENABLE_VOICE_INPUT = "enable_voice_input"
    const val PREF_ENABLE_AI_ASSISTANT = "enable_ai_assistant"
    
    // Cache expiry (24 hours in milliseconds)
    const val CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000L

    const val PREF_AI_MESSAGES = "pref_ai_messages"
    const val PREF_DOCUMENTS = "pref_documents"
    const val PREF_PAN_DETAILS = "pref_pan_details"
    const val PREF_BUREAU_REPORT = "pref_bureau_report"   
   
    
    // Settings
    const val PREF_SELECTED_OCR_SERVICE = "selected_ocr_service"
    const val PREF_AI_ASSISTANT_ENABLED = "ai_assistant_enabled"
}