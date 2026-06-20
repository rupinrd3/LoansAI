package com.loansai.unassisted.data.repository

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.domain.model.User
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.constants.PreferenceConstants
import com.loansai.unassisted.util.context.ApplicationContextProvider
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore

/**
 * Implementation of UserRepository using Firebase Authentication
 */
@Singleton
class UserRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore, 
    private val preferencesDataSource: PreferencesDataSource
) : UserRepository {

    // Use regular SharedPreferences for callbacks to avoid suspend functions
    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PreferenceConstants.DATA_STORE_NAME, Context.MODE_PRIVATE)
    }
    
    // Helper function to save string synchronously in callbacks
    private fun saveStringSync(key: String, value: String) {
        sharedPrefs.edit().putString(key, value).apply()
    }

    /**
     * Send OTP to the provided phone number
     */
    override suspend fun sendOTP(phoneNumber: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Format phone number for international format
            val formattedPhone = if (!phoneNumber.startsWith("+")) "+91$phoneNumber" else phoneNumber
            
            val activity = ApplicationContextProvider.getCurrentActivity()
            if (activity == null) {
                emit(Resource.Error("Activity not available for phone authentication"))
                return@flow
            }
            
            // THIS WAS MISSING: Original OTP sending logic
            val verificationId = sendOtpInternal(formattedPhone, activity)
            
            // Once we have the verification ID from the callback, save it using the suspending function
            withContext(Dispatchers.IO) {
                preferencesDataSource.saveString(PreferenceConstants.PREF_VERIFICATION_ID, verificationId)
                
                // New part: Save to application-based OTP collection
                val applicationId = preferencesDataSource.getCurrentApplicationId()
                
                if (!applicationId.isNullOrEmpty()) {
                    try {
                        // Generate a 6-digit OTP for storage (NOT the actual SMS OTP)
                        val storedOtp = (100000..999999).random().toString()
                        
                        // Create or update the OTP document with phone verification
                        val otpRef = firestore.collection("otps").document(applicationId)
                        val otpDoc = otpRef.get().await()
                        
                        if (otpDoc.exists()) {
                            // Update existing document
                            otpRef.update(
                                "phoneVerification", mapOf(
                                    "phoneNumber" to formattedPhone,
                                    "otp" to storedOtp,
                                    "isVerified" to false,
                                    "createdAt" to com.google.firebase.Timestamp.now(),
                                    "expiresAt" to com.google.firebase.Timestamp(
                                        java.util.Date(System.currentTimeMillis() + 15 * 60 * 1000)
                                    )
                                )
                            ).await()
                        } else {
                            // Create new document
                            otpRef.set(
                                mapOf(
                                    "applicationId" to applicationId,
                                    "phoneVerification" to mapOf(
                                        "phoneNumber" to formattedPhone,
                                        "otp" to storedOtp,
                                        "isVerified" to false,
                                        "createdAt" to com.google.firebase.Timestamp.now(),
                                        "expiresAt" to com.google.firebase.Timestamp(
                                            java.util.Date(System.currentTimeMillis() + 15 * 60 * 1000)
                                        )
                                    )
                                )
                            ).await()
                        }
                        
                        AppLogger.d("Stored phone verification record for application: $applicationId")
                    } catch (e: Exception) {
                        AppLogger.e("Error storing phone OTP record in Firestore: ${e.message}", e)
                        // Continue anyway - the real SMS OTP is still being sent
                    }
                }
            }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error sending OTP", e)
            emit(Resource.Error("Error sending OTP: ${e.message}"))
        }
    }



    private suspend fun sendOtpInternal(phoneNumber: String, activity: Activity): String {
        return suspendCoroutine { continuation ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    AppLogger.d("Phone auth completed automatically")
                    
                    // Use synchronous version for callbacks
                    val smsCode = credential.smsCode ?: ""
                    saveStringSync(PreferenceConstants.PREF_AUTO_VERIFIED_CREDENTIAL, smsCode)
                    
                    // Even though verification is completed, we still need a verification ID
                    // We'll use a dummy one since this case is rare
                    continuation.resume("auto-verified")
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    AppLogger.e("Phone auth failed", e)
                    continuation.resumeWithException(e)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    AppLogger.d("OTP code sent successfully")
                    
                    // Use synchronous version for callbacks
                    saveStringSync(PreferenceConstants.PREF_VERIFICATION_ID, verificationId)
                    
                    continuation.resume(verificationId)
                }
            }
            
            val options = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            
            PhoneAuthProvider.verifyPhoneNumber(options)
        }
    }

    /**
     * Verify OTP and authenticate user
     */
    override suspend fun verifyOTP(phoneNumber: String, otp: String): Flow<Resource<User>> = flow {
        emit(Resource.Loading())
        
        try {
            // Original verification code
            val verificationId = withContext(Dispatchers.IO) {
                preferencesDataSource.getString(PreferenceConstants.PREF_VERIFICATION_ID)
            }
            
            if (verificationId == null) {
                emit(Resource.Error("Verification ID not found"))
                return@flow
            }
            
            // Create credential for sign-in
            val credential = PhoneAuthProvider.getCredential(verificationId, otp)
            
            // Sign in with credential
            val authResult = try {
                withContext(Dispatchers.IO) {
                    firebaseAuth.signInWithCredential(credential).await()
                }
            } catch (e: Exception) {
                emit(Resource.Error("Invalid OTP: ${e.message}"))
                return@flow
            }
            
            // Get Firebase user
            val firebaseUser = authResult.user
            if (firebaseUser == null) {
                emit(Resource.Error("Authentication failed"))
                return@flow
            }
            
            // Get token without await to avoid potential issues
            val token = withContext(Dispatchers.IO) {
                try {
                    val tokenResult = firebaseAuth.currentUser?.getIdToken(false)?.await()
                    tokenResult?.token ?: ""
                } catch (e: Exception) {
                    // If there's an error getting the token, use an empty string
                    ""
                }
            }
            
            // Create user model
            val user = User(
                id = firebaseUser.uid,
                phoneNumber = phoneNumber,
                token = token,
                isPrivacyPolicyAccepted = false,
                currentApplicationId = null
            )
            
            // Save user data to preferences
            withContext(Dispatchers.IO) {
                preferencesDataSource.saveString(PreferenceConstants.PREF_AUTH_TOKEN, user.token)
                preferencesDataSource.saveString(PreferenceConstants.PREF_USER_ID, user.id)
                preferencesDataSource.saveString(PreferenceConstants.PREF_USER_PHONE, user.phoneNumber)
                
                // Add code to save phone number to current application in Firestore
                try {
                    // Get the current application ID
                    val applicationId = preferencesDataSource.getCurrentApplicationId()
                    
                    if (applicationId != null) {
                        // Save the phone number to the application in Firestore
                        // Make sure we're using the actual phone number, not Firebase UID
                        // Format phone number for better readability (remove the part we add for Firebase)
                        val formattedPhone = phoneNumber.replace("+91", "")
                        AppLogger.d("Saving formatted phone number to Firestore: $formattedPhone")

                        // Save the phone number to the application in Firestore
                        val firestore = FirebaseFirestore.getInstance()
                        val docRef = firestore.collection("applications").document(applicationId)

                        // Use update or set based on whether document exists
                        val docSnapshot = docRef.get().await()
                        if (docSnapshot.exists()) {
                            docRef.update(
                                "mobileNumber", formattedPhone,
                                "lastUpdatedAt", FieldValue.serverTimestamp()
                            ).await()
                        } else {
                            val data = mapOf(
                                "mobileNumber" to formattedPhone,
                                "lastUpdatedAt" to FieldValue.serverTimestamp()
                            )
                            docRef.set(data, SetOptions.merge()).await()
                        }

                        // For debugging: log the current document state
                        try {
                            val updatedDoc = docRef.get().await()
                            val currentMobileNumber = updatedDoc.getString("mobileNumber")
                            AppLogger.d("After update, document mobileNumber = $currentMobileNumber")
                        } catch (e: Exception) {
                            AppLogger.e("Error checking updated document: ${e.message}")
                        }
                        
                        AppLogger.d("Saved phone number to application Firestore document")
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error saving phone number to Firestore: ${e.message}", e)
                    // Continue with user authentication even if this fails
                }
                
                // New part: Update OTP verification status in Firestore
                val applicationId = preferencesDataSource.getCurrentApplicationId()
                
                if (!applicationId.isNullOrEmpty()) {
                    try {
                        // Update phone verification status
                        val otpRef = firestore.collection("otps").document(applicationId)
                        val otpDoc = otpRef.get().await()
                        
                        if (otpDoc.exists()) {
                            // Check if phoneVerification exists in the document
                            val phoneVerification = otpDoc.get("phoneVerification") as? Map<*, *>
                            
                            if (phoneVerification != null) {
                                // Update verification status
                                otpRef.update(
                                    "phoneVerification.isVerified", true,
                                    "phoneVerification.verifiedAt", com.google.firebase.Timestamp.now()
                                ).await()
                                
                                AppLogger.d("Updated phone verification status for application: $applicationId")
                            } else {
                                // Create phoneVerification if it doesn't exist
                                otpRef.update(
                                    "phoneVerification", mapOf(
                                        "phoneNumber" to phoneNumber,
                                        "isVerified" to true,
                                        "verifiedAt" to com.google.firebase.Timestamp.now()
                                    )
                                ).await()
                            }
                        } else {
                            // Create new document with verification status
                            otpRef.set(
                                mapOf(
                                    "applicationId" to applicationId,
                                    "phoneVerification" to mapOf(
                                        "phoneNumber" to phoneNumber,
                                        "isVerified" to true,
                                        "verifiedAt" to com.google.firebase.Timestamp.now()
                                    )
                                )
                            ).await()
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error updating phone verification status: ${e.message}", e)
                        // Continue anyway - the user is still authenticated
                    }
                }
            }
            
            emit(Resource.Success(user))
            
        } catch (e: Exception) {
            AppLogger.e("Error verifying OTP", e)
            emit(Resource.Error("Error verifying OTP: ${e.message}"))
        }
    }



    // Add method to save email OTP (for employment verification)
    /**
    * Updated method to save email OTP with proper application ID link
    */
// Make sure these methods are directly inside the UserRepositoryImpl class
// and not inside any other method or block


    /**
    * Save email OTP for verification
    */
    override suspend fun saveEmailOTP(applicationId: String, email: String, otp: String): Boolean {
        return try {
            AppLogger.d("UserRepositoryImpl: Saving email OTP for app $applicationId, email $email")
            
            // Create timestamps once for consistency
            val currentTime = com.google.firebase.Timestamp.now()
            val expirationTime = com.google.firebase.Timestamp(
                java.util.Date(System.currentTimeMillis() + 15 * 60 * 1000)
            )
            
            // Create the nested emailVerification map
            val emailVerificationMap = hashMapOf(
                "email" to email,
                "otp" to otp,
                "isVerified" to false,
                "createdAt" to currentTime,
                "expiresAt" to expirationTime
            )
            
            // Create or update the OTP document with email verification
            val otpRef = firestore.collection("otps").document(applicationId)
            val otpDoc = otpRef.get().await()
            
            if (otpDoc.exists()) {
                // IMPORTANT: For update, use a map instead of multiple arguments
                val updateMap = hashMapOf<String, Any>(
                    "emailVerification" to emailVerificationMap,
                    "email" to email,
                    "otp" to otp,
                    "isVerified" to false,
                    "createdAt" to currentTime,
                    "expiresAt" to expirationTime
                )
                
                // Use a single update call with the map
                otpRef.update(updateMap).await()
                AppLogger.d("UserRepositoryImpl: Updated existing OTP document for app $applicationId")
            } else {
                // For new document, include applicationId
                val newDocData = hashMapOf<String, Any>(
                    "applicationId" to applicationId,
                    "emailVerification" to emailVerificationMap,
                    "email" to email,
                    "otp" to otp,
                    "isVerified" to false,
                    "createdAt" to currentTime,
                    "expiresAt" to expirationTime
                )
                
                // Set the new document
                otpRef.set(newDocData).await()
                AppLogger.d("UserRepositoryImpl: Created new OTP document for app $applicationId")
            }
            
            AppLogger.d("UserRepositoryImpl: Successfully saved email OTP for app $applicationId")
            true
        } catch (e: Exception) {
            AppLogger.e("UserRepositoryImpl: Error storing email OTP in Firestore: ${e.message}", e)
            e.printStackTrace() // Print stack trace for debugging
            false
        }
    }

    /**
    * Verify email OTP
    */
    override suspend fun verifyEmailOTP(applicationId: String, email: String, otp: String): Boolean {
        return try {
            AppLogger.d("UserRepositoryImpl: Verifying email OTP for app $applicationId, email $email")
            
            val otpRef = firestore.collection("otps").document(applicationId)
            val otpDoc = otpRef.get().await()
            
            if (otpDoc.exists()) {
                // First try with flat structure
                val storedOtp = otpDoc.getString("otp")
                val storedEmail = otpDoc.getString("email")
                val expiresAt = otpDoc.getTimestamp("expiresAt")
                
                // Check if expired
                val isExpired = expiresAt?.toDate()?.before(java.util.Date()) ?: true
                
                AppLogger.d("UserRepositoryImpl: Verification check - stored OTP: $storedOtp, provided OTP: $otp, expired: $isExpired")
                
                // Try verification with flat structure first
                val verifiedWithFlat = storedOtp == otp && storedEmail == email && !isExpired
                
                // If flat structure fails, try with nested structure
                val verified = if (verifiedWithFlat) {
                    true
                } else {
                    // Try nested structure as fallback
                    val emailVerificationMap = otpDoc.get("emailVerification") as? Map<*, *>
                    
                    if (emailVerificationMap != null) {
                        val nestedOtp = emailVerificationMap["otp"] as? String
                        val nestedEmail = emailVerificationMap["email"] as? String
                        val nestedExpiresAt = emailVerificationMap["expiresAt"] as? com.google.firebase.Timestamp
                        val nestedExpired = nestedExpiresAt?.toDate()?.before(java.util.Date()) ?: true
                        
                        AppLogger.d("UserRepositoryImpl: Nested verification check - stored OTP: $nestedOtp, provided OTP: $otp, expired: $nestedExpired")
                        
                        nestedOtp == otp && nestedEmail == email && !nestedExpired
                    } else {
                        false
                    }
                }
                
                if (verified) {
                    // Current timestamp for verification time
                    val verifiedAt = com.google.firebase.Timestamp.now()
                    
                    // Update OTP document with verification status
                    // IMPORTANT: Use a map for update to avoid errors
                    val updateMap = hashMapOf<String, Any>(
                        "isVerified" to true,
                        "verifiedAt" to verifiedAt
                    )
                    
                    // Add nested updates if present
                    updateMap["emailVerification.isVerified"] = true
                    updateMap["emailVerification.verifiedAt"] = verifiedAt
                    
                    // Update OTP document
                    otpRef.update(updateMap).await()
                    
                    // Also update employment_verifications collection
                    val verificationData = hashMapOf(
                        "applicationId" to applicationId,
                        "verificationMethod" to "EMAIL",
                        "isVerified" to true,
                        "verifiedAt" to verifiedAt,
                        "verifiedEmail" to email
                    )
                    
                    // Save to employment_verifications collection
                    firestore.collection("employment_verifications")
                        .document(applicationId)
                        .set(verificationData)
                        .await()
                    
                    AppLogger.d("UserRepositoryImpl: Email OTP verified successfully for app $applicationId")
                    true
                } else {
                    AppLogger.d("UserRepositoryImpl: Invalid OTP for app $applicationId")
                    false
                }
            } else {
                AppLogger.d("UserRepositoryImpl: No OTP record found for app $applicationId")
                false
            }
        } catch (e: Exception) {
            AppLogger.e("UserRepositoryImpl: Error verifying email OTP: ${e.message}", e)
            e.printStackTrace() // Print stack trace for debugging
            false
        }
    }

    override fun getCurrentUser(): Flow<Resource<User>> = flow {
        emit(Resource.Loading())
        AppLogger.d("[UserRepository] Attempting to get current user...")

        var firebaseUser: com.google.firebase.auth.FirebaseUser? = null // Declare outside try
        try {
            // 1. Check Firebase Auth State
            firebaseUser = FirebaseAuth.getInstance().currentUser // Assign here
            AppLogger.d("[UserRepository] Firebase Auth currentUser: ${firebaseUser?.uid ?: "null"}")

            if (firebaseUser != null) {
                // User is authenticated with Firebase Auth
                AppLogger.d("[UserRepository] Firebase user found. Fetching details from preferences...")

                // 2. Fetch details from PreferencesDataSource with specific try-catch blocks
                var currentAppId: String? = null // Default to null
                try {
                    // *** ADDED TRY-CATCH HERE ***
                    currentAppId = preferencesDataSource.getCurrentApplicationId() // Suspending call
                    AppLogger.d("[UserRepository] Retrieved currentApplicationId from Preferences: $currentAppId")
                } catch (prefsError: Exception) {
                    // Log specific error for this preference read
                    AppLogger.e("[UserRepository] Error fetching currentApplicationId from preferences: ${prefsError.message}", prefsError)
                    // Emit a specific error OR allow proceeding with null appId
                    // Option A: Emit specific error and stop
                     emit(Resource.Error("Error reading application preference: ${prefsError.message}"))
                     return@flow // Stop processing if preference read fails critically
                    // Option B: Log and continue with null (less safe if appId is critical)
                    // currentAppId = null // Ensure it's null on error
                }

                var isPrivacyAccepted: Boolean = false // Default to false
                try {
                    // *** ADDED TRY-CATCH HERE ***
                    // Example: Assume privacy is also read (adjust key if necessary)
                    isPrivacyAccepted = preferencesDataSource.getBoolean(PreferenceConstants.PREF_PRIVACY_ACCEPTED, false)
                    AppLogger.d("[UserRepository] Retrieved isPrivacyAccepted from Preferences: $isPrivacyAccepted")
                } catch (prefsError: Exception) {
                    // Log specific error for this preference read
                    AppLogger.e("[UserRepository] Error fetching privacy status from preferences: ${prefsError.message}", prefsError)
                     // Emit a specific error OR allow proceeding with default value
                     // Option A: Emit specific error and stop
                      emit(Resource.Error("Error reading privacy preference: ${prefsError.message}"))
                      return@flow // Stop processing if preference read fails critically
                     // Option B: Log and continue with default
                     // isPrivacyAccepted = false // Ensure it's default on error
                }

                // 3. Construct User object
                val user = User(
                    id = firebaseUser.uid,
                    phoneNumber = firebaseUser.phoneNumber ?: "",
                    token = "", // Token fetched separately
                    isPrivacyPolicyAccepted = isPrivacyAccepted, // Use value from preferences
                    currentApplicationId = currentAppId // Use value from preferences
                )

                AppLogger.i("[UserRepository] Successfully constructed and emitting current user: ${user.id}")
                emit(Resource.Success(user)) // Emit Success

            } else {
                // User is not authenticated with Firebase Auth
                AppLogger.w("[UserRepository] Firebase currentUser is null. Emitting 'User not authenticated' error.")
                emit(Resource.Error("User not authenticated")) // Emit Specific Error
            }
        // *** MODIFIED OUTER CATCHES ***
        } catch (authError: com.google.firebase.FirebaseException) { // Catch specific Firebase errors
             AppLogger.e("[UserRepository] Firebase Auth exception in getCurrentUser: ${authError.message}", authError)
             emit(Resource.Error("Authentication error: ${authError.message ?: "Firebase error"}"))
        } catch (e: Exception) {
             // Catch any other unexpected exceptions
             AppLogger.e("[UserRepository] Unexpected exception in getCurrentUser: ${e.message}", e)
             emit(Resource.Error("Unexpected error getting user: ${e.message ?: "Unknown error"}"))
        }
    }


    /**
     * Update the privacy policy acceptance status
     */
    override suspend fun updatePrivacyPolicyAcceptance(accepted: Boolean): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            val userId = withContext(Dispatchers.IO) {
                preferencesDataSource.getString(PreferenceConstants.PREF_USER_ID)
            }
            
            if (userId == null) {
                emit(Resource.Error("User not authenticated"))
                return@flow
            }
            
            // Update local preference
            withContext(Dispatchers.IO) {
                preferencesDataSource.saveBoolean(PreferenceConstants.PREF_PRIVACY_ACCEPTED, accepted)
            }
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error updating privacy policy acceptance", e)
            emit(Resource.Error("Error updating privacy policy acceptance: ${e.message}"))
        }
    }

    /**
     * Update the current application ID for the user
     */
    override suspend fun updateCurrentApplicationId(applicationId: String?): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            withContext(Dispatchers.IO) {
                if (applicationId != null) {
                    preferencesDataSource.saveString(
                        PreferenceConstants.PREF_CURRENT_APPLICATION_ID, 
                        applicationId
                    )
                } else {
                    preferencesDataSource.remove(PreferenceConstants.PREF_CURRENT_APPLICATION_ID)
                }
            }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error updating current application ID", e)
            emit(Resource.Error("Error updating current application ID: ${e.message}"))
        }
    }

    /**
     * Sign out the current user
     */
    override suspend fun signOut(): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Sign out from Firebase
            firebaseAuth.signOut()
            
            // Clear preferences
            withContext(Dispatchers.IO) {
                preferencesDataSource.remove(PreferenceConstants.PREF_AUTH_TOKEN)
                preferencesDataSource.remove(PreferenceConstants.PREF_USER_ID)
                preferencesDataSource.remove(PreferenceConstants.PREF_USER_PHONE)
                preferencesDataSource.remove(PreferenceConstants.PREF_PRIVACY_ACCEPTED)
                preferencesDataSource.remove(PreferenceConstants.PREF_CURRENT_APPLICATION_ID)
                preferencesDataSource.remove(PreferenceConstants.PREF_VERIFICATION_ID)
                preferencesDataSource.remove(PreferenceConstants.PREF_AUTO_VERIFIED_CREDENTIAL)
            }
            
            emit(Resource.Success(true))
        } catch (e: Exception) {
            AppLogger.e("Error signing out", e)
            emit(Resource.Error("Error signing out: ${e.message}"))
        }
    }

    suspend fun sendEmailVerification(email: String): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get current Firebase user
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            if (currentUser != null) {
                // Send verification email
                currentUser.sendEmailVerification()
                    .addOnSuccessListener {
                        // Verification email sent successfully
                        AppLogger.d("Verification email sent to ${currentUser.email}")
                    }
                    .addOnFailureListener { e ->
                        // Failed to send verification email
                        AppLogger.e("Failed to send verification email: ${e.message}", e)
                    }
                
                emit(Resource.Success(true))
            } else {
                emit(Resource.Error("No authenticated user found"))
            }
        } catch (e: Exception) {
            AppLogger.e("Error sending verification email: ${e.message}", e)
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }



    /**
     * Check if the user is authenticated
     */
    override fun isUserAuthenticated(): Flow<Boolean> {
        return preferencesDataSource.getStringFlow(PreferenceConstants.PREF_AUTH_TOKEN)
            .map { token -> 
                if (token != null && token.isNotEmpty()) {
                    // Check if the token is still valid
                    firebaseAuth.currentUser != null
                } else {
                    false
                }
            }
    }

    // Replace viewModelScope with a proper scope
    fun requireFreshAuthentication() {
        firebaseAuth.signOut()
        // Use a simple non-suspending approach to avoid coroutine issues
        // preferencesDataSource.sharedPrefs.edit()
        //     .remove(PreferenceConstants.PREF_AUTH_TOKEN)
        //     .remove(PreferenceConstants.PREF_USER_ID)
        //     .remove(PreferenceConstants.PREF_USER_PHONE)
        //     .apply()
    }
}