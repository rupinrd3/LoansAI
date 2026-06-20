package com.loansai.unassisted.ui.navigation

/**
 * Defines all the screens/routes in the application
 * Organized to support the continuous form experience
 * Updated for v1.5.0 to include Bureau Confirmation
 */
sealed class Screen(val route: String) {
    // Authentication flow
    object Splash : Screen("splash")
    object Login : Screen("login")
    object PrivacyPolicy : Screen("privacy_policy")
    
    // Main application flow - continuous journey
    object Home : Screen("home")
    object PANEntry : Screen("pan_entry")
    object PersonalInfo : Screen("personal_info")
    object EmploymentDetails : Screen("employment_details")
    object DocumentUpload : Screen("document_upload")
    object BureauConfirmation : Screen("bureau_confirmation") // New screen for v1.5.0
    object LoanOffer : Screen("loan_offer")
    object EmploymentVerification : Screen("employment_verification")
    object KeyFactSheet : Screen("key_fact_sheet")
    object ApplicationComplete : Screen("application_complete")
    
    // Settings
    object Settings : Screen("settings")
    
    // Get the next screen in the application flow
    fun getNextScreen(): Screen? {
        return when (this) {
            is Splash -> Login
            is Login -> Home
            is PANEntry -> PersonalInfo
            is PersonalInfo -> EmploymentDetails
            is EmploymentDetails -> DocumentUpload
            is DocumentUpload -> BureauConfirmation // Updated flow includes Bureau Confirmation
            is BureauConfirmation -> LoanOffer
            is LoanOffer -> EmploymentVerification
            is EmploymentVerification -> KeyFactSheet
            is KeyFactSheet -> ApplicationComplete
            else -> null
        }
    }
    
    // Get the previous screen in the application flow
    fun getPreviousScreen(): Screen? {
        return when (this) {
            is Home -> null // Home has no back
            is PANEntry -> Home
            is PersonalInfo -> PANEntry
            is EmploymentDetails -> PersonalInfo
            is DocumentUpload -> EmploymentDetails
            is BureauConfirmation -> DocumentUpload
            is LoanOffer -> if (shouldIncludeBureauConfirmation()) BureauConfirmation else DocumentUpload
            is EmploymentVerification -> LoanOffer
            is KeyFactSheet -> EmploymentVerification
            is ApplicationComplete -> null // Can't go back from completion
            is Settings -> Home
            is PrivacyPolicy -> Login
            else -> null
        }
    }
    
    // Helper method to determine if Bureau Confirmation should be in the flow
    // In a real implementation, this would check the bureau score
    private fun shouldIncludeBureauConfirmation(): Boolean {
        // For demonstration purposes, we'll assume true
        // In a real app, this would check the bureau score range (1-1000)
        return true
    }
    
    // Route with arguments
    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
    
    // Route with query parameters
    fun withQueryParams(vararg params: Pair<String, String>): String {
        return buildString {
            append(route)
            if (params.isNotEmpty()) {
                append("?")
                params.forEachIndexed { index, (key, value) ->
                    append("$key=$value")
                    if (index < params.size - 1) {
                        append("&")
                    }
                }
            }
        }
    }
}