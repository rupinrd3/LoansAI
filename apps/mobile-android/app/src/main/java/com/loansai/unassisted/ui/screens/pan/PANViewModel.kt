package com.loansai.unassisted.ui.screens.pan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.loansai.unassisted.BuildConfig
import com.loansai.unassisted.data.local.source.PreferencesDataSource
import com.loansai.unassisted.data.repository.PANRepositoryImpl
import com.loansai.unassisted.domain.model.BureauReport
import com.loansai.unassisted.domain.model.FileMetadata
import com.loansai.unassisted.domain.model.OCRScanState
import com.loansai.unassisted.domain.model.PANDetails
import com.loansai.unassisted.domain.repository.LoanRepository
import com.loansai.unassisted.domain.repository.PANRepository
import com.loansai.unassisted.domain.repository.UserRepository
import com.loansai.unassisted.service.ocr.OCRService
import com.loansai.unassisted.util.FileUtils
import com.loansai.unassisted.util.extensions.Resource
import com.loansai.unassisted.util.extensions.isValidPAN
import com.loansai.unassisted.util.logger.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import java.time.format.DateTimeParseException 

/**
 * Data class for extracted personal info (Storing DOB as String)
 */
data class PersonalInfoExtractedData(
    val name: String = "",
    val dateOfBirthString: String? = null // Store DOB as YYYY-MM-DD String
)

/**
 * UI state for the PAN Entry screen
 */
data class PANEntryUIState(
    val isLoading: Boolean = false,
    val panNumber: String = "",
    val isPANValid: Boolean = false,
    val panError: String? = null,
    val isPANVerified: Boolean = false,
    val verifiedPANDetails: PANDetails? = null,
    val bureauReport: BureauReport? = null,
    val bureauReportFetched: Boolean = false,
    val error: String? = null,
    val fatalError: String? = null,
    val navigateToPersonalInfo: Boolean = false,
    
    // New fields for OCR extraction
    val extractedName: String = "",
    val extractedDOB: String = "",
    val extractionConfirmed: Boolean = false,
    val showExtractedFields: Boolean = false
)

/**
 * ViewModel for the PAN Entry screen
 */
@HiltViewModel
class PANViewModel @Inject constructor(
    private val panRepository: PANRepository,
    private val userRepository: UserRepository,
    private val loanRepository: LoanRepository,
    private val ocrService: OCRService,
    private val preferencesDataSource: PreferencesDataSource
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PANEntryUIState())
    val uiState: StateFlow<PANEntryUIState> = _uiState.asStateFlow()
    
    private val _scanState = MutableStateFlow<OCRScanState>(OCRScanState.Initial)
    val scanState: StateFlow<OCRScanState> = _scanState.asStateFlow()
    
    private var imageUri: Uri? = null
    private var currentApplicationId: String? = null
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var accelerometer: Sensor? = null
    private var ambientLight: Float = -1f
    private var deviceAngle: FloatArray? = null
    
    init {
        // Get current application ID
        viewModelScope.launch {
            userRepository.getCurrentUser().collectLatest { userResource ->
                if (userResource is Resource.Success) {
                    currentApplicationId = userResource.data.currentApplicationId
                }
            }
        }
    }
    
    /**
     * Update PAN number and validate it
     */
    fun updatePANNumber(panNumber: String) {
        val isValid = panNumber.isValidPAN()
        
        _uiState.update { 
            it.copy(
                panNumber = panNumber,
                isPANValid = isValid,
                panError = if (panNumber.isNotEmpty() && !isValid && panNumber.length == 10) {
                    "Please enter a valid PAN number (e.g., ABCDE1234F)"
                } else {
                    null
                }
            ) 
        }
    }
    
    /**
     * Update extracted name
     */
    fun updateExtractedName(name: String) {
        _uiState.update { 
            it.copy(extractedName = name) 
        }
    }

    /**
     * Update extracted DOB
     */
    fun updateExtractedDOB(dob: String) {
        _uiState.update { 
            it.copy(extractedDOB = dob) 
        }
    }

    /**
     * Set extraction confirmation state
     */
    fun setExtractionConfirmed(confirmed: Boolean) {
        _uiState.update { 
            it.copy(extractionConfirmed = confirmed) 
        }
    }

    /**
     * Show or hide extracted fields
     */
    fun showExtractedFields(show: Boolean) {
        _uiState.update { 
            it.copy(showExtractedFields = show) 
        }
    }
    
    /**
     * Initialize sensor listeners for camera metadata
     */
    private fun initSensors(context: Context) {
        try {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            
            val sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_LIGHT -> {
                            ambientLight = event.values[0]
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            deviceAngle = event.values.clone()
                        }
                    }
                }
                
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                    // Not needed for this implementation
                }
            }
            
            lightSensor?.let {
                sensorManager?.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
            
            accelerometer?.let {
                sensorManager?.registerListener(
                    sensorListener,
                    it,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        } catch (e: Exception) {
            AppLogger.e("Error initializing sensors: ${e.message}", e)
        }
    }
    
    /**
     * Clean up sensor listeners
     */
    private fun cleanupSensors() {
        try {
            sensorManager?.unregisterListener(null as SensorEventListener?)
            sensorManager = null
        } catch (e: Exception) {
            AppLogger.e("Error cleaning up sensors: ${e.message}", e)
        }
    }
    
    /**
     * Prepare for image capture
     */
    fun prepareImageCapture(
        context: Context,
        cameraLauncher: ActivityResultLauncher<Uri>
    ) {
        try {
            AppLogger.d("Preparing for image capture")
            
            // Initialize sensors for metadata collection
            initSensors(context)
            
            // Create temporary file for the image
            val tempFile = File.createTempFile(
                "pan_card_",
                ".jpg",
                context.cacheDir
            )
            
            AppLogger.d("Temp file created: ${tempFile.absolutePath}")
            
            // Create content URI
            val uri = FileProvider.getUriForFile(
                context,
                "com.loansai.unassisted.fileprovider",
                tempFile
            )
            
            AppLogger.d("Content URI created: $uri")
            
            // Save the URI for later processing
            imageUri = uri
            
            // Launch camera
            AppLogger.d("Launching camera with URI: $uri")
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            AppLogger.e("Error preparing camera: ${e.message}", e)
            _scanState.value = OCRScanState.Error("Error preparing camera: ${e.message}")
        }
    }
    
    /**
     * Set image URI (for gallery selection)
     */
    fun setImageUri(uri: Uri) {
        AppLogger.d("Setting image URI from gallery: $uri")
        imageUri = uri
    }
    
    /**
     * Process captured image with OCR
     */
    fun processCapturedImage() {
        val uri = imageUri ?: return
        
        // Clean up sensors - we've got the image now
        cleanupSensors()
        
        // Log that processing has started
        AppLogger.d("Starting to process image from URI: $uri")
        
        viewModelScope.launch {
            _scanState.value = OCRScanState.Scanning
            
            try {
                ocrService.recognizeText(uri).collectLatest { scanState ->
                    AppLogger.d("OCR scan state received: $scanState")
                    
                    when (scanState) {
                        is OCRScanState.Success -> {
                            AppLogger.d("OCR Success with text: ${scanState.text}")
                            // Extract PAN number from recognized text
                            val panNumber = extractPANNumber(scanState.text)
                            
                            if (panNumber != null) {
                                AppLogger.d("Valid PAN number extracted: $panNumber")
                                updatePANNumber(panNumber)
                                _scanState.value = OCRScanState.Success(panNumber, uri)
                                
                                // Record document event (success)
                                recordDocumentEvent(true)
                            } else {
                                AppLogger.d("No valid PAN number found in text")
                                _scanState.value = OCRScanState.Error(
                                    "Could not find a valid PAN number in the image. Please try again or enter manually."
                                )
                                
                                // Record document event (failure)
                                recordDocumentEvent(false, "No valid PAN number found")
                            }
                        }
                        is OCRScanState.Error -> {
                            AppLogger.e("OCR Error: ${scanState.message}")
                            _scanState.value = scanState
                            
                            // Record document event (failure)
                            recordDocumentEvent(false, scanState.message)
                        }
                        else -> {
                            AppLogger.d("OCR in progress: $scanState")
                            _scanState.value = scanState
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("Exception during image processing", e)
                _scanState.value = OCRScanState.Error("Error processing image: ${e.message}")
                
                // Record document event (failure)
                recordDocumentEvent(false, e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Get camera sensor metadata
     */
    private fun getCameraSensorMetadata(): Map<String, Any>? {
        val metadata = mutableMapOf<String, Any>()
        
        if (ambientLight >= 0) {
            metadata["ambientLight"] = ambientLight
        }
        
        deviceAngle?.let { angles ->
            if (angles.size >= 3) {
                // Convert raw accelerometer data to angles
                val pitch = Math.toDegrees(
                    Math.atan2(
                        angles[0].toDouble(),
                        Math.sqrt((angles[1] * angles[1] + angles[2] * angles[2]).toDouble())
                    )
                ).toFloat()
                
                val roll = Math.toDegrees(
                    Math.atan2(
                        angles[1].toDouble(),
                        Math.sqrt((angles[0] * angles[0] + angles[2] * angles[2]).toDouble())
                    )
                ).toFloat()
                
                metadata["deviceAngle"] = mapOf(
                    "pitch" to pitch,
                    "roll" to roll
                )
            }
        }
        
        return if (metadata.isNotEmpty()) metadata else null
    }
    
    /**
     * Record document event
     */
    private fun recordDocumentEvent(success: Boolean, failureReason: String? = null) {
        viewModelScope.launch {
            currentApplicationId?.let { appId ->
                val imageUri = imageUri
                
                // Get image metadata if available
                val fileMetadata = if (imageUri != null) {
                    try {
                        // Use the context from the app
                        val context = preferencesDataSource.getContext()
                        val fileName = FileUtils.getFileName(context, imageUri)
                        val fileSize = FileUtils.getFileSize(context, imageUri)
                        
                        FileMetadata(
                            fileName = fileName,
                            fileSize = fileSize
                        )
                    } catch (e: Exception) {
                        AppLogger.e("Error getting file metadata: ${e.message}", e)
                        null
                    }
                } else null
                
                // Get camera sensor metadata
                val cameraSensorMetadata = getCameraSensorMetadata()
                
                // Create document event map for Firestore
                val documentEvent = mapOf(
                    "eventId" to UUID.randomUUID().toString(),
                    "documentType" to "PAN_CARD",
                    "documentSourceType" to if (imageUri != null) "CAMERA" else "MANUAL_ENTRY",
                    "action" to "SCAN_ATTEMPT",
                    "timestamp" to LocalDateTime.now().toString(),
                    "status" to if (success) "SUCCESS" else "FAILURE",
                    "failureReason" to failureReason,
                    "extractionStatus" to if (success) "EXTRACTED" else "FAILED",
                    "fileMetadata" to if (fileMetadata != null) mapOf(
                        "fileName" to fileMetadata.fileName,
                        "fileSize" to fileMetadata.fileSize
                    ) else null,
                    "cameraMetadata" to cameraSensorMetadata
                )
                
                try {
                    // Update application metadata
                    loanRepository.updateApplicationMetadata(
                        applicationId = appId,
                        metadataField = "documentEvents",
                        data = documentEvent
                    ).collect {}
                } catch (e: Exception) {
                    AppLogger.e("Error recording document event: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Extract PAN number, name and DOB from OCR text
     */
    private fun extractPANNumber(text: String): String? {
        // Look for pattern: 5 letters followed by 4 digits followed by 1 letter
        val panRegex = "[A-Z]{5}[0-9]{4}[A-Z]{1}".toRegex()
        val match = panRegex.find(text.replace(" ", ""))
        
        if (match != null) {
            // Only try to extract name and DOB if a valid PAN is found
            
            // Extract name: Look for lines with all capitals after "INCOME TAX DEPARTMENT"
            val lines = text.split("\n")
            for (i in 0 until lines.size) {
                val line = lines[i].trim()
                if (line == "INCOME TAX DEPARTMENT" && i + 1 < lines.size) {
                    val possibleName = lines[i + 1].trim()
                    if (possibleName.isNotEmpty() && possibleName.uppercase() == possibleName 
                        && possibleName != "Permanent Account Number") {
                        updateExtractedName(possibleName)
                        break
                    }
                }
            }
            
            // Try to extract DOB directly with a date pattern
            val dobRegex = "\\d{2}/\\d{2}/\\d{4}".toRegex()
            val dobMatch = dobRegex.find(text)
            if (dobMatch != null) {
                updateExtractedDOB(dobMatch.value)
            }
            
            // Show the fields for confirmation
            showExtractedFields(true)
        }
        
        return match?.value
    }
    


    /**
    * Verify PAN number with backend
    */
    fun verifyPAN() {
        AppLogger.d("[PANViewModel] verifyPAN() called.") 
        val panNumber = uiState.value.panNumber
        AppLogger.d("[PANViewModel] PAN to verify: $panNumber") 

        // Log the validation check result
        if (!panNumber.isValidPAN()) {
            AppLogger.w("[PANViewModel] Invalid PAN format. Aborting verifyPAN.") 
            _uiState.update {
                it.copy(panError = "Please enter a valid PAN number (e.g., ABCDE1234F)")
            }
            return
        }

        AppLogger.d("[PANViewModel] PAN format valid. Launching verification coroutine.") 
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            AppLogger.d("[PANViewModel] Calling panRepository.verifyPAN($panNumber)...") 

            try {
                panRepository.verifyPAN(panNumber).collectLatest { result ->
                    AppLogger.d("[PANViewModel] Received result from panRepository.verifyPAN: ${result::class.simpleName}") 
                    when (result) {
                        is Resource.Success -> {
                            val panDetails = result.data
                            AppLogger.i("[PANViewModel] PAN Verification Success. Details: $panDetails") 
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    isPANVerified = true,
                                    verifiedPANDetails = panDetails
                                )
                            }

                            // Explicitly check if bureau data exists in Appwrite
                            try {
                                val existsInAppwrite = panRepository.checkBureauDataExists(panNumber)
                                AppLogger.d("[PANViewModel] Bureau data exists in Appwrite: $existsInAppwrite")
                            } catch (e: Exception) {
                                AppLogger.e("[PANViewModel] Error checking bureau data in Appwrite: ${e.message}", e)
                                // Continue with normal fetch regardless
                            }

                            // Trigger bureau fetch only on successful verification
                            AppLogger.d("[PANViewModel] PAN Verified successfully, now calling fetchBureauReport($panNumber)...") 
                            fetchBureauReport(panNumber)
                        }
                        is Resource.Error -> {
                            AppLogger.e("[PANViewModel] PAN Verification Failed. Error: ${result.message}") 
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    panError = "Failed to verify PAN: ${result.message}",
                                    error = result.message
                                )
                            }
                        }
                        is Resource.Loading -> {
                            AppLogger.d("[PANViewModel] panRepository.verifyPAN is Loading...") 
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("[PANViewModel] Uncaught exception in verifyPAN: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        panError = "Error: ${e.message}",
                        error = e.message
                    )
                }
            }
        }
    }
    


    /**
     * Auto-navigate to personal info screen after a delay
     */
    private fun autoNavigateAfterDelay() {
        AppLogger.d("Starting auto-navigation delay")
        viewModelScope.launch {
            delay(1500) // 1.5 seconds delay
            AppLogger.d("Auto-navigating to Personal Info screen")
            _uiState.update { it.copy(navigateToPersonalInfo = true) }
        }
    }
    
    /**
    * Fetch bureau report for the verified PAN
    */
    private fun fetchBureauReport(panNumber: String) {
        AppLogger.d("[PANViewModel] BUREAU-FETCH: fetchBureauReport($panNumber) called with repository ${panRepository.javaClass.simpleName}")
        viewModelScope.launch {
            AppLogger.d("[PANViewModel] BUREAU-FETCH: Launching coroutine for panRepository.fetchBureauReport($panNumber)")
            
            try {
                panRepository.fetchBureauReport(panNumber).collectLatest { result ->
                    AppLogger.d("[PANViewModel] BUREAU-FETCH: Received result from fetchBureauReport: ${result::class.simpleName}")
                    
                    when (result) {
                        is Resource.Success -> {
                            val bureauReport = result.data
                            AppLogger.d("[PANViewModel] BUREAU-FETCH: Success! score=${bureauReport.creditScore}, source=${bureauReport.bureauType ?: "unknown"}")
                            
                            _uiState.update { 
                                it.copy(
                                    bureauReport = bureauReport,
                                    bureauReportFetched = true
                                ) 
                            }
                            
                            // Save bureau report to application
                            currentApplicationId?.let { appId ->
                                AppLogger.d("[PANViewModel] BUREAU-FETCH: Saving bureau report to application: $appId")
                                try {
                                    panRepository.saveBureauReport(appId, bureauReport)
                                        .collectLatest { saveResult ->
                                            if (saveResult is Resource.Success) {
                                                AppLogger.d("[PANViewModel] BUREAU-FETCH: Bureau report saved successfully")
                                            } else if (saveResult is Resource.Error) {
                                                AppLogger.e("[PANViewModel] BUREAU-FETCH: Error saving bureau report: ${saveResult.message}")
                                            }
                                        }
                                    
                                    // Auto-navigate after bureau report is saved
                                    autoNavigateAfterDelay()
                                } catch (e: Exception) {
                                    AppLogger.e("[PANViewModel] BUREAU-FETCH: Exception saving bureau report: ${e.message}", e)
                                    // Auto-navigate even if saving fails
                                    autoNavigateAfterDelay()
                                }
                            }
                        }
                        is Resource.Error -> {
                            AppLogger.e("[PANViewModel] BUREAU-FETCH: Error fetching bureau report: ${result.message}")
                            _uiState.update { 
                                it.copy(
                                    error = "Failed to fetch bureau report: ${result.message}",
                                    bureauReportFetched = false
                                ) 
                            }
                            
                            // Even if bureau report fetch fails, still auto-navigate
                            // after a short delay if the PAN was verified successfully
                            if (uiState.value.isPANVerified) {
                                autoNavigateAfterDelay()
                            }
                        }
                        is Resource.Loading -> {
                            AppLogger.d("[PANViewModel] BUREAU-FETCH: Bureau report fetch in progress")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("[PANViewModel] BUREAU-FETCH: Uncaught exception in fetchBureauReport flow: ${e.message}", e)
            }
        }
    }




    /**
    * Continue to next screen with extracted data
    */
    fun onContinue() {
        AppLogger.d("[PANViewModel] onContinue() called.")
        val currentAppId = currentApplicationId
        AppLogger.d("[PANViewModel] Current application ID: $currentAppId")
        currentApplicationId?.let { appId ->
        
            viewModelScope.launch {
                var parsedDate: LocalDate? = null
                if (uiState.value.extractionConfirmed && uiState.value.extractedDOB.isNotEmpty()) {
                    try {
                        // Existing date parsing code...
                        val parts = uiState.value.extractedDOB.split("/", "-", ".")
                        if (parts.size == 3) {
                            val day = parts[0].toInt()
                            val month = parts[1].toInt()
                            // Handle 2-digit and 4-digit years more robustly
                            val year = when {
                                parts[2].length <= 2 -> {
                                    val twoDigitYear = parts[2].toInt()
                                    // Assume years > 50 are 19xx, others are 20xx (adjust threshold if needed)
                                    if (twoDigitYear > 50) 1900 + twoDigitYear else 2000 + twoDigitYear
                                }
                                else -> parts[2].toInt()
                            }
                                
                            AppLogger.d("Parsed date components: day=$day, month=$month, year=$year")
                            
                            // Validate the date components before creating LocalDate
                            if (day in 1..31 && month in 1..12 && year in 1900..(LocalDate.now().year + 1)) {
                                try {
                                    parsedDate = LocalDate.of(year, month, day)
                                    AppLogger.d("Successfully parsed LocalDate: $parsedDate")
                                } catch (e: Exception) {
                                    AppLogger.e("Invalid date components after parsing: $day/$month/$year", e)
                                }
                            } else {
                                AppLogger.e("Date components out of valid range: $day/$month/$year")
                            }
                        } else {
                            AppLogger.w("Could not split DOB string '${uiState.value.extractedDOB}' into 3 parts.")
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Error parsing date string '${uiState.value.extractedDOB}': ${e.message}", e)
                    }
                }

                // Create extracted data for Personal Info screen
                val extractedData = PersonalInfoExtractedData(
                    name = if (uiState.value.extractionConfirmed) uiState.value.extractedName else "",
                    dateOfBirthString = parsedDate?.toString()
                )
                
                AppLogger.d("Saving extracted data: name=${extractedData.name}, dobString=${extractedData.dateOfBirthString}")
                saveExtractedData(appId, extractedData)

                // NEW CODE: Check if we have a valid PAN from OCR that we could use to fetch bureau data
                // This is optional and happens in the background - we don't wait for it to complete
                val extractedPAN = uiState.value.panNumber
                if (extractedPAN.isNotEmpty() && extractedPAN.isValidPAN() && !uiState.value.isPANVerified) {
                    AppLogger.d("[PANViewModel] OCR extracted a valid PAN. Attempting background bureau check: $extractedPAN")
                    
                    // Launch a new coroutine for the background fetch - don't block navigation
                    viewModelScope.launch {
                        try {
                            AppLogger.d("[PANViewModel] Checking if bureau data exists for OCR-extracted PAN")
                            val exists = panRepository.checkBureauDataExists(extractedPAN)
                            if (exists) {
                                AppLogger.d("[PANViewModel] Bureau data found in Appwrite for OCR-extracted PAN")
                                // Fetch the bureau report
                                AppLogger.d("[PANViewModel] Fetching bureau data for OCR-extracted PAN")
                                panRepository.fetchBureauReport(extractedPAN).collect { result ->
                                    if (result is Resource.Success) {
                                        AppLogger.d("[PANViewModel] Successfully fetched bureau data for OCR-extracted PAN")
                                        // Save to application
                                        panRepository.saveBureauReport(appId, result.data).collect {}
                                    }
                                }
                            } else {
                                AppLogger.d("[PANViewModel] No bureau data found in Appwrite for OCR-extracted PAN")
                            }
                        } catch (e: Exception) {
                            AppLogger.e("[PANViewModel] Background bureau check failed: ${e.message}", e)
                            // Non-critical, navigation continues regardless
                        }
                    }
                }

                // Proceed with navigation regardless of bureau fetch status
                AppLogger.d("[PANViewModel] Extracted data logic complete. Calling autoNavigateAfterDelay().")
                autoNavigateAfterDelay()
            }
        } ?: AppLogger.e("Cannot save extracted data, currentApplicationId is null")
    }




    /**
     * Save extracted data for use in PersonalInfo screen
     */
    private fun saveExtractedData(applicationId: String, data: PersonalInfoExtractedData) {
        // Save to a shared preference
        viewModelScope.launch {
            val json = Gson().toJson(data)
            preferencesDataSource.saveString("extracted_data_$applicationId", json)
        }
    }
    
    /**
     * Reset navigation flags
     */
    fun resetNavigation() {
        _uiState.update { it.copy(navigateToPersonalInfo = false) }
    }
    
    /**
     * Skip verification and go to the next screen
     * This is only available in debug builds for development purposes
     */
    fun onSkip() {
        if (BuildConfig.DEBUG) {
            AppLogger.d("Skipping PAN verification (debug mode)")
            _uiState.update { it.copy(navigateToPersonalInfo = true) }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Retry after a fatal error
     */
    fun retry() {
        _uiState.update { 
            it.copy(
                isLoading = false,
                fatalError = null,
                error = null
            ) 
        }
    }
    
    /**
     * Clean up resources when the ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        cleanupSensors()
    }
}