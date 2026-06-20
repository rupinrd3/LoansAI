package com.loansai.unassisted.service.appwrite.impl

import com.google.firebase.firestore.FirebaseFirestore
import com.loansai.unassisted.domain.model.BorrowerSummary
import com.loansai.unassisted.domain.model.Enquiry
import com.loansai.unassisted.domain.model.Tradeline
import com.loansai.unassisted.service.appwrite.AppwriteService
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import io.appwrite.Client
import io.appwrite.ID
import io.appwrite.Query
import io.appwrite.exceptions.AppwriteException
import io.appwrite.models.DocumentList
import io.appwrite.services.Databases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppwriteServiceImpl @Inject constructor(
    private val client: Client,
    private val firestore: FirebaseFirestore // Assuming this is used for caching
) : AppwriteService {

    // Appwrite database ID and collection IDs
    private val databaseId = "67fb572700006c9fc191"
    private val borrowerSummaryCollection = "borrower_summary"
    private val enquiriesCollection = "enquiries"
    private val tradelinesCollection = "tradelines"

    private fun getDatabases(): Databases {
        try {
            // Ensure client is configured with proper endpoint and project ID before each call
            client.setEndpoint("https://fra.cloud.appwrite.io/v1")
                .setProject("67fb549a0036c841fb32")
            AppLogger.d("[AppwriteService] Appwrite client configured successfully")
        } catch (e: Exception) {
            AppLogger.e("[AppwriteService] Failed to configure Appwrite client: ${e.message}", e)
            // Continue even if configuration fails, as client might already be configured
        }
        return Databases(client)
    }

    override suspend fun getBorrowerSummary(panNumber: String): Resource<BorrowerSummary> {
        AppLogger.d("[AppwriteService] Attempting to get BorrowerSummary for PAN: $panNumber") // <-- Log Start
        return withContext(Dispatchers.IO) {
            try {
                val databases = getDatabases()
                AppLogger.d("[AppwriteService] Calling Appwrite getDocument (DB: $databaseId, Coll: $borrowerSummaryCollection, DocID: $panNumber)") // <-- Log Before Call

                val document = databases.getDocument(
                    databaseId = databaseId,
                    collectionId = borrowerSummaryCollection,
                    documentId = panNumber // PAN is used as document ID here
                )
                AppLogger.i("[AppwriteService] Successfully fetched BorrowerSummary document for PAN: $panNumber") // <-- Log Success

                // Convert to domain model (existing conversion logic)
                 val borrowerSummary = BorrowerSummary(
                    panNumber = document.data["panNumber"] as? String ?: panNumber,
                    controlNumber = document.data["controlNumber"] as? String,
                    customerName = document.data["customerName"] as? String,
                    creditScore = (document.data["creditScore"] as? Number)?.toInt(),
                    reportDate = document.data["reportDate"] as? String,
                    dateOfBirth = document.data["dateOfBirth"] as? String,
                    gender = document.data["gender"] as? String,
                    addresses = document.data["addresses"] as? String,
                    contacts = document.data["contacts"] as? String,
                    email = document.data["email"] as? String,
                    totalAccounts = (document.data["totalAccounts"] as? Number)?.toInt() ?: 0,
                    openAccounts = (document.data["openAccounts"] as? Number)?.toInt() ?: 0,
                    closedAccounts = (document.data["closedAccounts"] as? Number)?.toInt() ?: 0,
                    totalLoanAmount = (document.data["totalLoanAmount"] as? Number)?.toDouble() ?: 0.0,
                    currentBalance = (document.data["currentBalance"] as? Number)?.toDouble() ?: 0.0,
                    totalOverdueAmount = (document.data["totalOverdueAmount"] as? Number)?.toDouble() ?: 0.0,
                    suitFiled = document.data["suitFiled"] as? Boolean ?: false,
                    wilfulDefault = document.data["wilfulDefault"] as? Boolean ?: false,
                    writtenOffStatus = document.data["writtenOffStatus"] as? Boolean ?: false,
                    delinquencyStatus = document.data["delinquencyStatus"] as? String
                )
                Resource.Success(borrowerSummary)
            } catch (e: AppwriteException) { // <-- Catch specific AppwriteException
                val errorDetails = "Code: ${e.code}, Type: ${e.type}, Response: ${e.response}"
                AppLogger.e("[AppwriteService] AppwriteException fetching borrower summary for PAN $panNumber: $errorDetails", e)
                Resource.Error("Appwrite Error (${e.code}): Failed to fetch borrower summary. ${e.message}")
            } catch (e: Exception) { // <-- Catch general exceptions
                AppLogger.e("[AppwriteService] General Exception fetching borrower summary for PAN $panNumber: ${e.message}", e)
                Resource.Error("Failed to fetch borrower summary: ${e.message}")
            }
        }
    }

    override suspend fun getEnquiries(panNumber: String): Resource<List<Enquiry>> {
        AppLogger.d("[AppwriteService] Attempting to get Enquiries for PAN: $panNumber") // <-- Log Start
        return withContext(Dispatchers.IO) {
            try {
                val databases = getDatabases()
                val query = listOf(Query.equal("panNumber", panNumber))
                AppLogger.d("[AppwriteService] Calling Appwrite listDocuments (DB: $databaseId, Coll: $enquiriesCollection, Query: $query)") // <-- Log Before Call

                val documents = databases.listDocuments(
                    databaseId = databaseId,
                    collectionId = enquiriesCollection,
                    queries = query
                )
                AppLogger.i("[AppwriteService] Successfully fetched ${documents.total} Enquiries documents for PAN: $panNumber") // <-- Log Success

                // Convert to domain models (existing conversion logic)
                 val enquiries = documents.documents.map { doc ->
                    Enquiry(
                        id = doc.id,
                        panNumber = doc.data["panNumber"] as? String ?: panNumber,
                        enquiryDate = doc.data["enquiryDate"] as? String,
                        memberName = doc.data["memberName"] as? String,
                        purpose = doc.data["purpose"] as? String,
                        type = doc.data["type"] as? String,
                        amount = (doc.data["amount"] as? Number)?.toDouble()
                    )
                }
                Resource.Success(enquiries)
            } catch (e: AppwriteException) { // <-- Catch specific AppwriteException
                val errorDetails = "Code: ${e.code}, Type: ${e.type}, Response: ${e.response}"
                AppLogger.e("[AppwriteService] AppwriteException fetching enquiries for PAN $panNumber: $errorDetails", e)
                Resource.Error("Appwrite Error (${e.code}): Failed to fetch enquiries. ${e.message}")
            } catch (e: Exception) { // <-- Catch general exceptions
                AppLogger.e("[AppwriteService] General Exception fetching enquiries for PAN $panNumber: ${e.message}", e)
                Resource.Error("Failed to fetch enquiries: ${e.message}")
            }
        }
    }



    override suspend fun getTradelines(panNumber: String): Resource<List<Tradeline>> {
        AppLogger.d("[AppwriteService] Attempting to get Tradelines for PAN: $panNumber") // <-- Log Start
        return withContext(Dispatchers.IO) {
            try {
                val databases = getDatabases()
                val query = listOf(Query.equal("panNumber", panNumber))
                AppLogger.d("[AppwriteService] Calling Appwrite listDocuments (DB: $databaseId, Coll: $tradelinesCollection, Query: $query)") // <-- Log Before Call

                val documents = databases.listDocuments(
                    databaseId = databaseId,
                    collectionId = tradelinesCollection,
                    queries = query
                )
                AppLogger.i("[AppwriteService] Successfully fetched ${documents.total} Tradelines documents for PAN: $panNumber") // <-- Log Success

                // Convert to domain models (existing conversion logic)
                val tradelines = documents.documents.map { doc ->
                    Tradeline(
                        id = doc.id,
                        panNumber = doc.data["panNumber"] as? String ?: panNumber,
                        memberName = doc.data["memberName"] as? String ?: "Unknown Lender",
                        accountType = doc.data["accountType"] as? String ?: "Unknown",
                        accountNumber = doc.data["accountNumber"] as? String ?: "XXXXXXXX",
                        ownership = doc.data["ownership"] as? String,
                        creditLimit = (doc.data["creditLimit"] as? Number)?.toDouble(),
                        highCredit = (doc.data["highCredit"] as? Number)?.toDouble(),
                        currentBalance = (doc.data["currentBalance"] as? Number)?.toDouble(),
                        amountOverdue = (doc.data["amountOverdue"] as? Number)?.toDouble(),
                        rateOfInterest = (doc.data["rateOfInterest"] as? Number)?.toDouble(),
                        repaymentTenure = (doc.data["repaymentTenure"] as? Number)?.toInt(),
                        emiAmount = (doc.data["emiAmount"] as? Number)?.toDouble(),
                        dateOpened = doc.data["dateOpened"] as? String,
                        dateClosed = doc.data["dateClosed"] as? String,
                        lastPaymentDate = doc.data["lastPaymentDate"] as? String,
                        dateReported = doc.data["dateReported"] as? String,
                        facilityStatus = doc.data["facilityStatus"] as? String,
                        suitFiled = doc.data["suitFiled"] as? String,
                        paymentFrequency = doc.data["paymentFrequency"] as? String,
                        paymentHistory = doc.data["paymentHistory"] as? String,
                        writtenOffTotal = (doc.data["writtenOffTotal"] as? Number)?.toDouble(),
                        writtenOffPrincipal = (doc.data["writtenOffPrincipal"] as? Number)?.toDouble(),
                        controlNumber = doc.data["controlNumber"] as? String
                    )
                }
                Resource.Success(tradelines)
            } catch (e: AppwriteException) { // <-- Catch specific AppwriteException
                val errorDetails = "Code: ${e.code}, Type: ${e.type}, Response: ${e.response}"
                AppLogger.e("[AppwriteService] AppwriteException fetching tradelines for PAN $panNumber: $errorDetails", e)
                Resource.Error("Appwrite Error (${e.code}): Failed to fetch tradelines. ${e.message}")
            } catch (e: Exception) { // <-- Catch general exceptions
                AppLogger.e("[AppwriteService] General Exception fetching tradelines for PAN $panNumber: ${e.message}", e)
                Resource.Error("Failed to fetch tradelines: ${e.message}")
            }
        }
    }

    override suspend fun fetchAndCacheBorrowerSummary(panNumber: String): Resource<BorrowerSummary> {
        AppLogger.d("[AppwriteService] Attempting fetchAndCacheBorrowerSummary for PAN: $panNumber") // <-- Log Start
        // Get from Appwrite first
        val summaryResource = getBorrowerSummary(panNumber) // This already has enhanced logging

        if (summaryResource is Resource.Success) {
            try {
                AppLogger.d("[AppwriteService] Caching BorrowerSummary for PAN $panNumber to Firestore") // <-- Log Cache Start
                // Cache in Firestore (existing logic)
                 val summary = summaryResource.data
                 val summaryData = mapOf(
                    "panNumber" to summary.panNumber,
                    "controlNumber" to summary.controlNumber,
                    "customerName" to summary.customerName,
                    "creditScore" to summary.creditScore,
                    "reportDate" to summary.reportDate,
                    "dateOfBirth" to summary.dateOfBirth,
                    "gender" to summary.gender,
                    "addresses" to summary.addresses,
                    "contacts" to summary.contacts,
                    "email" to summary.email,
                    "totalAccounts" to summary.totalAccounts,
                    "openAccounts" to summary.openAccounts,
                    "closedAccounts" to summary.closedAccounts,
                    "totalLoanAmount" to summary.totalLoanAmount,
                    "currentBalance" to summary.currentBalance,
                    "totalOverdueAmount" to summary.totalOverdueAmount,
                    "suitFiled" to summary.suitFiled,
                    "wilfulDefault" to summary.wilfulDefault,
                    "writtenOffStatus" to summary.writtenOffStatus,
                    "delinquencyStatus" to summary.delinquencyStatus,
                    "cachedAt" to com.google.firebase.Timestamp.now()
                )
                firestore.collection("bureau_cache")
                    .document(panNumber)
                    .set(summaryData)
                    .await()
                 AppLogger.i("[AppwriteService] Successfully cached BorrowerSummary for PAN $panNumber") // <-- Log Cache Success
            } catch (e: Exception) {
                AppLogger.e("[AppwriteService] Error caching borrower summary: ${e.message}", e) // <-- Log Cache Error
                // Continue despite caching error
            }
        }
        return summaryResource
    }

    override suspend fun fetchAndCacheEnquiries(panNumber: String): Resource<List<Enquiry>> {
         AppLogger.d("[AppwriteService] Attempting fetchAndCacheEnquiries for PAN: $panNumber") // <-- Log Start
        val enquiriesResource = getEnquiries(panNumber) // This already has enhanced logging

        if (enquiriesResource is Resource.Success) {
            try {
                AppLogger.d("[AppwriteService] Caching ${enquiriesResource.data.size} Enquiries for PAN $panNumber to Firestore") // <-- Log Cache Start
                // Cache in Firestore (existing logic)
                val batch = firestore.batch()
                val enquiriesCollectionRef = firestore.collection("bureau_cache") // Corrected variable name
                    .document(panNumber)
                    .collection("enquiries")

                // Delete existing cached enquiries
                val existingDocs = enquiriesCollectionRef.get().await()
                AppLogger.d("[AppwriteService] Deleting ${existingDocs.size()} existing cached enquiries for PAN $panNumber")
                for (doc in existingDocs.documents) {
                    batch.delete(doc.reference)
                }

                // Add new enquiries
                 for (enquiry in enquiriesResource.data) {
                    val enquiryData = mapOf(
                        "panNumber" to enquiry.panNumber,
                        "enquiryDate" to enquiry.enquiryDate,
                        "memberName" to enquiry.memberName,
                        "purpose" to enquiry.purpose,
                        "type" to enquiry.type,
                        "amount" to enquiry.amount,
                        "cachedAt" to com.google.firebase.Timestamp.now()
                    )
                    val docRef = enquiriesCollectionRef.document(enquiry.id) // Corrected reference
                    batch.set(docRef, enquiryData)
                }
                batch.commit().await()
                AppLogger.i("[AppwriteService] Successfully cached Enquiries for PAN $panNumber") // <-- Log Cache Success
            } catch (e: Exception) {
                AppLogger.e("[AppwriteService] Error caching enquiries: ${e.message}", e) // <-- Log Cache Error
                // Continue despite caching error
            }
        }
        return enquiriesResource
    }

    override suspend fun fetchAndCacheTradelines(panNumber: String): Resource<List<Tradeline>> {
         AppLogger.d("[AppwriteService] Attempting fetchAndCacheTradelines for PAN: $panNumber") // <-- Log Start
        val tradelinesResource = getTradelines(panNumber) // This already has enhanced logging

        if (tradelinesResource is Resource.Success) {
            try {
                AppLogger.d("[AppwriteService] Caching ${tradelinesResource.data.size} Tradelines for PAN $panNumber to Firestore") // <-- Log Cache Start
                // Cache in Firestore (existing logic)
                 val batch = firestore.batch()
                 val tradelinesCollectionRef = firestore.collection("bureau_cache") // Corrected variable name
                    .document(panNumber)
                    .collection("tradelines")

                // Delete existing cached tradelines
                 val existingDocs = tradelinesCollectionRef.get().await() // Corrected reference
                 AppLogger.d("[AppwriteService] Deleting ${existingDocs.size()} existing cached tradelines for PAN $panNumber")
                 for (doc in existingDocs.documents) {
                     batch.delete(doc.reference)
                 }

                // Add new tradelines
                 for (tradeline in tradelinesResource.data) {
                    val tradelineData = mapOf(
                        "panNumber" to tradeline.panNumber,
                        "memberName" to tradeline.memberName,
                        "accountType" to tradeline.accountType,
                        "accountNumber" to tradeline.accountNumber,
                        "ownership" to tradeline.ownership,
                        "creditLimit" to tradeline.creditLimit,
                        "highCredit" to tradeline.highCredit,
                        "currentBalance" to tradeline.currentBalance,
                        "amountOverdue" to tradeline.amountOverdue,
                        "rateOfInterest" to tradeline.rateOfInterest,
                        "repaymentTenure" to tradeline.repaymentTenure,
                        "emiAmount" to tradeline.emiAmount,
                        "dateOpened" to tradeline.dateOpened,
                        "dateClosed" to tradeline.dateClosed,
                        "lastPaymentDate" to tradeline.lastPaymentDate,
                        "dateReported" to tradeline.dateReported,
                        "facilityStatus" to tradeline.facilityStatus,
                        "suitFiled" to tradeline.suitFiled,
                        "paymentFrequency" to tradeline.paymentFrequency,
                        "paymentHistory" to tradeline.paymentHistory,
                        "writtenOffTotal" to tradeline.writtenOffTotal,
                        "writtenOffPrincipal" to tradeline.writtenOffPrincipal,
                        "controlNumber" to tradeline.controlNumber,
                        "cachedAt" to com.google.firebase.Timestamp.now()
                    )
                    val docRef = tradelinesCollectionRef.document(tradeline.id) // Corrected reference
                    batch.set(docRef, tradelineData)
                 }
                 batch.commit().await()
                 AppLogger.i("[AppwriteService] Successfully cached Tradelines for PAN $panNumber") // <-- Log Cache Success
            } catch (e: Exception) {
                AppLogger.e("[AppwriteService] Error caching tradelines: ${e.message}", e) // <-- Log Cache Error
                // Continue despite caching error
            }
        }
        return tradelinesResource
    }

     override suspend fun hasBureauData(panNumber: String): Boolean {
        AppLogger.d("[AppwriteService] Checking if bureau data exists for PAN: $panNumber")
        return withContext(Dispatchers.IO) {
             try {
                 // Try Appwrite first
                 AppLogger.d("[AppwriteService] Checking Appwrite for borrowerSummary document ID: $panNumber")
                 val databases = getDatabases()
                 val document = databases.getDocument(
                     databaseId = databaseId,
                     collectionId = borrowerSummaryCollection,
                     documentId = panNumber
                 )
                 AppLogger.d("[AppwriteService] Data found in Appwrite for PAN: $panNumber")
                 true // If no exception, it exists
             } catch (e: AppwriteException) {
                 AppLogger.w("[AppwriteService] Data not found in Appwrite for PAN $panNumber (Code: ${e.code}, Type: ${e.type}). Checking Firestore cache.")
                 try {
                    // Fall back to Firestore cache
                     AppLogger.d("[AppwriteService] Checking Firestore cache for document ID: $panNumber")
                    val docRef = firestore.collection("bureau_cache").document(panNumber)
                    val snapshot = docRef.get().await()
                    val exists = snapshot.exists()
                    AppLogger.d("[AppwriteService] Data ${if(exists) "found" else "not found"} in Firestore cache for PAN: $panNumber")
                    exists
                } catch (e2: Exception) {
                     AppLogger.e("[AppwriteService] Error checking Firestore cache for PAN $panNumber: ${e2.message}", e2)
                     false
                 }
            } catch (e: Exception) {
                AppLogger.e("[AppwriteService] General error checking bureau data for PAN $panNumber: ${e.message}", e)
                 false
             }
         }
     }

      override suspend fun getBureauScore(panNumber: String): Int? {
        AppLogger.d("[AppwriteService] Attempting to get Bureau Score for PAN: $panNumber")
          return withContext(Dispatchers.IO) {
              try {
                  // Try Appwrite first
                  AppLogger.d("[AppwriteService] Trying to fetch score from Appwrite for PAN: $panNumber")
                  val summaryResource = getBorrowerSummary(panNumber) // Has internal logging
                  if (summaryResource is Resource.Success) {
                      val score = summaryResource.data.creditScore
                      AppLogger.d("[AppwriteService] Score found in Appwrite: $score")
                      return@withContext score // Return score if found
                  } else {
                       AppLogger.w("[AppwriteService] Failed to get summary from Appwrite for score check. Falling back to Firestore cache.")
                      // Fall back to Firestore cache
                      AppLogger.d("[AppwriteService] Checking Firestore cache for score for PAN: $panNumber")
                      val docRef = firestore.collection("bureau_cache").document(panNumber)
                      val snapshot = docRef.get().await()
                      if (snapshot.exists()) {
                         val score = (snapshot.get("creditScore") as? Number)?.toInt()
                         AppLogger.d("[AppwriteService] Score found in Firestore cache: $score")
                         return@withContext score
                      } else {
                          AppLogger.w("[AppwriteService] Score not found in Firestore cache for PAN: $panNumber")
                          return@withContext null // Return null if not found in cache either
                      }
                  }
              } catch (e: Exception) {
                  AppLogger.e("[AppwriteService] Exception getting bureau score for PAN $panNumber: ${e.message}", e)
                  return@withContext null // Return null on error
              }
          }
      }
}