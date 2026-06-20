package com.loansai.unassisted.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.loansai.unassisted.domain.model.BorrowerSummary
import com.loansai.unassisted.domain.model.Enquiry
import com.loansai.unassisted.domain.model.Tradeline
import com.loansai.unassisted.domain.repository.AppwriteRepository
import com.loansai.unassisted.data.local.source.PreferencesDataSource 
import com.loansai.unassisted.service.appwrite.AppwriteService
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import com.google.gson.JsonSyntaxException


/**
 * Implementation of AppwriteRepository
 */
@Singleton
class AppwriteRepositoryImpl @Inject constructor(
    private val appwriteService: AppwriteService,
    private val firestore: FirebaseFirestore,
    private val preferencesDataSource: PreferencesDataSource
) : AppwriteRepository {
    
    override suspend fun getBorrowerSummary(
        panNumber: String
    ): Flow<Resource<BorrowerSummary>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try to get from Appwrite first
            val appwriteResult = appwriteService.getBorrowerSummary(panNumber)
            
            if (appwriteResult is Resource.Success) {
                emit(appwriteResult)
                return@flow
            }
            
            // If Appwrite fails, try to get from Firestore cache
            val firestoreDoc = firestore.collection("bureau_cache")
                .document(panNumber)
                .get()
                .await()
            
            if (firestoreDoc.exists()) {
                val data = firestoreDoc.data
                if (data != null) {
                    // Create BorrowerSummary from Firestore data
                    val borrowerSummary = BorrowerSummary(
                        panNumber = data["panNumber"] as? String ?: panNumber,
                        controlNumber = data["controlNumber"] as? String,
                        customerName = data["customerName"] as? String,
                        creditScore = (data["creditScore"] as? Number)?.toInt(),
                        reportDate = data["reportDate"] as? String,
                        dateOfBirth = data["dateOfBirth"] as? String,
                        gender = data["gender"] as? String,
                        addresses = data["addresses"] as? String,
                        contacts = data["contacts"] as? String,
                        email = data["email"] as? String,
                        totalAccounts = (data["totalAccounts"] as? Number)?.toInt() ?: 0,
                        openAccounts = (data["openAccounts"] as? Number)?.toInt() ?: 0,
                        closedAccounts = (data["closedAccounts"] as? Number)?.toInt() ?: 0,
                        totalLoanAmount = (data["totalLoanAmount"] as? Number)?.toDouble() ?: 0.0,
                        currentBalance = (data["currentBalance"] as? Number)?.toDouble() ?: 0.0,
                        totalOverdueAmount = (data["totalOverdueAmount"] as? Number)?.toDouble() ?: 0.0,
                        suitFiled = data["suitFiled"] as? Boolean ?: false,
                        wilfulDefault = data["wilfulDefault"] as? Boolean ?: false,
                        writtenOffStatus = data["writtenOffStatus"] as? Boolean ?: false,
                        delinquencyStatus = data["delinquencyStatus"] as? String
                    )
                    
                    emit(Resource.Success(borrowerSummary))
                    return@flow
                }
            }
            
            // If we get here, both Appwrite and Firestore failed
            emit(Resource.Error("Failed to get borrower summary from Appwrite and Firestore cache"))
        } catch (e: Exception) {
            val errorMessage = "Error getting borrower summary: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    
    override suspend fun getEnquiries(
        panNumber: String
    ): Flow<Resource<List<Enquiry>>> = flow {
        emit(Resource.Loading())
        
        try {
            // Try to get from Appwrite first
            val appwriteResult = appwriteService.getEnquiries(panNumber)
            
            if (appwriteResult is Resource.Success) {
                emit(appwriteResult)
                return@flow
            }
            
            // If Appwrite fails, try to get from Firestore cache
            val firestoreSnapshot = firestore.collection("bureau_cache")
                .document(panNumber)
                .collection("enquiries")
                .get()
                .await()
            
            if (!firestoreSnapshot.isEmpty) {
                val enquiries = firestoreSnapshot.documents.mapNotNull { doc ->
                    try {
                        Enquiry(
                            id = doc.id,
                            panNumber = doc.getString("panNumber") ?: panNumber,
                            enquiryDate = doc.getString("enquiryDate"),
                            memberName = doc.getString("memberName"),
                            purpose = doc.getString("purpose"),
                            type = doc.getString("type"),
                            amount = doc.getDouble("amount")
                        )
                    } catch (e: Exception) {
                        AppLogger.e("Error parsing enquiry from Firestore: ${e.message}", e)
                        null
                    }
                }
                
                emit(Resource.Success(enquiries))
                return@flow
            }
            
            // If we get here, both Appwrite and Firestore failed
            emit(Resource.Error("Failed to get enquiries from Appwrite and Firestore cache"))
        } catch (e: Exception) {
            val errorMessage = "Error getting enquiries: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    

    
    override suspend fun getTradelines(
        panNumber: String
    ): Flow<Resource<List<Tradeline>>> = flow {
        emit(Resource.Loading())
        AppLogger.d("[AppwriteRepo] Attempting to get Tradelines for PAN: $panNumber") // Log entry

        try {
            // --- Step 1: Try Preferences Cache ---
            AppLogger.d("[AppwriteRepo] Checking preferences cache for tradelines with PAN: $panNumber")
            var cachedTradelines: List<Tradeline>? = null
            try {
                // Attempt to get cached data
                cachedTradelines = preferencesDataSource.getTradelinesForPan(panNumber) // This is a suspend function
            } catch (cacheReadError: Exception) {
                // Log specific errors during cache read attempt
                AppLogger.e("[AppwriteRepo] Error reading tradelines from preferences cache for PAN $panNumber: ${cacheReadError.message}", cacheReadError)
                // Do not emit error yet, proceed to other sources
            }

            if (cachedTradelines != null && cachedTradelines.isNotEmpty()) {
                AppLogger.i("[AppwriteRepo] Found ${cachedTradelines.size} tradelines in preferences cache for PAN: $panNumber. Emitting success.")
                emit(Resource.Success(cachedTradelines))
                return@flow // Successfully returned from cache
            } else {
                AppLogger.d("[AppwriteRepo] No valid tradelines found in preferences cache.")
            }

            // --- Step 2: Try Appwrite Service ---
            AppLogger.d("[AppwriteRepo] No tradelines in preferences cache, trying Appwrite service.")
            val appwriteResult = appwriteService.getTradelines(panNumber) // This returns Resource

            if (appwriteResult is Resource.Success) {
                AppLogger.i("[AppwriteRepo] Successfully fetched ${appwriteResult.data.size} tradelines from Appwrite for PAN: $panNumber")
                // Cache the results in preferences for future use
                try {
                    if (appwriteResult.data.isNotEmpty()) {
                        AppLogger.d("[AppwriteRepo] Caching ${appwriteResult.data.size} tradelines from Appwrite in preferences")
                        preferencesDataSource.saveTradelinesForPan(panNumber, appwriteResult.data)
                    }
                } catch (e: Exception) {
                    AppLogger.e("[AppwriteRepo] Error caching Appwrite tradelines in preferences: ${e.message}", e)
                }
                emit(Resource.Success(appwriteResult.data)) // Emit the success result from Appwrite
                return@flow
            } else if (appwriteResult is Resource.Error) {
                AppLogger.w("[AppwriteRepo] Failed to fetch tradelines from Appwrite: ${appwriteResult.message}. Trying Firestore cache.")
                // Don't emit error yet, try Firestore next
            }


            // --- Step 3: Try Firestore Cache (as last resort) ---
            AppLogger.d("[AppwriteRepo] No tradelines found in Appwrite, trying Firestore cache.")
            try {
                val firestoreSnapshot = firestore.collection("bureau_cache")
                    .document(panNumber)
                    .collection("tradelines")
                    .get()
                    .await()

                if (!firestoreSnapshot.isEmpty) {
                    AppLogger.i("[AppwriteRepo] Found ${firestoreSnapshot.size()} tradelines in Firestore cache for PAN: $panNumber")
                    val tradelines = firestoreSnapshot.documents.mapNotNull { doc ->
                        try {
                            // Your existing mapping logic from Firestore document to Tradeline
                            Tradeline(
                                id = doc.id,
                                panNumber = doc.getString("panNumber") ?: panNumber,
                                memberName = doc.getString("memberName") ?: "Unknown Lender",
                                accountType = doc.getString("accountType") ?: "Unknown",
                                accountNumber = doc.getString("accountNumber") ?: "XXXXXXXX",
                                ownership = doc.getString("ownership"),
                                creditLimit = doc.getDouble("creditLimit"),
                                highCredit = doc.getDouble("highCredit"),
                                currentBalance = doc.getDouble("currentBalance"),
                                amountOverdue = doc.getDouble("amountOverdue"),
                                rateOfInterest = doc.getDouble("rateOfInterest"),
                                repaymentTenure = doc.getLong("repaymentTenure")?.toInt(),
                                emiAmount = doc.getDouble("emiAmount"),
                                dateOpened = doc.getString("dateOpened"),
                                dateClosed = doc.getString("dateClosed"),
                                lastPaymentDate = doc.getString("lastPaymentDate"),
                                dateReported = doc.getString("dateReported"),
                                facilityStatus = doc.getString("facilityStatus"),
                                suitFiled = doc.getString("suitFiled"),
                                paymentFrequency = doc.getString("paymentFrequency"),
                                paymentHistory = doc.getString("paymentHistory"),
                                writtenOffTotal = doc.getDouble("writtenOffTotal"),
                                writtenOffPrincipal = doc.getDouble("writtenOffPrincipal"),
                                controlNumber = doc.getString("controlNumber")
                            )
                        } catch (e: Exception) {
                            AppLogger.e("[AppwriteRepo] Error parsing tradeline from Firestore cache: ${e.message}", e)
                            null // Skip this problematic document
                        }
                    }

                    // Also cache these Firestore results back to preferences
                    try {
                        if (tradelines.isNotEmpty()) {
                            AppLogger.d("[AppwriteRepo] Caching ${tradelines.size} tradelines from Firestore in preferences")
                            preferencesDataSource.saveTradelinesForPan(panNumber, tradelines)
                        }
                    } catch (e: Exception) {
                        AppLogger.e("[AppwriteRepo] Error caching Firestore tradelines in preferences: ${e.message}", e)
                    }

                    emit(Resource.Success(tradelines))
                    return@flow
                } else {
                    AppLogger.w("[AppwriteRepo] No tradelines found in Firestore cache for PAN: $panNumber")
                }
            } catch(e: Exception) {
                AppLogger.e("[AppwriteRepo] Error fetching from Firestore cache for PAN $panNumber: ${e.message}", e)
                // Don't emit error yet, let the final error emit
            }

            // --- Step 4: All sources failed ---
            val finalErrorMessage = "Failed to get tradelines from all available sources (Preferences, Appwrite, Firestore) for PAN: $panNumber"
            AppLogger.e("[AppwriteRepo] $finalErrorMessage")
            emit(Resource.Error(finalErrorMessage))

        } catch (e: Exception) { // Catch any unexpected errors during the flow
            val errorMessage = "[AppwriteRepo] Unhandled exception in getTradelines for PAN $panNumber: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }


    override suspend fun getActiveTradelines(
        panNumber: String
    ): Flow<Resource<List<Tradeline>>> = flow {
        emit(Resource.Loading())
        
        try {
            // Get all tradelines first
            val allTradelinesFlow = getTradelines(panNumber)
            
            allTradelinesFlow.collect { result ->
                if (result is Resource.Success) {
                    // Filter only active tradelines
                    val activeTradelines = result.data.filter { tradeline ->
                        tradeline.facilityStatus?.equals("Active", ignoreCase = true) == true ||
                        tradeline.dateClosed == null
                    }
                    
                    emit(Resource.Success(activeTradelines))
                } else if (result is Resource.Error) {
                    emit(result)
                } else {
                    // Just pass through the loading state
                    emit(result)
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting active tradelines: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }



    // After: (With added logging)

    override suspend fun fetchAllBureauData(
        panNumber: String
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        AppLogger.d("[AppwriteRepo] Starting fetchAllBureauData for PAN: $panNumber") // <-- ADD Log Start

        try {
            var success = false
            var finalErrorMessage = "Failed to fetch any bureau data. Errors: "
            val errors = mutableListOf<String>()

            // --- Fetch Summary ---
            AppLogger.d("[AppwriteRepo] Calling appwriteService.fetchAndCacheBorrowerSummary for PAN: $panNumber") // <-- ADD Log before Summary call
            val summaryResult = appwriteService.fetchAndCacheBorrowerSummary(panNumber)
            AppLogger.d("[AppwriteRepo] Result for fetchAndCacheBorrowerSummary: ${summaryResult::class.simpleName}") // <-- ADD Log Summary result type
            if (summaryResult is Resource.Success) {
                success = true
                AppLogger.i("[AppwriteRepo] Successfully fetched/cached BorrowerSummary for PAN: $panNumber") // <-- ADD Log Summary success
            } else if (summaryResult is Resource.Error) {
                AppLogger.w("[AppwriteRepo] Failed fetchAndCacheBorrowerSummary for PAN: $panNumber. Error: ${summaryResult.message}") // <-- ADD Log Summary error
                errors.add("Summary: ${summaryResult.message}")
            }

            // --- Fetch Enquiries ---
            AppLogger.d("[AppwriteRepo] Calling appwriteService.fetchAndCacheEnquiries for PAN: $panNumber") // <-- ADD Log before Enquiries call
            val enquiriesResult = appwriteService.fetchAndCacheEnquiries(panNumber)
            AppLogger.d("[AppwriteRepo] Result for fetchAndCacheEnquiries: ${enquiriesResult::class.simpleName}") // <-- ADD Log Enquiries result type
            if (enquiriesResult is Resource.Success) {
                success = true
                AppLogger.i("[AppwriteRepo] Successfully fetched/cached Enquiries for PAN: $panNumber") // <-- ADD Log Enquiries success
            } else if (enquiriesResult is Resource.Error) {
                AppLogger.w("[AppwriteRepo] Failed fetchAndCacheEnquiries for PAN: $panNumber. Error: ${enquiriesResult.message}") // <-- ADD Log Enquiries error
                errors.add("Enquiries: ${enquiriesResult.message}")
            }

            // --- Fetch Tradelines ---
            AppLogger.d("[AppwriteRepo] Calling appwriteService.fetchAndCacheTradelines for PAN: $panNumber") // <-- ADD Log before Tradelines call
            val tradelinesResult = appwriteService.fetchAndCacheTradelines(panNumber)
            AppLogger.d("[AppwriteRepo] Result for fetchAndCacheTradelines: ${tradelinesResult::class.simpleName}") // <-- ADD Log Tradelines result type
            if (tradelinesResult is Resource.Success) {
                success = true
                AppLogger.i("[AppwriteRepo] Successfully fetched/cached Tradelines for PAN: $panNumber") // <-- ADD Log Tradelines success
            } else if (tradelinesResult is Resource.Error) {
                AppLogger.w("[AppwriteRepo] Failed fetchAndCacheTradelines for PAN: $panNumber. Error: ${tradelinesResult.message}") // <-- ADD Log Tradelines error
                errors.add("Tradelines: ${tradelinesResult.message}")
            }

            // --- Emit Final Result ---
            if (success) {
                AppLogger.i("[AppwriteRepo] fetchAllBureauData completed with partial or full success for PAN: $panNumber") // <-- ADD Log overall success
                emit(Resource.Success(true))
            } else {
                finalErrorMessage += errors.joinToString("; ")
                AppLogger.e("[AppwriteRepo] fetchAllBureauData failed completely for PAN: $panNumber. Details: $finalErrorMessage") // <-- ADD Log overall failure
                emit(Resource.Error(finalErrorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Exception during fetchAllBureauData for PAN $panNumber: ${e.message}" // <-- Update error message
            AppLogger.e("[AppwriteRepo] $errorMessage", e) // Log exception with context
            emit(Resource.Error(errorMessage, e)) // Pass exception too
        }
    }

}
