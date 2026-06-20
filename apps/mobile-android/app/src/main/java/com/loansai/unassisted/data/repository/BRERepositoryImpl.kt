package com.loansai.unassisted.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.*
import com.loansai.unassisted.data.remote.api.BREApi // Ensure BREApi is imported
import com.loansai.unassisted.domain.model.ApplicationStatus
import com.loansai.unassisted.domain.model.BREInput
import com.loansai.unassisted.domain.model.BankStatementData
import com.loansai.unassisted.domain.model.BureauData
import com.loansai.unassisted.domain.model.DecisionStatus
import com.loansai.unassisted.domain.model.DocumentExtractions
import com.loansai.unassisted.domain.model.IncomeTaxReturnData
import com.loansai.unassisted.domain.model.LlmProcessingStatus
import com.loansai.unassisted.domain.model.LoanApplication
import com.loansai.unassisted.domain.model.LoanOffer
import com.loansai.unassisted.domain.model.OfferStatus
import com.loansai.unassisted.domain.model.SalarySlipData
import com.loansai.unassisted.domain.repository.BRERepository
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.ObligationRefinementRepository
import com.loansai.unassisted.util.DateConverter
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.logger.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
// Add missing imports if any were removed previously
import com.loansai.unassisted.domain.model.ExcludedLoan
import com.loansai.unassisted.domain.model.PANDetails
import com.loansai.unassisted.domain.model.PersonalInfo
import com.loansai.unassisted.domain.model.EmploymentDetails
import com.loansai.unassisted.domain.model.ObligationRefinement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.loansai.unassisted.util.validation.BREDataStateChecker
import com.loansai.unassisted.util.constants.BREErrorCodes
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.SetOptions
import com.loansai.unassisted.domain.model.Address
import com.loansai.unassisted.domain.model.AddressType
import com.loansai.unassisted.domain.model.ApplicationStep
import com.loansai.unassisted.domain.model.MaritalStatus
import java.time.LocalDate

import com.loansai.unassisted.domain.model.Gender
import com.loansai.unassisted.domain.model.EmploymentType
import com.loansai.unassisted.domain.model.VerificationMethod





@Singleton
class BRERepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val breApi: BREApi,
    private val loanRepository: LoanRepository,
    private val obligationRefinementRepository: ObligationRefinementRepository
) : BRERepository {

    /**
     * Start the BRE process for a loan application - direct API call
     */
    override suspend fun startBREProcess(applicationId: String): Flow<Resource<String>> = flow {
        val processId = UUID.randomUUID().toString().substring(0, 8)
        AppLogger.d("[BRE_PROCESS:$processId] Starting BRE process for application: $applicationId")
        emit(Resource.Loading())

        try {
            var breInputResult: Resource<BREInput>? = null
            
            // Collect preparation result without throwing exceptions
            prepareBREInput(applicationId)
                .catch { e ->
                    AppLogger.e("[BRE_PROCESS:$processId] Error in preparation flow: ${e.message}", e)
                    breInputResult = Resource.Error("Error preparing BRE input: ${e.message}")
                }
                .collect { result ->
                    breInputResult = result
                    AppLogger.d("[BRE_PROCESS:$processId] BRE input preparation result: ${result.javaClass.simpleName}")
                }
            
            // Check if we got a successful result
            when (val result = breInputResult) {
                is Resource.Success -> {
                    val breInput = result.data
                    AppLogger.d("[BRE_PROCESS:$processId] BRE input prepared successfully")
                    
                    // Validate BRE input data
                    val validationResult = BREDataStateChecker.validateBREInput(breInput)
                    if (validationResult is BREDataStateChecker.ValidationResult.Invalid) {
                        val errorCode = BREErrorCodes.INVALID_INPUT_DATA
                        val errorMsg = "BRE input validation failed: ${validationResult.errors}"
                        AppLogger.e("[BRE_PROCESS:$processId] $errorCode: $errorMsg")
                        emit(Resource.Error("$errorCode: $errorMsg"))
                        return@flow
                    }

                    // Submit the BRE input
                    var submitResult: Resource<String>? = null
                    submitBREInput(breInput)
                        .catch { e ->
                            AppLogger.e("[BRE_PROCESS:$processId] Error in submit flow: ${e.message}", e)
                            submitResult = Resource.Error("Error submitting BRE input: ${e.message}")
                        }
                        .collect { submitRes ->
                            submitResult = submitRes
                            AppLogger.d("[BRE_PROCESS:$processId] BRE submit result: ${submitRes.javaClass.simpleName}")
                        }
                    
                    // Handle the final result
                    when (val submitRes = submitResult) {
                        is Resource.Success -> {
                            val breInputId = submitRes.data
                            AppLogger.d("[BRE_PROCESS:$processId] BRE process started successfully (ID: $breInputId)")
                            emit(Resource.Success(breInputId))
                        }
                        is Resource.Error -> {
                            val errorCode = getAPIErrorCode(submitRes.message)
                            AppLogger.e("[BRE_PROCESS:$processId] $errorCode: ${submitRes.message}")
                            emit(Resource.Error("$errorCode: ${submitRes.message}"))
                        }
                        is Resource.Loading,
                        null -> {
                            emit(Resource.Error("BRE_001: No result from submit flow"))
                        }
                    }
                }
                is Resource.Error -> {
                    val errorCode = BREErrorCodes.FLOW_EXCEPTION
                    AppLogger.e("[BRE_PROCESS:$processId] $errorCode: ${result.message}")
                    emit(Resource.Error("$errorCode: ${result.message}"))
                }
                is Resource.Loading,
                null -> {
                    emit(Resource.Error("BRE_001: No result from preparation flow"))
                }
            }
        } catch (e: Exception) {
            val errorCode = BREErrorCodes.FLOW_EXCEPTION
            val errorMsg = "Error starting BRE process: ${e.message}"
            AppLogger.e("[BRE_PROCESS:$processId] $errorCode: $errorMsg", e)
            emit(Resource.Error("$errorCode: $errorMsg"))
        }
    }

    /**
    * Prepare BRE input data with comprehensive data tracking and error handling
    */
    override suspend fun prepareBREInput(applicationId: String): Flow<Resource<BREInput>> = flow {
        val prepId = UUID.randomUUID().toString().substring(0, 8)
        AppLogger.d("[BRE_PREP:$prepId] Preparing input for application: $applicationId")
        emit(Resource.Loading())

        try {
            // Get application data
            val applicationDocument = firestore.collection("applications")
                .document(applicationId)
                .get()
                .await()

            if (!applicationDocument.exists()) {
                emit(Resource.Error("BRE_101: Application not found in Firestore: $applicationId"))
                return@flow
            }

            // Parse application data
            val applicationData = parseApplicationDocument(applicationDocument)
            if (applicationData == null) {
                emit(Resource.Error("BRE_101: Failed to parse application data"))
                return@flow
            }

            // Get recalculated obligation - don't fail if not found
            // Get recalculated obligation DIRECTLY from the fetched application data's BRE input snapshot
            val breInputMap = applicationDocument.get("breInputData") as? Map<String, Any>
            val llmRecalculatedObligation = (breInputMap?.get("llmRecalculatedObligation") as? Number)?.toInt()

            val monthlyEmi = if (llmRecalculatedObligation != null) {
                AppLogger.d("[BRE_PREP:$prepId] Using llmRecalculatedObligation from breInputData: $llmRecalculatedObligation")
                llmRecalculatedObligation
            } else {
                val declaredEmi = applicationData.employmentDetails?.monthlyEmi?.toInt() ?: 0
                AppLogger.d("[BRE_PREP:$prepId] No llmRecalculatedObligation in breInputData, using declared EMI: $declaredEmi")
                declaredEmi
            }

            // Extract bureau data
            val bureauData = extractBureauData(applicationData)
            AppLogger.d("[BRE_PREP:$prepId] Extracted bureau data: creditScore=${bureauData?.creditScore}")

            // Get document extractions
            val documentExtractions = try {
                fetchDocumentExtractions(applicationId)
            } catch (e: Exception) {
                AppLogger.w("[BRE_PREP:$prepId] Error fetching document extractions: ${e.message}")
                null
            }

            // Create BRE input
            val breInput = BREInput(
                id = UUID.randomUUID().toString(),
                applicationId = applicationId,
                timestamp = LocalDateTime.now(),
                userId = applicationData.userId,
                version = "1.5.0",
                panDetails = applicationData.panDetails,
                personalInfo = applicationData.personalInfo,
                employmentDetails = applicationData.employmentDetails?.copy(monthlyEmi = monthlyEmi.toDouble()),
                bureauData = bureauData,
                documentExtractions = documentExtractions,
                llmRecalculatedObligation = llmRecalculatedObligation,
                additionalData = emptyMap()
            )

            // Save to Firestore
            withContext(Dispatchers.IO) {
                try {
                    saveBREInput(breInput)
                    AppLogger.d("[BRE_PREP:$prepId] Successfully saved input to Firestore")
                } catch (e: Exception) {
                    AppLogger.e("[BRE_PREP:$prepId] Error saving to Firestore: ${e.message}", e)
                    // Continue anyway - this is for audit purposes
                }
            }

            emit(Resource.Success(breInput))
        } catch (e: Exception) {
            AppLogger.e("Error preparing BRE input: ${e.message}", e)
            emit(Resource.Error("BRE_001: Error preparing BRE input: ${e.message}"))
        }
    }


    /**
     * Submit BRE input and trigger the BRE process directly via API
     */
    override suspend fun submitBREInput(
        breInput: BREInput
    ): Flow<Resource<String>> = flow {
        emit(Resource.Loading())

        try {
            AppLogger.d("BRE: submitBREInput started for application: ${breInput.applicationId}")
            // Convert BRE input to a map for the API
            val inputMap = mapBREInputToRequestMap(breInput)

            // Log the request details
            AppLogger.d("BRE: Calling API for application: ${breInput.applicationId}")
            AppLogger.d("BRE: Input map contents: ${inputMap.toString().take(500)}...") // Log truncated
            AppLogger.d("BRE: Input map contains ${inputMap.keys.size} keys: ${inputMap.keys}")

            // Make direct API call to BRE worker with improved error handling
            try {
                // Test health check first
                AppLogger.d("[DEBUG] START - BRE: Testing health check endpoint")
                AppLogger.d("[DEBUG] Current thread: ${Thread.currentThread().name}")
                try {
                    AppLogger.d("[DEBUG] About to call breApi.healthCheck()")
                    val healthResponse = breApi.healthCheck()
                    AppLogger.d("[DEBUG] Health check response received")
                    AppLogger.d("[DEBUG] Response code: ${healthResponse.code()}")
                    AppLogger.d("[DEBUG] Response body: ${healthResponse.body()?.toString()}")
                } catch (healthError: Exception) {
                    AppLogger.e("[DEBUG] Health check failed - EXCEPTION")
                    AppLogger.e("[DEBUG] Exception type: ${healthError.javaClass.simpleName}")
                    AppLogger.e("[DEBUG] Exception message: ${healthError.message}")
                    healthError.printStackTrace()
                }
                AppLogger.d("[DEBUG] END - BRE: Testing health check endpoint")

                // Make the main api call now.
                AppLogger.d("BRE: About to call breApi.calculateOffer")
                val response = breApi.calculateOffer(inputMap)
                AppLogger.d("BRE: API call completed")
                AppLogger.d("BRE: Raw API response: isSuccessful=${response.isSuccessful}, code=${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val breOutputMap = response.body()!!
                    AppLogger.d("BRE: API response - decision=${breOutputMap["decisionStatus"]}, amount=${breOutputMap["approvedLoanAmount"]}")

                    // Make Firestore saving optional - don't let it block the result
                    try {
                        // Save the BRE output to Firestore
                        saveBREOutput(breInput.applicationId, breOutputMap)

                        // If the decision is AUTO_APPROVED, also save a loan offer
                        if (breOutputMap["decisionStatus"] == "AUTO_APPROVED") {
                            saveLoanOffer(breInput.applicationId, breOutputMap)
                        }
                    } catch (fsException: Exception) {
                        AppLogger.w("BRE: Non-critical error saving results to Firestore: ${fsException.message}")
                        // Continue anyway - we have the API response
                    }

                    emit(Resource.Success(breInput.id)) // Emit the original BREInput ID on success
                } else {
                    // Log more details about the error
                    val errorBody = try { response.errorBody()?.string() } catch (e: Exception) { "Error reading error body" } ?: "No error body"
                    val errorMessage = "BRE API call failed: code=${response.code()}, message=${response.message()}, errorBody=${errorBody.take(500)}" // Limit error body size
                    AppLogger.e(errorMessage)
                    emit(Resource.Error(errorMessage))
                }
            } catch (networkException: Exception) {
                val errorMessage = "BRE API network error: ${networkException.message}" // Use networkException.message
                AppLogger.e(errorMessage, networkException)
                emit(Resource.Error(errorMessage))
            }
        } catch (e: Exception) {
            val errorMessage = "Error submitting BRE input: ${e.message}" // Use e.message
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Convert BREInput to request map for API
     */
    private fun mapBREInputToRequestMap(breInput: BREInput): Map<String, @JvmSuppressWildcards Any> {
        val requestMap = mutableMapOf<String, Any>()
        
        requestMap["applicationId"] = breInput.applicationId
        requestMap["timestamp"] = breInput.timestamp.toString()
        requestMap["userId"] = breInput.userId
        requestMap["version"] = breInput.version
        
        // Personal info
        breInput.personalInfo?.let { info ->
            val personalInfoMap = mutableMapOf<String, Any>()
            info.dateOfBirth?.let { personalInfoMap["dateOfBirth"] = it.toString() }
            personalInfoMap["name"] = info.name
            info.email?.let { personalInfoMap["email"] = it }
            info.alternatePhoneNumber?.let { personalInfoMap["phone"] = it }
            
            val addressMap = mapOf(
                "state" to info.address.state,
                "city" to info.address.city,
                "postalCode" to info.address.postalCode,
                "addressLine1" to info.address.addressLine1,
                "addressLine2" to info.address.addressLine2,
                "country" to info.address.country
            )
            personalInfoMap["address"] = addressMap
            requestMap["personalInfo"] = personalInfoMap
        }
        
        // Employment details
        breInput.employmentDetails?.let { emp ->
            val employmentDetailsMap = mutableMapOf<String, Any>()
            employmentDetailsMap["employmentType"] = emp.employmentType.name
            employmentDetailsMap["monthlySalary"] = emp.monthlySalary
            employmentDetailsMap["monthlyEmi"] = emp.monthlyEmi ?: 0
            employmentDetailsMap["employerName"] = emp.employerName
            emp.workEmail?.let { employmentDetailsMap["workEmail"] = it }
            emp.department?.let { employmentDetailsMap["department"] = it }
            emp.designation?.let { employmentDetailsMap["designation"] = it }
            requestMap["employmentDetails"] = employmentDetailsMap
        }
        
        // Bureau data
        breInput.bureauData?.let { bureau ->
            val bureauDataMap = mutableMapOf<String, Any>()
            bureauDataMap["creditScore"] = bureau.creditScore
            bureauDataMap["writtenOffStatus"] = bureau.writtenOffStatus
            bureauDataMap["suitFiled"] = bureau.suitFiled
            bureau.delinquencyStatus?.let { bureauDataMap["delinquencyStatus"] = it }
            bureauDataMap["openAccounts"] = bureau.openAccounts
            bureauDataMap["totalAccounts"] = bureau.totalAccounts
            bureauDataMap["currentBalance"] = bureau.currentBalance
            bureauDataMap["totalLoanAmount"] = bureau.totalLoanAmount
            bureauDataMap["totalOverdueAmount"] = bureau.totalOverdueAmount
            requestMap["bureauData"] = bureauDataMap
        }
        
        // PAN details
        breInput.panDetails?.let { pan ->
            val panDetailsMap = mutableMapOf<String, Any>()
            panDetailsMap["panNumber"] = pan.panNumber
            panDetailsMap["name"] = pan.name
            pan.dateOfBirth?.let { panDetailsMap["dateOfBirth"] = it.toString() }
            panDetailsMap["isVerified"] = pan.isVerified
            pan.verificationDate?.let { panDetailsMap["verificationDate"] = it.toString() }
            requestMap["panDetails"] = panDetailsMap
        }
        
        // Document extractions
        breInput.documentExtractions?.let { docs ->
            val documentExtractionsMap = mutableMapOf<String, Any>()
            
            docs.salarySlip?.let { salary ->
                val salarySlipMap = mutableMapOf<String, Any>()
                salary.employerName?.let { salarySlipMap["employerName"] = it }
                salary.netSalary?.let { salarySlipMap["netSalary"] = it }
                salary.grossSalary?.let { salarySlipMap["grossSalary"] = it }
                salary.employeeName?.let { salarySlipMap["employeeName"] = it }
                salary.employeeId?.let { salarySlipMap["employeeId"] = it }
                documentExtractionsMap["salarySlip"] = salarySlipMap
            }
            
            docs.bankStatement?.let { bank ->
                val bankStatementMap = mutableMapOf<String, Any>()
                bank.bankName?.let { bankStatementMap["bankName"] = it }
                bank.accountNumber?.let { bankStatementMap["accountNumber"] = it }
                bank.totalCredits?.let { bankStatementMap["totalCredits"] = it }
                bank.accountHolderName?.let { bankStatementMap["accountHolderName"] = it }
                documentExtractionsMap["bankStatement"] = bankStatementMap
            }
            
            docs.incomeTaxReturn?.let { itr ->
                val itrMap = mutableMapOf<String, Any>()
                itr.panNumber?.let { itrMap["panNumber"] = it }
                itr.totalGrossIncome?.let { itrMap["totalGrossIncome"] = it }
                itr.taxableIncome?.let { itrMap["taxableIncome"] = it }
                itr.taxPaid?.let { itrMap["taxPaid"] = it }
                documentExtractionsMap["incomeTaxReturn"] = itrMap
            }
            
            requestMap["documentExtractions"] = documentExtractionsMap
        }
        
        // LLM recalculated obligation
        breInput.llmRecalculatedObligation?.let { requestMap["llmRecalculatedObligation"] = it }
        
        return requestMap
    }

    /**
     * Save BRE input to Firestore
     */
    private suspend fun saveBREInput(breInput: BREInput) {
        try {
            AppLogger.d("BRE: Saving BRE input with ID: ${breInput.id}")

            // Convert BRE input to a map for Firestore
            val breInputData = mapOf(
                "id" to breInput.id,
                "applicationId" to breInput.applicationId,
                "timestamp" to DateConverter.toTimestamp(breInput.timestamp),
                "userId" to breInput.userId,
                "version" to breInput.version,
                "llmRecalculatedObligation" to breInput.llmRecalculatedObligation,
                "personalInfo" to breInput.personalInfo?.let { info ->
                    mapOf(
                        "name" to info.name,
                        "dateOfBirth" to info.dateOfBirth?.toString(),
                        "email" to info.email,
                        "gender" to info.gender?.name,
                        "maritalStatus" to info.maritalStatus?.name,
                        "address" to info.address.let { addr ->
                            mapOf(
                                "addressLine1" to addr.addressLine1,
                                "addressLine2" to addr.addressLine2,
                                "city" to addr.city,
                                "state" to addr.state,
                                "postalCode" to addr.postalCode,
                                "country" to addr.country,
                                "addressType" to addr.addressType.name
                            )
                        },
                        "alternatePhoneNumber" to info.alternatePhoneNumber
                    )
                },
                "employmentDetails" to breInput.employmentDetails?.let { emp ->
                    mapOf(
                        "employmentType" to emp.employmentType.name,
                        "employerName" to emp.employerName,
                        "employerId" to emp.employerId,
                        "designation" to emp.designation,
                        "department" to emp.department,
                        "employeeId" to emp.employeeId,
                        "workEmail" to emp.workEmail,
                        "monthlySalary" to emp.monthlySalary,
                        "monthlyEmi" to emp.monthlyEmi,
                        "officeAddress" to emp.officeAddress?.let { addr ->
                            mapOf(
                                "addressLine1" to addr.addressLine1,
                                "addressLine2" to addr.addressLine2,
                                "city" to addr.city,
                                "state" to addr.state,
                                "postalCode" to addr.postalCode,
                                "country" to addr.country,
                                "addressType" to addr.addressType.name
                            )
                        },
                        "isVerified" to emp.isVerified,
                        "verificationMethod" to emp.verificationMethod?.name
                    )
                },
                "bureauData" to breInput.bureauData?.let { bureau ->
                    mapOf(
                        "panNumber" to bureau.panNumber,
                        "fetchedAt" to DateConverter.toTimestamp(bureau.fetchedAt),
                        "bureauType" to bureau.bureauType,
                        "reportDate" to DateConverter.toTimestamp(bureau.reportDate),
                        "customerName" to bureau.customerName,
                        "creditScore" to bureau.creditScore,
                        "totalAccounts" to bureau.totalAccounts,
                        "openAccounts" to bureau.openAccounts,
                        "totalLoanAmount" to bureau.totalLoanAmount,
                        "currentBalance" to bureau.currentBalance,
                        "totalOverdueAmount" to bureau.totalOverdueAmount,
                        "delinquencyStatus" to bureau.delinquencyStatus,
                        "suitFiled" to bureau.suitFiled,
                        "wilfulDefault" to bureau.wilfulDefault,
                        "writtenOffStatus" to bureau.writtenOffStatus
                    )
                },
                "documentExtractions" to breInput.documentExtractions?.let { docs ->
                    mapOf(
                        "salarySlip" to docs.salarySlip?.let { salary ->
                            mapOf(
                                "employerName" to salary.employerName,
                                "employeeName" to salary.employeeName,
                                "employeeId" to salary.employeeId,
                                "salaryMonth" to salary.salaryMonth,
                                "salaryYear" to salary.salaryYear,
                                "basicSalary" to salary.basicSalary,
                                "grossSalary" to salary.grossSalary,
                                "netSalary" to salary.netSalary
                            )
                        },
                        "bankStatement" to docs.bankStatement?.let { bank ->
                            mapOf(
                                "bankName" to bank.bankName,
                                "accountNumber" to bank.accountNumber,
                                "accountHolderName" to bank.accountHolderName,
                                "statementPeriodStart" to bank.statementPeriodStart?.let { DateConverter.toTimestamp(it) },
                                "statementPeriodEnd" to bank.statementPeriodEnd?.let { DateConverter.toTimestamp(it) },
                                "openingBalance" to bank.openingBalance,
                                "closingBalance" to bank.closingBalance,
                                "averageBalance" to bank.averageBalance,
                                "totalCredits" to bank.totalCredits,
                                "totalDebits" to bank.totalDebits,
                                "transactionsCount" to bank.transactionsCount
                            )
                        },
                        "incomeTaxReturn" to docs.incomeTaxReturn?.let { itr ->
                            mapOf(
                                "itrType" to itr.itrType,
                                "assessmentYear" to itr.assessmentYear,
                                "panNumber" to itr.panNumber,
                                "name" to itr.name,
                                "totalGrossIncome" to itr.totalGrossIncome,
                                "taxableIncome" to itr.taxableIncome,
                                "taxPaid" to itr.taxPaid
                            )
                        }
                    )
                },
                "additionalData" to breInput.additionalData
            )

            // Also update the application document with breInputData
            val applicationUpdateData = mapOf(
                "breInputData" to breInputData,
                "lastUpdatedAt" to DateConverter.toTimestamp(LocalDateTime.now())
            )

            // Save to Firestore collection using batch write for atomicity
            val batch = firestore.batch()
            
            // Add to bre_input collection
            val breInputRef = firestore.collection("bre_input").document(breInput.id)
            batch.set(breInputRef, breInputData)

            // Update application document
            val applicationRef = firestore.collection("applications").document(breInput.applicationId)
            batch.set(applicationRef, applicationUpdateData, SetOptions.merge())

            // Execute batch
            batch.commit().await()

            AppLogger.d("BRE: Successfully saved to bre_input collection and updated application")

            // If recalculated obligation was included, log this
            if (breInput.llmRecalculatedObligation != null) {
                AppLogger.d("BRE: Input includes llmRecalculatedObligation: ${breInput.llmRecalculatedObligation}")
            }
        } catch (e: Exception) {
            AppLogger.e("BRE: Error saving BRE input: ${e.message}", e)
            // Don't throw - this is just for audit purposes
        }
    }

    /**
     * Save BRE output to Firestore
     */
    private suspend fun saveBREOutput(applicationId: String, breOutputMap: Map<String, Any?>) {
        try {
            // Update application document with BRE output
            firestore.collection("applications")
                .document(applicationId)
                .update(
                    "breOutputData", breOutputMap,
                    "lastUpdatedAt", FieldValue.serverTimestamp()
                )
                .await()

            // Also save to bre_output collection for historical record
            val breOutputId = UUID.randomUUID().toString()
            firestore.collection("bre_output")
                .document(breOutputId)
                .set(
                    breOutputMap + mapOf(
                        "id" to breOutputId,
                        "applicationId" to applicationId,
                        "timestamp" to FieldValue.serverTimestamp()
                    )
                )
                .await()

            AppLogger.d("Saved BRE output to Firestore for application: $applicationId")
        } catch (e: Exception) {
            AppLogger.e("Error saving BRE output to Firestore: ${e.message}", e)
            // Continue without failing - this is just for recording
        }
    }

    /**
     * Save loan offer to Firestore
     */
    private suspend fun saveLoanOffer(applicationId: String, breOutputMap: Map<String, Any?>) {
        try {
            // Create a loan offer from BRE output
            val offerId = "offer-${applicationId}-${System.currentTimeMillis()}"

            val approvedAmount = breOutputMap["approvedLoanAmount"] as? Number ?: 0.0
            val minAmount = breOutputMap["minLoanAmount"] as? Number ?: 0.0
            val maxAmount = breOutputMap["maxLoanAmount"] as? Number ?: 0.0
            val interestRate = breOutputMap["interestRate"] as? Number ?: 14.0
            val processingFeePercentage = breOutputMap["processingFeePercentage"] as? Number ?: 1.0
            val minTenor = breOutputMap["minTenure"] as? Number ?: 12
            val maxTenor = breOutputMap["maxTenure"] as? Number ?: 60

            val loanOfferMap = mapOf(
                "offerId" to offerId,
                "applicationId" to applicationId,
                "generatedAt" to FieldValue.serverTimestamp(),
                "expiresAt" to DateConverter.toTimestamp(LocalDateTime.now().plusDays(7)),
                "offerStatus" to "GENERATED",
                "approvedLoanAmount" to approvedAmount.toDouble(),
                "minLoanAmount" to minAmount.toDouble(),
                "maxLoanAmount" to maxAmount.toDouble(),
                "minTenure" to minTenor.toInt(),
                "maxTenure" to maxTenor.toInt(),
                "interestRate" to interestRate.toDouble(),
                "processingFeePercentage" to processingFeePercentage.toDouble(),
                "processingFeeAmount" to (approvedAmount.toDouble() * processingFeePercentage.toDouble() / 100.0)
            )

            // Save loan offer to both application and separate collection
            firestore.collection("applications")
                .document(applicationId)
                .update("loanOffer", loanOfferMap)
                .await()

            firestore.collection("loan_offers")
                .document(offerId)
                .set(loanOfferMap)
                .await()

            AppLogger.d("Saved loan offer to Firestore for application: $applicationId")
        } catch (e: Exception) {
            AppLogger.e("Error saving loan offer to Firestore: ${e.message}", e)
            // Continue without failing
        }
    }


    /**
    * Parse application document from Firestore
    */
    private fun parseApplicationDocument(document: DocumentSnapshot): LoanApplication? {
        try {
            AppLogger.d("BRE: Parsing application document: ${document.id}")
            
            // Parse application data manually instead of using toObject()
            val id = document.getString("id") ?: document.id
            val userId = document.getString("userId") ?: ""
            val createdAt = DateConverter.parseFirestoreValue(document.get("createdAt")) ?: LocalDateTime.now()
            val lastUpdatedAt = DateConverter.parseFirestoreValue(document.get("lastUpdatedAt")) ?: LocalDateTime.now()
            
            val applicationStatus = try {
                ApplicationStatus.valueOf(document.getString("applicationStatus") ?: "CREATED")
            } catch (e: Exception) { ApplicationStatus.CREATED }
            
            val currentStep = try {
                ApplicationStep.valueOf(document.getString("currentStep") ?: "PAN_VERIFICATION")
            } catch (e: Exception) { ApplicationStep.PAN_VERIFICATION }
            
            val completedSteps = (document.get("completedSteps") as? List<*>)
                ?.mapNotNull { step -> (step as? String)?.let { try { ApplicationStep.valueOf(it) } catch (e: Exception) { null } } }
                ?: emptyList()
            
            // Parse personal info
            val personalInfoMap = document.get("personalInfo") as? Map<String, Any?>
            val personalInfo = personalInfoMap?.let { parsePersonalInfo(it) }
            
            // Parse employment details
            val employmentDetailsMap = document.get("employmentDetails") as? Map<String, Any?>
            val employmentDetails = employmentDetailsMap?.let { parseEmploymentDetails(it) }
            
            // Parse PAN details
            val panDetailsMap = document.get("panDetails") as? Map<String, Any?>
            val panDetails = panDetailsMap?.let { parsePANDetails(it) }
            
            // Get PAN number from root level if available
            val panNumber = document.getString("panNumber") ?: panDetails?.panNumber
            
            // Extract all available data to additionalData
            val additionalData = mutableMapOf<String, Any>()
            
            // Add root level bureau fields - these have the correct values
            document.getLong("bureauScore")?.let { score ->
                additionalData["bureauScore"] = score
                AppLogger.d("BRE: Added bureauScore to additionalData: $score")
            }
            document.getLong("bureauTotalAccounts")?.let { total ->
                additionalData["bureauTotalAccounts"] = total
                AppLogger.d("BRE: Added bureauTotalAccounts to additionalData: $total")
            }
            document.getLong("bureauCurrentBalance")?.let { balance ->
                additionalData["bureauCurrentBalance"] = balance
                AppLogger.d("BRE: Added bureauCurrentBalance to additionalData: $balance")
            }
            document.getLong("bureauOpenAccounts")?.let { open ->
                additionalData["bureauOpenAccounts"] = open
                AppLogger.d("BRE: Added bureauOpenAccounts to additionalData: $open")
            }
            
            // Add bureauData object but only if we don't already have better data from root
            document.get("bureauData")?.let { bureauData ->
                if (additionalData["bureauScore"] == null) {
                    additionalData["bureauData"] = bureauData
                    AppLogger.d("BRE: Added bureauData object to additionalData")
                } else {
                    AppLogger.d("BRE: Skipping bureauData object since we have root level data")
                }
            }
            
            return LoanApplication(
                id = id,
                userId = userId,
                createdAt = createdAt,
                lastUpdatedAt = lastUpdatedAt,
                applicationStatus = applicationStatus,
                panNumber = panNumber,
                panDetails = panDetails,
                personalInfo = personalInfo,
                employmentDetails = employmentDetails,
                documents = emptyList(),
                documentIds = (document.get("documentIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                loanOffer = null,
                currentStep = currentStep,
                completedSteps = completedSteps,
                additionalData = additionalData
            )
        } catch (e: Exception) {
            AppLogger.e("BRE: Error parsing application document: ${e.message}", e)
            return null
        }
    }

    private fun parsePersonalInfo(map: Map<String, Any?>): PersonalInfo? {
        return try {
            val addressMap = map["address"] as? Map<String, Any?>
            val address = addressMap?.let { addr ->
                Address(
                    addressLine1 = addr["addressLine1"] as? String ?: "",
                    addressLine2 = addr["addressLine2"] as? String,
                    city = addr["city"] as? String ?: "",
                    state = addr["state"] as? String ?: "",
                    postalCode = addr["postalCode"] as? String ?: "",
                    country = addr["country"] as? String ?: "India",
                    addressType = try { 
                        (addr["addressType"] as? String)?.let { AddressType.valueOf(it) } ?: AddressType.CURRENT 
                    } catch (e: Exception) { AddressType.CURRENT }
                )
            } ?: Address("", null, "", "", "", "India", AddressType.CURRENT)
            
            PersonalInfo(
                name = map["name"] as? String ?: "",
                dateOfBirth = (map["dateOfBirth"] as? String)?.let { dobString ->
                    try { LocalDate.parse(dobString) } catch (e: Exception) { null }
                },
                email = map["email"] as? String ?: "",
                gender = try { (map["gender"] as? String)?.let { Gender.valueOf(it) } } catch(e: Exception) { null },
                maritalStatus = try { (map["maritalStatus"] as? String)?.let { MaritalStatus.valueOf(it) } } catch(e: Exception) { null },
                address = address,
                alternatePhoneNumber = map["alternatePhoneNumber"] as? String
            )
        } catch (e: Exception) {
            AppLogger.e("BRE: Error parsing personalInfo: ${e.message}", e)
            null
        }
    }

    private fun parseEmploymentDetails(map: Map<String, Any?>): EmploymentDetails? {
        return try {
            val officeAddressMap = map["officeAddress"] as? Map<String, Any?>
            val officeAddress = officeAddressMap?.let { addr ->
                Address(
                    addressLine1 = addr["addressLine1"] as? String ?: "",
                    addressLine2 = addr["addressLine2"] as? String,
                    city = addr["city"] as? String ?: "",
                    state = addr["state"] as? String ?: "",
                    postalCode = addr["postalCode"] as? String ?: "",
                    country = addr["country"] as? String ?: "India",
                    addressType = AddressType.OFFICE
                )
            }
            
            EmploymentDetails(
                employmentType = try { 
                    (map["employmentType"] as? String)?.let { EmploymentType.valueOf(it) } ?: EmploymentType.OTHER 
                } catch(e: Exception) { EmploymentType.OTHER },
                employerName = map["employerName"] as? String ?: "",
                employerId = map["employerId"] as? String,
                designation = map["designation"] as? String,
                department = map["department"] as? String,
                employeeId = map["employeeId"] as? String,
                workEmail = map["workEmail"] as? String,
                monthlySalary = (map["monthlySalary"] as? Number)?.toDouble() ?: 0.0,
                monthlyEmi = (map["monthlyEmi"] as? Number)?.toDouble(),
                officeAddress = officeAddress,
                isVerified = map["isVerified"] as? Boolean ?: false,
                verificationMethod = try { 
                    (map["verificationMethod"] as? String)?.let { VerificationMethod.valueOf(it) } 
                } catch (e: Exception) { null }
            )
        } catch (e: Exception) {
            AppLogger.e("BRE: Error parsing employmentDetails: ${e.message}", e)
            null
        }
    }

    private fun parsePANDetails(map: Map<String, Any?>): PANDetails? {
        return try {
            PANDetails(
                panNumber = map["panNumber"] as? String ?: "",
                name = map["name"] as? String ?: "",
                fatherName = map["fatherName"] as? String,
                dateOfBirth = (map["dateOfBirth"] as? String)?.let {
                    try { LocalDate.parse(it) } catch(e: Exception) { null }
                },
                isVerified = (map["isVerified"] as? Boolean) ?: false,
                verificationDate = (map["verificationDate"] as? String)?.let {
                    try { LocalDate.parse(it) } catch(e: Exception) { null }
                }
            )
        } catch (e: Exception) {
            AppLogger.e("BRE: Error parsing panDetails: ${e.message}", e)
            null
        }
    }

    /**
     * Extract bureau data from loan application with proper field mapping
     */
    private fun extractBureauData(application: LoanApplication): BureauData? {
        AppLogger.d("Extracting bureau data from application: ${application.id}")
        
        var creditScore = 0
        var currentBalance = 0.0
        var totalAccounts = 0
        var openAccounts = 0
        
        // First check root level fields (these seem to have the correct values)
        application.additionalData?.get("bureauScore")?.let { 
            creditScore = when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull() ?: 0
                else -> 0
            }
        }
        
        application.additionalData?.get("bureauCurrentBalance")?.let {
            currentBalance = when (it) {
                is Number -> it.toDouble()
                is String -> it.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
        
        application.additionalData?.get("bureauTotalAccounts")?.let {
            totalAccounts = when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull() ?: 0
                else -> 0
            }
        }
        
        application.additionalData?.get("bureauOpenAccounts")?.let {
            openAccounts = when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull() ?: 0
                else -> 0
            }
        }
        
        // If we found data from root level fields, use them
        if (creditScore > 0) {
            AppLogger.d("Using bureau data from root level fields: score=$creditScore")
            return BureauData(
                panNumber = application.panNumber ?: "",
                fetchedAt = LocalDateTime.now(),
                bureauType = "CIBIL",
                reportDate = LocalDateTime.now(),
                customerName = application.personalInfo?.name ?: "",
                creditScore = creditScore,
                totalAccounts = totalAccounts,
                openAccounts = openAccounts,
                totalLoanAmount = 0.0,
                currentBalance = currentBalance,
                totalOverdueAmount = 0.0,
                delinquencyStatus = null,
                suitFiled = false,
                wilfulDefault = false,
                writtenOffStatus = false
            )
        }
        
        // Then check bureauData object in additionalData
        val bureauDataMap = application.additionalData?.get("bureauData") as? Map<String, Any>
        if (bureauDataMap != null) {
            val bureauCreditScore = (bureauDataMap.get("creditScore") as? Number)?.toInt() ?: 0
            if (bureauCreditScore > 0) {
                AppLogger.d("Using bureau data from bureauData object: score=$bureauCreditScore")
                return BureauData(
                    panNumber = application.panNumber ?: "",
                    fetchedAt = LocalDateTime.now(),
                    bureauType = "CIBIL",
                    reportDate = LocalDateTime.now(),
                    customerName = application.personalInfo?.name ?: "",
                    creditScore = bureauCreditScore,
                    totalAccounts = (bureauDataMap.get("totalAccounts") as? Number)?.toInt() ?: 0,
                    openAccounts = (bureauDataMap.get("openAccounts") as? Number)?.toInt() ?: 0,
                    totalLoanAmount = (bureauDataMap.get("totalLoanAmount") as? Number)?.toDouble() ?: 0.0,
                    currentBalance = (bureauDataMap.get("currentBalance") as? Number)?.toDouble() ?: 0.0,
                    totalOverdueAmount = (bureauDataMap.get("totalOverdueAmount") as? Number)?.toDouble() ?: 0.0,
                    delinquencyStatus = bureauDataMap.get("delinquencyStatus") as? String,
                    suitFiled = (bureauDataMap.get("suitFiled") as? Boolean) ?: false,
                    wilfulDefault = (bureauDataMap.get("wilfulDefault") as? Boolean) ?: false,
                    writtenOffStatus = (bureauDataMap.get("writtenOffStatus") as? Boolean) ?: false
                )
            }
        }
        
        // Fallback to bureauReport
        return application.bureauReport?.let { bureauReport ->
            AppLogger.d("Using bureau data from bureauReport: score=${bureauReport.creditScore}")
            BureauData(
                panNumber = bureauReport.panNumber,
                fetchedAt = bureauReport.createdAt,
                bureauType = bureauReport.bureauType?.name ?: "CIBIL",
                reportDate = bureauReport.createdAt,
                customerName = bureauReport.customerName ?: "",
                creditScore = bureauReport.creditScore ?: 0,
                totalAccounts = bureauReport.accountSummary?.totalAccounts ?: 0,
                openAccounts = bureauReport.accountSummary?.activeAccounts ?: 0,
                totalLoanAmount = bureauReport.accountSummary?.totalCreditLimit?.toDouble() ?: 0.0,
                currentBalance = bureauReport.accountSummary?.totalOutstanding?.toDouble() ?: 0.0,
                totalOverdueAmount = bureauReport.accountSummary?.totalOverdue?.toDouble() ?: 0.0,
                delinquencyStatus = bureauReport.delinquentStatus,
                suitFiled = bureauReport.hasExistingDefaultOrWriteOff || (bureauReport.accountSummary?.suitFiledAccountsCount ?: 0) > 0,
                wilfulDefault = bureauReport.hasExistingDefaultOrWriteOff,
                writtenOffStatus = bureauReport.totalWrittenOffAmount > 0 || (bureauReport.accountSummary?.writtenOffAccountsCount ?: 0) > 0
            )
        }
    }

    /**
     * Fetch document extractions for income verification
     */
    private suspend fun fetchDocumentExtractions(applicationId: String): DocumentExtractions? {
        try {
            // Get documents for this application
            val snapshot = firestore.collection("documents")
                .whereEqualTo("applicationId", applicationId)
                .get()
                .await()

            if (snapshot.isEmpty) {
                return null
            }

            // Extract data from documents
            var bankStatementData: BankStatementData? = null
            var salarySlipData: SalarySlipData? = null
            var incomeTaxReturnData: IncomeTaxReturnData? = null

            for (document in snapshot.documents) {
                val docData = document.data ?: continue
                val docType = docData["documentType"] as? String ?: continue
                val extractedData = docData["extractedData"] as? Map<String, Any> ?: continue

                when (docType) {
                    "BANK_STATEMENT" -> {
                        // Extract bank statement data
                        bankStatementData = BankStatementData(
                            bankName = extractedData["bankName"] as? String,
                            accountNumber = extractedData["accountNumber"] as? String,
                            accountHolderName = extractedData["accountHolderName"] as? String,
                            totalCredits = (extractedData["totalCredits"] as? Number)?.toDouble() ?: 0.0
                            // Add other fields as needed from your BankStatementData model
                        )
                    }
                    "SALARY_SLIP" -> {
                        // Extract salary slip data
                        salarySlipData = SalarySlipData(
                            employerName = extractedData["companyName"] as? String, // Key might be different
                            employeeName = extractedData["employeeName"] as? String,
                            netSalary = (extractedData["netSalary"] as? Number)?.toDouble() ?: 0.0,
                            grossSalary = (extractedData["grossSalary"] as? Number)?.toDouble() ?: 0.0
                            // Add other fields as needed
                        )
                    }
                    "INCOME_TAX_RETURN" -> {
                        // Extract ITR data
                        incomeTaxReturnData = IncomeTaxReturnData(
                            panNumber = extractedData["panNumber"] as? String,
                            name = extractedData["name"] as? String,
                            totalGrossIncome = (extractedData["grossTotalIncome"] as? Number)?.toDouble() ?: 0.0
                            // Add other fields as needed
                        )
                    }
                }
            }

            // Only create DocumentExtractions if we have at least one document with data
            if (bankStatementData != null || salarySlipData != null || incomeTaxReturnData != null) {
                return DocumentExtractions(
                    bankStatement = bankStatementData,
                    salarySlip = salarySlipData,
                    incomeTaxReturn = incomeTaxReturnData
                )
            }

            return null
        } catch (e: Exception) {
            AppLogger.e("Error fetching document extractions: ${e.message}", e)
            return null
        }
    }

    /**
     * Get the latest decision for an application
     */
    override suspend fun getLatestDecision(
        applicationId: String
    ): Flow<Resource<DecisionStatus>> = flow {
        emit(Resource.Loading())

        try {
            AppLogger.d("BRE: Getting latest decision for application: $applicationId")
            // Query Firestore directly for breOutputData in application
            val appDoc = firestore.collection("applications")
                .document(applicationId)
                .get()
                .await()

            AppLogger.d("BRE: Application document exists: ${appDoc.exists()}")
            if (appDoc.exists()) {
                val appData = appDoc.data
                AppLogger.d("BRE: Application data keys: ${appData?.keys}")
                val breOutputData = appDoc.get("breOutputData") as? Map<String, Any>
                AppLogger.d("BRE: breOutputData found: ${breOutputData != null}")
                AppLogger.d("BRE: breOutputData contents: $breOutputData")

                if (breOutputData != null) {
                    // Extract decision status
                    val statusStr = breOutputData["decisionStatus"] as? String ?: "PENDING"
                    val decisionStatus = try {
                        DecisionStatus.valueOf(statusStr)
                    } catch (e: Exception) {
                        DecisionStatus.PENDING
                    }

                    emit(Resource.Success(decisionStatus))
                    return@flow
                }
            }

            // If not found in application, check bre_output collection
            val snapshot = firestore.collection("bre_output")
                .whereEqualTo("applicationId", applicationId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                val document = snapshot.documents[0]

                // Extract the decision status
                val status = document.getString("decisionStatus") ?: "PENDING"
                val decisionStatus = try {
                    DecisionStatus.valueOf(status)
                } catch (e: Exception) {
                    DecisionStatus.PENDING
                }

                emit(Resource.Success(decisionStatus))
            } else {
                AppLogger.w("No decision found yet for application $applicationId, returning PENDING.")
                emit(Resource.Success(DecisionStatus.PENDING))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting latest decision: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Get the loan offer from BRE output
     */
    override suspend fun getLoanOffer(
        applicationId: String
    ): Flow<Resource<LoanOffer>> = flow {
        emit(Resource.Loading())

        try {
            // Query Firestore for the loan offer in the application document
            val appDoc = firestore.collection("applications")
                .document(applicationId)
                .get()
                .await()

            if (appDoc.exists()) {
                val loanOfferMap = appDoc.get("loanOffer") as? Map<String, Any>

                if (loanOfferMap != null) {
                    // Extract loan offer parameters
                    val offerId = loanOfferMap["offerId"] as? String ?: ""
                    val approvedAmount = (loanOfferMap["approvedLoanAmount"] as? Number)?.toDouble() ?: 0.0
                    val minAmount = (loanOfferMap["minLoanAmount"] as? Number)?.toDouble() ?: 0.0
                    val maxAmount = (loanOfferMap["maxLoanAmount"] as? Number)?.toDouble() ?: 0.0
                    val minTenure = (loanOfferMap["minTenure"] as? Number)?.toInt() ?: 12
                    val maxTenure = (loanOfferMap["maxTenure"] as? Number)?.toInt() ?: 60
                    val interestRate = (loanOfferMap["interestRate"] as? Number)?.toDouble() ?: 10.0
                    val processingFeePercentage = (loanOfferMap["processingFeePercentage"] as? Number)?.toDouble() ?: 1.0
                    val generatedAt = DateConverter.parseFirestoreValue(loanOfferMap["generatedAt"]) ?: LocalDateTime.now()
                    val expiresAt = DateConverter.parseFirestoreValue(loanOfferMap["expiresAt"])
                    val offerStatusStr = loanOfferMap["offerStatus"] as? String ?: "GENERATED"
                    val offerStatus = try { OfferStatus.valueOf(offerStatusStr) } catch (e: Exception) { OfferStatus.GENERATED }


                    // Create a loan offer
                    val loanOffer = LoanOffer(
                        applicationId = applicationId,
                        offerId = offerId,
                        generatedAt = generatedAt,
                        expiresAt = expiresAt,
                        approvedLoanAmount = approvedAmount,
                        minLoanAmount = minAmount,
                        maxLoanAmount = maxAmount,
                        minTenure = minTenure,
                        maxTenure = maxTenure,
                        interestRate = interestRate,
                        processingFeePercentage = processingFeePercentage,
                        processingFeeAmount = approvedAmount * processingFeePercentage / 100.0,
                        offerStatus = offerStatus
                    )

                    emit(Resource.Success(loanOffer))
                    return@flow
                }
            }

            // If not found in application, check loan_offers collection (might be redundant if app doc is source of truth)
            // Consider removing this fallback if 'applications.loanOffer' is always updated
            val snapshot = firestore.collection("loan_offers")
                .whereEqualTo("applicationId", applicationId)
                .orderBy("generatedAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (!snapshot.isEmpty) {
                 val document = snapshot.documents[0]
                 // Extract loan offer parameters from 'loan_offers' collection
                 val offerId = document.getString("offerId") ?: ""
                 val approvedAmount = (document.get("approvedLoanAmount") as? Number)?.toDouble() ?: 0.0
                 val minAmount = (document.get("minLoanAmount") as? Number)?.toDouble() ?: 0.0
                 val maxAmount = (document.get("maxLoanAmount") as? Number)?.toDouble() ?: 0.0
                 val minTenure = (document.get("minTenure") as? Number)?.toInt() ?: 12
                 val maxTenure = (document.get("maxTenure") as? Number)?.toInt() ?: 60
                 val interestRate = (document.get("interestRate") as? Number)?.toDouble() ?: 10.0
                 val processingFeePercentage = (document.get("processingFeePercentage") as? Number)?.toDouble() ?: 1.0
                 val generatedAt = DateConverter.parseFirestoreValue(document.get("generatedAt")) ?: LocalDateTime.now()
                 val expiresAt = DateConverter.parseFirestoreValue(document.get("expiresAt"))
                 val offerStatusStr = document.getString("offerStatus") ?: "GENERATED"
                 val offerStatus = try { OfferStatus.valueOf(offerStatusStr) } catch (e: Exception) { OfferStatus.GENERATED }

                // Create a loan offer
                val loanOffer = LoanOffer(
                    applicationId = applicationId,
                    offerId = offerId,
                    generatedAt = generatedAt,
                    expiresAt = expiresAt,
                    approvedLoanAmount = approvedAmount,
                    minLoanAmount = minAmount,
                    maxLoanAmount = maxAmount,
                    minTenure = minTenure,
                    maxTenure = maxTenure,
                    interestRate = interestRate,
                    processingFeePercentage = processingFeePercentage,
                    processingFeeAmount = approvedAmount * processingFeePercentage / 100.0,
                    offerStatus = offerStatus
                )
                emit(Resource.Success(loanOffer))
            } else {
                emit(Resource.Error("No loan offer found for application: $applicationId"))
            }
        } catch (e: Exception) {
            val errorMessage = "Error getting loan offer: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }

    /**
     * Update the loan application with BRE decision
     */
    override suspend fun updateApplicationWithDecision(
        applicationId: String
    ): Flow<Resource<LoanApplication>> = flow {
        emit(Resource.Loading())

        try {
            // Get BRE output data (as before)
            val appDoc = firestore.collection("applications")
                .document(applicationId)
                .get()
                .await()

            if (appDoc.exists()) {
                 val breOutputData = appDoc.get("breOutputData") as? Map<String, Any>
                if (breOutputData != null) {
                    // Extract decision status (as before)
                     val statusStr = breOutputData["decisionStatus"] as? String ?: "PENDING"
                     val decisionStatus = try { DecisionStatus.valueOf(statusStr) } catch (e: Exception) { DecisionStatus.PENDING }

                    // Get the current application (use firstOrNull and check result)
                     val applicationResource = loanRepository.getApplication(applicationId)
                         .filterNot { it is Resource.Loading }
                         .firstOrNull() // Use firstOrNull

                    if (applicationResource is Resource.Success) {
                        val application = applicationResource.data
                        // Extract reasons (as before)
                         val rejectionReasons = breOutputData["rejectionReasons"]?.toString()
                         val referralReasons = breOutputData["referralReasons"]?.toString()
                         val decisionReason = rejectionReasons ?: referralReasons

                        // Update the application with decision (as before)
                         val updatedApplication = application.copy(
                             decisionStatus = decisionStatus,
                             decisionReason = decisionReason,
                             lastUpdatedAt = LocalDateTime.now()
                         )

                        // Use the repository to update the application (use firstOrNull and check result)
                         // Determine the ApplicationStatus based on DecisionStatus
                         val newAppStatus = when(decisionStatus) {
                            DecisionStatus.AUTO_APPROVED -> ApplicationStatus.APPROVED
                            DecisionStatus.REJECTED -> ApplicationStatus.REJECTED
                            DecisionStatus.REFERRED_TO_UNDERWRITER -> ApplicationStatus.UNDER_REVIEW
                            DecisionStatus.PENDING -> application.applicationStatus // Keep existing status if pending
                         }

                         val updateResource = loanRepository.updateApplicationStatus(
                             applicationId,
                             newAppStatus // Pass the determined status
                         ).filterNot { it is Resource.Loading }
                          .firstOrNull() // Use firstOrNull

                        // Check the update result
                         when (updateResource) {
                            is Resource.Success -> emit(Resource.Success(updateResource.data))
                            is Resource.Error -> emit(Resource.Error(updateResource.message)) // Access message correctly
                            null -> emit(Resource.Error("Failed to update application (null result)"))
                            is Resource.Loading -> emit(Resource.Error("Unexpected loading state during application update")) // Handle loading
                         }
                    } else {
                         // Handle error getting application
                         val errorMsg = (applicationResource as? Resource.Error)?.message ?: "Failed to get application (or result was null)"
                         emit(Resource.Error(errorMsg))
                    }
                 } else {
                      emit(Resource.Error("No BRE output data found for application: $applicationId"))
                 }
            } else {
                 emit(Resource.Error("Application not found: $applicationId"))
            }
        } catch (e: Exception) {
            val errorMessage = "Error updating application with decision: ${e.message}"
            AppLogger.e(errorMessage, e)
            emit(Resource.Error(errorMessage))
        }
    }


    /**
    * Get appropriate error code based on error message
    */
    private fun getAPIErrorCode(errorMessage: String?): String {
        return when {
            errorMessage?.contains("connection", true) == true -> BREErrorCodes.BRE_API_CONNECTION_FAILED
            errorMessage?.contains("timeout", true) == true -> BREErrorCodes.BRE_API_TIMEOUT
            errorMessage?.contains("authentication", true) == true -> BREErrorCodes.BRE_API_AUTHENTICATION_FAILED
            errorMessage?.contains("invalid", true) == true -> BREErrorCodes.BRE_API_INVALID_RESPONSE
            else -> BREErrorCodes.BRE_API_CONNECTION_FAILED
        }
    }

    /**
     * Log application state for debugging
     */
    private fun logApplicationState(prepId: String, application: LoanApplication) {
        AppLogger.d("[BRE_PREP:$prepId] Application State: " +
                "id=${application.id}, " +
                "hasPersonalInfo=${application.personalInfo != null}, " +
                "hasEmploymentDetails=${application.employmentDetails != null}, " +
                "hasBureauReport=${application.bureauReport != null}, " +
                "monthlySalary=${application.employmentDetails?.monthlySalary}, " +
                "monthlyEmi=${application.employmentDetails?.monthlyEmi}")
    }

    /**
     * Log BRE input state for debugging
     */
    private fun logBREInputState(prepId: String, breInput: BREInput) {
        AppLogger.d("[BRE_PREP:$prepId] BREInput State: " +
                "applicationId=${breInput.applicationId}, " +
                "hasPersonalInfo=${breInput.personalInfo != null}, " +
                "hasEmploymentDetails=${breInput.employmentDetails != null}, " +
                "hasBureauData=${breInput.bureauData != null}, " +
                "hasDocumentExtractions=${breInput.documentExtractions != null}, " +
                "llmRecalculatedObligation=${breInput.llmRecalculatedObligation}")
    }

} // End of class BRERepositoryImpl