package com.loansai.unassisted.data.repository

import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.data.model.BureauReportDto
import com.loansai.unassisted.data.model.PANVerificationRequest
import com.loansai.unassisted.data.model.toPANDetails
import com.loansai.unassisted.data.remote.api.PANApi
import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.domain.model.PANDetails
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.PANRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.util.constants.PreferenceConstants
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.loansai.unassisted.util.converter.BureauConverter
import io.appwrite.Client
import io.appwrite.services.Databases
import io.appwrite.models.Document
import io.appwrite.Query
import com.google.gson.reflect.TypeToken
import com.loansai.unassisted.domain.model.BureauAccountSummary
import com.loansai.unassisted.domain.model.BureauAddress
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.loansai.unassisted.domain.model.BorrowerSummary
import com.loansai.unassisted.domain.model.Enquiry
import com.loansai.unassisted.domain.model.Tradeline
import com.loansai.unassisted.service.appwrite.AppwriteService
import java.util.UUID
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


@Singleton
class PANRepositoryImpl @Inject constructor(
    private val panApi: PANApi,
    private val preferencesDataSource: PreferencesDataSource,
    private val client: Client,
    private val userRepository: UserRepository,  
    private val loanRepository: LoanRepository,   
    private val appwriteService: AppwriteService 
) : PANRepository {

    // Add missing constants and Gson instance
    private val gson = Gson()
    private val panDetailsKey = PreferenceConstants.PREF_PAN_DETAILS
    private val bureauReportKey = PreferenceConstants.PREF_BUREAU_REPORT

    // Store current application ID
    private var currentApplicationId: String? = null

    // Initialize databases service
    private val databases by lazy { 
        try {
            Databases(client)
        } catch (e: Exception) {
            AppLogger.e("Error initializing Appwrite Databases: ${e.message}", e)
            null
        }
    }
    
    // Add Appwrite constants
    companion object {
        private const val DATABASE_ID = "67fb572700006c9fc191"
        const val BORROWER_SUMMARY_COLLECTION = "borrower_summary"
        const val ENQUIRIES_COLLECTION = "enquiries"
        const val TRADELINES_COLLECTION = "tradelines"
    }

    // Initialize currentApplicationId when needed
    private suspend fun ensureApplicationId() {
        if (currentApplicationId == null) {
            try {
                // Try to get from UserRepository
                val userResource = userRepository.getCurrentUser().first()
                if (userResource is Resource.Success && userResource.data.currentApplicationId != null) {
                    currentApplicationId = userResource.data.currentApplicationId
                    AppLogger.d("Retrieved applicationId from UserRepository: $currentApplicationId")
                    return
                }

                // As fallback, try to get from LoanRepository
                val application = loanRepository.getCurrentApplication()
                if (application != null) {
                    currentApplicationId = application.id
                    AppLogger.d("Retrieved applicationId from LoanRepository: $currentApplicationId")
                    return
                }
            } catch (e: Exception) {
                AppLogger.e("Error retrieving current application ID: ${e.message}", e)
            }
            
            AppLogger.w("Could not retrieve current application ID")
        }
    }
    


    // Implement the verifyPAN method
    override suspend fun verifyPAN(panNumber: String): Flow<Resource<PANDetails>> = flow {
        emit(Resource.Loading())
        
        // Ensure we have the application ID
        ensureApplicationId()
        
        try {
            AppLogger.d("Verifying PAN: $panNumber")
            val request = PANVerificationRequest(panNumber)
            val response = panApi.verifyPAN(request)
            
            if (response.isSuccessful && response.body() != null) {
                val panDetailsDto = response.body()!!
                val panDetails = panDetailsDto.toPANDetails()
                
                AppLogger.d("PAN verification successful for: $panNumber")
                
                // Save locally and to Firestore
                currentApplicationId?.let { appId ->
                    saveLocalPANDetails(appId, panDetails)
                    AppLogger.d("Saved PAN details locally for application: $appId")
                    
                    // Save PAN details to Firestore root level and also include mobile number
                    try {
                        AppLogger.d("Attempting to save PAN details to Firestore for app: $appId")
                        
                        val firestore = FirebaseFirestore.getInstance()
                        val docRef = firestore.collection("applications").document(appId)
                        
                        // Important: Get the phone number from Firebase Auth
                        // Get phone number from preferences (more reliable than Firebase Auth)
                        val phoneNumber = preferencesDataSource.getString(PreferenceConstants.PREF_USER_PHONE) ?: ""

                        // Log it for debugging
                        AppLogger.d("Retrieved phone number from preferences: $phoneNumber")

                        // If phone number from preferences is empty, try Firebase Auth as fallback
                        val finalPhoneNumber = if (phoneNumber.isNotEmpty()) {
                            phoneNumber
                        } else {
                            // Try to get from Firebase Auth
                            val currentUser = FirebaseAuth.getInstance().currentUser
                            val authPhoneNumber = currentUser?.phoneNumber ?: ""
                            AppLogger.d("Retrieved phone number from Firebase Auth: $authPhoneNumber")
                            authPhoneNumber
                        }
                        
                        
                        // First check if document exists
                        val docSnapshot = docRef.get().await()
                        AppLogger.d("Document exists check: ${docSnapshot.exists()}")
                        
                        if (docSnapshot.exists()) {
                            // Document exists, use update
                            AppLogger.d("Using update() for existing document")
                            docRef.update(
                                "panNumber", panNumber,
                                "mobileNumber", finalPhoneNumber, // USE mobileNumber here, not phoneNumber
                                "lastUpdatedAt", FieldValue.serverTimestamp(),
                                "panDetails", mapOf(
                                    "panNumber" to panDetails.panNumber,
                                    "name" to panDetails.name,
                                    "fatherName" to panDetails.fatherName,
                                    "dateOfBirth" to panDetails.dateOfBirth?.toString(),
                                    "isVerified" to panDetails.isVerified,
                                    "verificationDate" to panDetails.verificationDate?.toString()
                                )
                            ).await()
                            
                            AppLogger.d("Successfully updated document with PAN and mobile number")
                        } else {
                            // Use set for new document
                            AppLogger.d("Using set() for new document")
                            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            
                            val documentData = HashMap<String, Any>()
                            documentData["id"] = appId
                            documentData["panNumber"] = panNumber
                            documentData["mobileNumber"] = finalPhoneNumber // USE mobileNumber here
                            documentData["userId"] = userId
                            documentData["panDetails"] = mapOf(
                                "panNumber" to panDetails.panNumber,
                                "name" to panDetails.name,
                                "fatherName" to panDetails.fatherName,
                                "dateOfBirth" to panDetails.dateOfBirth?.toString(),
                                "isVerified" to panDetails.isVerified,
                                "verificationDate" to panDetails.verificationDate?.toString()
                            )
                            documentData["createdAt"] = FieldValue.serverTimestamp()
                            documentData["lastUpdatedAt"] = FieldValue.serverTimestamp()
                            
                            docRef.set(documentData, SetOptions.merge()).await()
                            AppLogger.d("Successfully set new document with PAN and mobile number")
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error saving PAN details to Firestore: ${e.message}", e)
                        e.printStackTrace()
                    }
                }
                
                emit(Resource.Success(panDetails))
            } else {
                val errorMessage = "Failed to verify PAN: ${response.message()}"
                AppLogger.e(errorMessage)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error verifying PAN: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }
    


    override suspend fun fetchBureauReport(panNumber: String): Flow<Resource<BureauReport>> = flow {
        emit(Resource.Loading())
        ensureApplicationId()
        AppLogger.d("BUREAU-FETCH: Starting bureau report fetch for PAN: $panNumber")

        try {
            // Explicitly configure the client
            client.setEndpoint("https://fra.cloud.appwrite.io/v1")
                .setProject("67fb549a0036c841fb32")
            AppLogger.d("BUREAU-FETCH: Appwrite client configured")
        } catch (e: Exception) {
            AppLogger.e("BUREAU-FETCH: Failed to configure Appwrite client: ${e.message}", e)
        }

        // Try fetching from Appwrite first
        var bureauReport: BureauReport? = null
        var fetchedFromAppwrite = false

        try {
            AppLogger.d("BUREAU-FETCH: Attempting to fetch bureau data via AppwriteService")
            val summaryResource = appwriteService.getBorrowerSummary(panNumber)
            
            if (summaryResource is Resource.Success) {
                AppLogger.d("BUREAU-FETCH: Found bureau summary in Appwrite! Creating report...")
                
                // Get borrower summary
                val summary = summaryResource.data
                
                // Get enquiries
                val enquiriesResource = appwriteService.getEnquiries(panNumber)
                val enquiries = if (enquiriesResource is Resource.Success) enquiriesResource.data else emptyList()
                
                // Get tradelines
                val tradelinesResource = appwriteService.getTradelines(panNumber)
                val tradelines = if (tradelinesResource is Resource.Success) {
                    // IMPORTANT NEW CODE: Cache tradelines in preferences
                    try {
                        if (tradelinesResource.data.isNotEmpty()) {
                            AppLogger.d("BUREAU-FETCH: Caching ${tradelinesResource.data.size} tradelines in preferences")
                            preferencesDataSource.saveTradelinesForPan(panNumber, tradelinesResource.data)
                            
                            // Also save PAN number for the current application
                            currentApplicationId?.let { appId ->
                                preferencesDataSource.savePanNumberForApplication(appId, panNumber)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("BUREAU-FETCH: Error caching tradelines: ${e.message}", e)
                    }
                    tradelinesResource.data
                } else emptyList()
                
                // Create bureau report
                bureauReport = createBureauReportFromAppwriteData(summary, enquiries, tradelines)
                fetchedFromAppwrite = true
                
                AppLogger.i("BUREAU-FETCH: Successfully created bureau report from Appwrite data")
            } else {
                AppLogger.w("BUREAU-FETCH: Couldn't find bureau data in Appwrite, falling back to API")
            }
        } catch (e: Exception) {
            AppLogger.e("BUREAU-FETCH: Error fetching from Appwrite: ${e.message}", e)
        }

        // Fall back to API if Appwrite fails
        if (!fetchedFromAppwrite) {
            AppLogger.d("BUREAU-FETCH: Falling back to API call for bureau report")
            
            try {
                val response = panApi.fetchBureauReport(panNumber)
                
                if (response.isSuccessful && response.body() != null) {
                    val bureauReportDto = response.body()!!
                    bureauReport = BureauConverter.fromDto(bureauReportDto)
                    AppLogger.d("BUREAU-FETCH: Successfully fetched bureau report via API")
                } else {
                    val errorMessage = "Failed to fetch bureau report from API: ${response.message()}"
                    AppLogger.e(errorMessage)
                    emit(Resource.Error(errorMessage))
                    return@flow
                }
            } catch (e: Exception) {
                val errorMessage = "Error fetching bureau report from API: ${e.message}"
                AppLogger.e(errorMessage, e)
                emit(Resource.Error(errorMessage))
                return@flow
            }
        }

        // Emit the report if we have one
        if (bureauReport != null) {
            AppLogger.i("BUREAU-FETCH: Successfully fetched bureau report (source: ${if(fetchedFromAppwrite) "Appwrite" else "API"})")
            emit(Resource.Success(bureauReport))
        } else {
            emit(Resource.Error("Failed to obtain bureau report from any source"))
        }
    }



    /**
    * Check if bureau data exists in Appwrite for a PAN number
    */
    override suspend fun checkBureauDataExists(panNumber: String): Boolean {
        AppLogger.d("BUREAU-CHECK: Checking if bureau data exists in Appwrite for PAN: $panNumber")
        return try {
            // Use appwriteService directly
            val hasBureauData = appwriteService.hasBureauData(panNumber)
            AppLogger.d("BUREAU-CHECK: Result: $hasBureauData")
            hasBureauData
        } catch (e: Exception) {
            AppLogger.e("BUREAU-CHECK: Error checking Appwrite: ${e.message}", e)
            
            // Fallback to direct client access if service fails
            try {
                val databases = Databases(client)
                val document = databases.getDocument(
                    databaseId = DATABASE_ID,
                    collectionId = BORROWER_SUMMARY_COLLECTION,
                    documentId = panNumber
                )
                AppLogger.d("BUREAU-CHECK: Found document via direct client access")
                true
            } catch (e2: Exception) {
                AppLogger.e("BUREAU-CHECK: Direct client access failed: ${e2.message}", e2)
                false
            }
        }
    }

    // Helper method to create bureau report from Appwrite data
    // Alternative approach using your existing converter
    private fun createBureauReportFromAppwriteData(
        summary: BorrowerSummary,
        enquiries: List<Enquiry>,
        tradelines: List<Tradeline>
    ): BureauReport {
        try {
            // Log what we're doing
            AppLogger.d("Creating BureauReport from Appwrite data for PAN: ${summary.panNumber}")
            
            // Create a minimal BureauReportDto with just the essential fields
            val dto = BureauReportDto(
                customerID = UUID.randomUUID().toString(),
                panNumber = summary.panNumber,
                name = summary.customerName ?: "",
                creditScore = summary.creditScore,
                // Omit other fields to avoid errors
                activeLoanAccounts = summary.openAccounts,
                totalOutstandingAmount = summary.currentBalance,
                totalEMIAmount = 0.0,
                lastUpdated = LocalDateTime.now().toString()
            )
            
            // Use your existing converter which should be compatible with your model
            return BureauConverter.fromDto(dto)
        } catch (e: Exception) {
            AppLogger.e("Error creating bureau report from Appwrite data: ${e.message}", e)
            
            // Create a minimal report with just the ID and PAN if conversion fails
            return BureauReport(
                id = UUID.randomUUID().toString(),
                panNumber = summary.panNumber,
                creditScore = summary.creditScore,
                createdAt = LocalDateTime.now()
            )
        }
    }


    /**
    * Save bureau report to the application
    */
    override suspend fun saveBureauReport(
        applicationId: String,
        bureauReport: BureauReport
    ): Flow<Resource<Boolean>> = flow {
        emit(Resource.Loading())
        
        // Store the application ID
        currentApplicationId = applicationId
        
        try {
            // Convert domain model to DTO
            val bureauReportDto = convertToBureauReportDto(bureauReport)
            
            // Save key bureau fields to Firestore application document
            saveBureauFieldsToFirestore(applicationId, bureauReport)
            
            // Call API to save bureau report
            val response = panApi.saveBureauReport(applicationId, bureauReportDto)
            
            if (response.isSuccessful) {
                // Save locally
                saveLocalBureauReport(applicationId, bureauReport)
                AppLogger.d("Bureau report saved to API and locally for application: $applicationId")
                
                emit(Resource.Success(true))
            } else {
                val errorMessage = "Failed to save bureau report: ${response.message()}"
                AppLogger.e(errorMessage)
                
                // Still save locally even if API fails
                saveLocalBureauReport(applicationId, bureauReport)
                AppLogger.d("Bureau report saved locally (API failed) for application: $applicationId")
                
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error saving bureau report: ${e.message}"
            AppLogger.e(errorMessage, e)
            
            // Try to save locally even if API fails
            try {
                saveLocalBureauReport(applicationId, bureauReport)
                AppLogger.d("Bureau report saved locally (after exception) for application: $applicationId")
            } catch (e2: Exception) {
                AppLogger.e("Failed to save bureau report locally: ${e2.message}", e2)
            }
            
            emit(Resource.Error(errorMessage))
        }
    }

    /**
    * Save important bureau fields directly to the Firestore application document
    */
    private suspend fun saveBureauFieldsToFirestore(applicationId: String, bureauReport: BureauReport) {
        try {
            AppLogger.d("Saving bureau fields to Firestore for application: $applicationId")

            // Use safe defaults (0 or false) if data is missing
            val creditScore = bureauReport.creditScore ?: 0
            val totalAccounts = bureauReport.accountSummary?.totalAccounts ?: 0
            val openAccounts = bureauReport.accountSummary?.activeAccounts ?: 0
            // Use 0L for Long if null
            val currentBalance = bureauReport.accountSummary?.totalOutstanding ?: 0L

            // Create map of data to update
            // Using FieldValue.serverTimestamp() for Firestore timestamp
            val bureauFields = mapOf(
                // Add the PAN number here
                "panNumber" to bureauReport.panNumber,
                "bureauData" to mapOf(
                    "creditScore" to creditScore,
                    "totalAccounts" to totalAccounts,
                    "openAccounts" to openAccounts,
                    "currentBalance" to currentBalance,
                    "fetchedAt" to FieldValue.serverTimestamp() // Use server timestamp
                ),
                "bureauScore" to creditScore,
                "bureauTotalAccounts" to totalAccounts,
                "bureauOpenAccounts" to openAccounts,
                "bureauCurrentBalance" to currentBalance,
                "bureauReportFetched" to true,
                "bureauReportFetchedAt" to FieldValue.serverTimestamp() // Use server timestamp
            )

            // Update Firestore - use safer approach that works for both new and existing documents
            val firestore = FirebaseFirestore.getInstance()
            val docRef = firestore.collection("applications").document(applicationId)
            
            // Check if document exists first
            val docSnapshot = docRef.get().await()
            
            if (docSnapshot.exists()) {
                // Use update for existing document
                docRef.update(bureauFields).await()
            } else {
                // Use set with merge for new document
                docRef.set(bureauFields, SetOptions.merge()).await()
            }

            AppLogger.d("Successfully saved bureau fields to Firestore for application: $applicationId")
        } catch (e: Exception) {
            AppLogger.e("Failed to save bureau fields to Firestore: ${e.message}", e)
            // Consider how to handle this error - maybe retry later? For now, just log it.
        }
    }



    /**
     * Get bureau report for an application
     */
    override fun getBureauReport(applicationId: String): Flow<Resource<BureauReport>> = flow {
        emit(Resource.Loading())
        
        // Store the application ID
        currentApplicationId = applicationId
        
        try {
            // Try to get from local storage first (fastest)
            val localBureauReport = getLocalBureauReport(applicationId)
            if (localBureauReport != null) {
                AppLogger.d("Retrieved bureau report from local storage for application: $applicationId")
                emit(Resource.Success(localBureauReport))
                return@flow
            }
            
            // Try to fetch from API if not in local storage
            try {
                AppLogger.d("Fetching bureau report from API for application: $applicationId")
                val response = panApi.getBureauReport(applicationId)
                
                if (response.isSuccessful && response.body() != null) {
                    val bureauReportDto = response.body()!!
                    
                    // Convert DTO to domain model
                    val bureauReport = convertToBureauReport(bureauReportDto)
                    
                    // Save locally for future use
                    saveLocalBureauReport(applicationId, bureauReport)
                    AppLogger.d("Bureau report retrieved from API and saved locally for application: $applicationId")
                    
                    emit(Resource.Success(bureauReport))
                    return@flow
                } else {
                    AppLogger.w("Failed to get bureau report from API: ${response.message()}")
                }
            } catch (e: Exception) {
                AppLogger.e("Error fetching bureau report from API: ${e.message}", e)
                // Continue to other methods
            }
            
            // If we get here, both local storage and API failed
            val errorMessage = "Bureau report not found for application: $applicationId"
            AppLogger.e(errorMessage)
            emit(Resource.Error(errorMessage))
        } catch (e: Exception) {
            val errorMessage = "Error getting bureau report: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    // Helper functions for local storage
    
    private suspend fun saveLocalPANDetails(applicationId: String, panDetails: PANDetails) {
        val key = "${panDetailsKey}_${applicationId}"
        val json = gson.toJson(panDetails)
        preferencesDataSource.saveString(key, json)
    }
    
    private suspend fun getLocalPANDetails(applicationId: String): PANDetails? {
        val key = "${panDetailsKey}_${applicationId}"
        val json = preferencesDataSource.getString(key)
        return if (json != null) {
            try {
                gson.fromJson(json, PANDetails::class.java)
            } catch (e: Exception) {
                AppLogger.e("Error parsing stored PAN details: ${e.message}", e)
                null
            }
        } else {
            null
        }
    }
    
    private suspend fun saveLocalBureauReport(applicationId: String, bureauReport: BureauReport) {
        val key = "${bureauReportKey}_${applicationId}"
        val json = gson.toJson(bureauReport)
        preferencesDataSource.saveString(key, json)
    }
    
    private suspend fun getLocalBureauReport(applicationId: String): BureauReport? {
        val key = "${bureauReportKey}_${applicationId}"
        val json = preferencesDataSource.getString(key)
        return if (json != null) {
            try {
                gson.fromJson(json, BureauReport::class.java)
            } catch (e: Exception) {
                AppLogger.e("Error parsing stored bureau report: ${e.message}", e)
                null
            }
        } else {
            null
        }
    }
    
    // Conversion Helper Functions

    private fun convertToBureauReport(dto: BureauReportDto): BureauReport {
        return BureauConverter.fromDto(dto)
    }
    
    private fun convertToBureauReportDto(bureauReport: BureauReport): BureauReportDto {
        return BureauConverter.toDto(bureauReport)
    }



    // Helper method to create bureau report from Appwrite documents
    private fun createBureauReportFromAppwrite(
        borrowerSummary: Document<Map<String, Any>>,
        enquiries: List<Document<Map<String, Any>>>,
        tradelines: List<Document<Map<String, Any>>>
    ): BureauReport {
        try {
            // Extract basic info from borrower summary
            val panNumber = borrowerSummary.data["panNumber"] as? String ?: ""
            val customerName = borrowerSummary.data["customerName"] as? String
            val creditScore = (borrowerSummary.data["creditScore"] as? Number)?.toInt()
            val reportDate = borrowerSummary.data["reportDate"] as? String
            val dateOfBirth = borrowerSummary.data["dateOfBirth"] as? String
            val gender = borrowerSummary.data["gender"] as? String
            
            AppLogger.d("Creating bureau report for PAN: $panNumber, Name: $customerName, Score: $creditScore")
            
            // Calculate total current balance from tradelines
            val totalCurrentBalance = tradelines.sumOf { tradeline ->
                (tradeline.data["currentBalance"] as? Number)?.toLong() ?: 0L
            }
            
            // Extract account summary with additional fields we want to save to Firestore
            val accountSummary = BureauAccountSummary(
                totalAccounts = (borrowerSummary.data["totalAccounts"] as? Number)?.toInt() ?: 0,
                activeAccounts = (borrowerSummary.data["openAccounts"] as? Number)?.toInt() ?: 0,
                closedAccounts = (borrowerSummary.data["closedAccounts"] as? Number)?.toInt() ?: 0,
                totalCreditLimit = (borrowerSummary.data["totalLoanAmount"] as? Number)?.toLong() ?: 0L,
                totalOutstanding = totalCurrentBalance, // Use calculated value from tradelines
                totalOverdue = (borrowerSummary.data["totalOverdueAmount"] as? Number)?.toLong() ?: 0L,
                delinquentAccountsCount = 0, // Calculate from tradelines if needed
                suitFiledAccountsCount = if (borrowerSummary.data["suitFiled"] as? Boolean == true) 1 else 0,
                writtenOffAccountsCount = if (borrowerSummary.data["writtenOffStatus"] as? Boolean == true) 1 else 0
            )
            
            AppLogger.d("Account summary - Total Accounts: ${accountSummary.totalAccounts}, Active: ${accountSummary.activeAccounts}, Current Balance: ${accountSummary.totalOutstanding}")
            
            // Parse addresses from string representation
            val addressesString = borrowerSummary.data["addresses"] as? String
            val addresses = if (!addressesString.isNullOrEmpty()) {
                try {
                    // Try to parse JSON structure from string
                    val type = object : TypeToken<List<Map<String, String>>>() {}.type
                    val addressMaps: List<Map<String, String>> = gson.fromJson(addressesString, type)
                    
                    // Convert each map to a BureauAddress
                    addressMaps.map { addressMap ->
                        BureauAddress(
                            addressLine1 = addressMap["addressLine1"] ?: "",
                            addressLine2 = addressMap["addressLine2"],
                            city = addressMap["city"] ?: "",
                            state = addressMap["state"] ?: "",
                            pincode = addressMap["pincode"] ?: "",
                            addressType = addressMap["addressType"] ?: "Residential",
                            residenceType = addressMap["residenceType"]
                        )
                    }
                } catch (e: Exception) {
                    AppLogger.e("Error parsing addresses: ${e.message}", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
            
            // Count recent enquiries
            val inquiryCountLast30Days = enquiries.count { enquiry ->
                try {
                    val enquiryDateStr = enquiry.data["enquiryDate"] as? String
                    if (enquiryDateStr != null) {
                        // Parse date and check if it's within last 30 days
                        val enquiryDate = parseStringDate(enquiryDateStr)
                        val thirtyDaysAgo = LocalDate.now().minusDays(30)
                        enquiryDate.isAfter(thirtyDaysAgo)
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            
            // Calculate total written off amount from tradelines
            val totalWrittenOffAmount = tradelines.sumOf { tradeline ->
                (tradeline.data["writtenOffTotal"] as? Number)?.toLong() ?: 0L
            }
            
            // Check if there are any delinquent accounts
            val hasExistingDefaultOrWriteOff = totalWrittenOffAmount > 0 || 
                                            tradelines.any { tradeline ->
                                                val status = tradeline.data["facilityStatus"] as? String
                                                status?.contains("Default", ignoreCase = true) == true ||
                                                status?.contains("Overdue", ignoreCase = true) == true
                                            }
            
            return BureauReport(
                id = borrowerSummary.id,
                panNumber = panNumber,
                controlNumber = borrowerSummary.data["controlNumber"] as? String,
                customerName = customerName,
                creditScore = creditScore,
                scoreDate = LocalDate.now(), // Set appropriate date if available
                bureauType = null, // Set if available in data
                reportDate = reportDate,
                dateOfBirth = dateOfBirth?.let { parseStringDate(it) },
                gender = gender,
                addresses = addresses,
                mobilePhones = emptyList(), // Extract from borrower summary if available
                email = borrowerSummary.data["email"] as? String,
                accountSummary = accountSummary,
                inquiryCountLast30Days = inquiryCountLast30Days,
                hasExistingDefaultOrWriteOff = hasExistingDefaultOrWriteOff,
                totalWrittenOffAmount = totalWrittenOffAmount,
                delinquentStatus = borrowerSummary.data["delinquencyStatus"] as? String ?: "Standard",
                createdAt = LocalDateTime.now()
            )
        } catch (e: Exception) {
            AppLogger.e("Error creating bureau report from Appwrite: ${e.message}", e)
            
            // Return a basic report with the PAN number
            return BureauReport(
                id = borrowerSummary.id,
                panNumber = borrowerSummary.data["panNumber"] as? String ?: "",
                creditScore = (borrowerSummary.data["creditScore"] as? Number)?.toInt() ?: 0,
                createdAt = LocalDateTime.now()
            )
        }
    }



    // Helper method to parse dates
    private fun parseStringDate(dateString: String): LocalDate {
        return try {
            // Try multiple date formats
            val formats = listOf(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy")
            )
            
            for (format in formats) {
                try {
                    return LocalDate.parse(dateString, format)
                } catch (e: Exception) {
                    // Try next format
                }
            }
            
            // If all formats fail, default to current date
            AppLogger.w("Could not parse date: $dateString, using current date")
            LocalDate.now()
        } catch (e: Exception) {
            AppLogger.e("Error parsing date: $dateString - ${e.message}", e)
            LocalDate.now()
        }
    }
}